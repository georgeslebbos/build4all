package com.build4all.authentication.service;

import com.build4all.authentication.domain.OwnerEmailOtp;
import com.build4all.authentication.repository.OwnerEmailOtpRepository;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class OwnerOtpService {

    private static final int OTP_LENGTH = 6;
    private static final int MAX_ATTEMPTS = 5;
    private static final int TTL_MINUTES = 10;

    private final SecureRandom rng = new SecureRandom();
    private final OwnerEmailOtpRepository repo;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;

    public OwnerOtpService(OwnerEmailOtpRepository repo,
                           JavaMailSender mailSender,
                           PasswordEncoder passwordEncoder) {
        this.repo = repo;
        this.mailSender = mailSender;
        this.passwordEncoder = passwordEncoder;
    }

    private String generateNumericOtp() {
        // 000000..999999
        return String.format("%06d", rng.nextInt(1_000_000));
    }

    @Transactional
    public void generateAndSendOtp(String email) {
        // purge only expired rows (<= now)
    	 repo.deleteAllByExpiresAtBefore(LocalDateTime.now().minusDays(1));

    	    String otp = generateNumericOtp();
    	    String hash = passwordEncoder.encode(otp);

    	    repo.deleteByEmail(email);
    	    OwnerEmailOtp rec = new OwnerEmailOtp(email, hash, LocalDateTime.now().plusMinutes(TTL_MINUTES));
    	    repo.save(rec);

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(email);
        msg.setSubject("Your Build4All verification code");
        msg.setText("""
                Your verification code is: %s

                It expires in %d minutes.
                If you didn't request this, you can ignore this email.
                """.formatted(otp, TTL_MINUTES));
        mailSender.send(msg);
    }

    @Transactional
    public boolean verify(String email, String submittedCode) {
        OwnerEmailOtp rec = repo.findTopByEmailOrderByCreatedAtDesc(email).orElse(null);
        if (rec == null) return false;

        if (rec.getExpiresAt().isBefore(LocalDateTime.now())) {
            repo.deleteByEmail(email);
            return false;
        }

        if (rec.getAttempts() >= MAX_ATTEMPTS) {
            repo.deleteByEmail(email);
            return false;
        }

        boolean ok = passwordEncoder.matches(submittedCode, rec.getCodeHash());
        rec.setAttempts(rec.getAttempts() + 1);
        repo.save(rec);

        if (ok) {
            repo.deleteByEmail(email); // one-time use
            return true;
        }
        return false;
    }
}
