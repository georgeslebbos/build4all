


package com.build4all.security.refresh.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.build4all.security.refresh.AuthRefreshToken;
import com.build4all.security.refresh.repository.AuthRefreshTokenRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class AuthRefreshTokenService {

    private final AuthRefreshTokenRepository repo;
    private final SecureRandom random = new SecureRandom();

    private static final long REFRESH_DAYS = 30;

    public AuthRefreshTokenService(AuthRefreshTokenRepository repo) {
        this.repo = repo;
    }

    public record Rotated(String newRefreshToken, String subjectType, Long subjectId, Long ownerProjectId) {}

    @Transactional
    public String issue(String subjectType, Long subjectId, Long ownerProjectId) {
        String raw = generateRawToken();
        String hash = sha256Base64(raw);

        AuthRefreshToken row = new AuthRefreshToken();
        row.setSubjectType(subjectType);
        row.setSubjectId(subjectId);
        row.setOwnerProjectId(ownerProjectId);
        row.setTokenHash(hash);
        row.setExpiresAt(LocalDateTime.now().plusDays(REFRESH_DAYS));

        repo.save(row);
        return raw;
    }

    @Transactional
    public Rotated rotate(String oldRaw) {
        LocalDateTime now = LocalDateTime.now();
        String oldHash = sha256Base64(oldRaw);

        AuthRefreshToken old = repo.findByTokenHash(oldHash)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        if (!old.isActive(now)) {
            throw new RuntimeException("Refresh token expired or revoked");
        }

        String newRaw = generateRawToken();
        String newHash = sha256Base64(newRaw);

        old.setRevokedAt(now);
        old.setReplacedByHash(newHash);
        repo.save(old);

        AuthRefreshToken next = new AuthRefreshToken();
        next.setSubjectType(old.getSubjectType());
        next.setSubjectId(old.getSubjectId());
        next.setOwnerProjectId(old.getOwnerProjectId());
        next.setTokenHash(newHash);
        next.setExpiresAt(now.plusDays(REFRESH_DAYS));
        repo.save(next);

        return new Rotated(newRaw, old.getSubjectType(), old.getSubjectId(), old.getOwnerProjectId());
    }

    @Transactional
    public void revoke(String raw) {
        String hash = sha256Base64(raw);
        repo.findByTokenHash(hash).ifPresent(t -> {
            t.setRevokedAt(LocalDateTime.now());
            repo.save(t);
        });
    }

    private String generateRawToken() {
        byte[] bytes = new byte[64];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Base64(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new RuntimeException("Hash error");
        }
    }
}