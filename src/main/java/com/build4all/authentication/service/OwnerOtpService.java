package com.build4all.authentication.service;

import com.build4all.authentication.domain.OwnerEmailOtp;
import com.build4all.authentication.repository.OwnerEmailOtpRepository;
import com.build4all.notifications.service.EmailService; // <— use your wrapper
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
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService; // <— injected wrapper

    public OwnerOtpService(OwnerEmailOtpRepository repo,
                           PasswordEncoder passwordEncoder,
                           EmailService emailService) {
        this.repo = repo;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    private String generateNumericOtp() {
        // 000000..999999
        // If you want exactly 6 digits with leading zeros:
        int n = rng.nextInt(1_000_000); // 0..999999
        return String.format("%06d", n);
    }

    @Transactional
    public void generateAndSendOtp(String email) {
        // Drop anything very old (housekeeping)
        repo.deleteAllByExpiresAtBefore(LocalDateTime.now().minusDays(1));

        // Generate & hash OTP
        String otp = generateNumericOtp();
        String hash = passwordEncoder.encode(otp);

        // Ensure one active OTP per email
        repo.deleteByEmail(email);

        OwnerEmailOtp rec = new OwnerEmailOtp(email, hash, LocalDateTime.now().plusMinutes(TTL_MINUTES));
        repo.save(rec);

        // Build email content (HTML + plaintext fallback)
        String subject = "Your Build4All verification code";
        String html = """
            <html>
              <body style="font-family: Arial, Helvetica, sans-serif; padding:24px; background:#f7f8fa;">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:520px;margin:0 auto;background:#ffffff;border-radius:10px;box-shadow:0 6px 24px rgba(0,0,0,0.06);">
                  <tr>
                    <td style="padding:28px 28px 8px 28px;">
                      <h2 style="margin:0 0 12px 0; color:#111827;">Verify your email</h2>
                      <p style="margin:0; color:#4b5563;">Use this one-time code to finish setting up your Owner account.</p>
                    </td>
                  </tr>
                  <tr>
                    <td style="padding:8px 28px 16px 28px; text-align:center;">
                      <div style="display:inline-block; font-size:32px; letter-spacing:6px; font-weight:700; color:#111827; padding:14px 22px; border:2px solid #e5e7eb; border-radius:12px;">
                        %s
                      </div>
                    </td>
                  </tr>
                  <tr>
                    <td style="padding:0 28px 24px 28px;">
                      <p style="margin:0; color:#6b7280;">This code expires in <b>%d minutes</b>. If you didn’t request it, you can safely ignore this email.</p>
                    </td>
                  </tr>
                </table>
                <p style="text-align:center; color:#9ca3af; font-size:12px; margin-top:16px;">© Build4All</p>
              </body>
            </html>
            """.formatted(otp, TTL_MINUTES);

        String text = """
            Your Build4All verification code is: %s
            It expires in %d minutes.
            If you didn't request this, you can ignore this email.
            """.formatted(otp, TTL_MINUTES);

        // Send HTML email (your EmailService sends with HTML enabled)
        try {
            emailService.sendHtmlEmail(email, subject, html);
        } catch (Exception ex) {
            // Optional: fallback to plaintext if HTML fails for any reason
            emailService.sendEmail(email, subject, text);
        }
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
