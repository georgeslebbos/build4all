package com.build4all.authentication.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private static final Duration RESET_AFTER = Duration.ofHours(24);

    private final ConcurrentHashMap<String, AttemptState> attempts = new ConcurrentHashMap<>();

    public LockStatus checkBlocked(Long ownerProjectLinkId, String loginType, String identifier) {
        String key = buildKey(ownerProjectLinkId, loginType, identifier);
        AttemptState state = attempts.get(key);

        if (state == null) {
            return LockStatus.free();
        }

        synchronized (state) {
            LocalDateTime now = LocalDateTime.now();

            if (state.lastFailedAt != null &&
                    Duration.between(state.lastFailedAt, now).compareTo(RESET_AFTER) > 0) {
                attempts.remove(key);
                return LockStatus.free();
            }

            if (state.lockedUntil != null && state.lockedUntil.isAfter(now)) {
                long seconds = Math.max(1, Duration.between(now, state.lockedUntil).getSeconds());
                return new LockStatus(true, state.failedAttempts, seconds, state.lockedUntil);
            }

            return new LockStatus(false, state.failedAttempts, 0, state.lockedUntil);
        }
    }

    public LockStatus recordFailure(Long ownerProjectLinkId, String loginType, String identifier) {
        String key = buildKey(ownerProjectLinkId, loginType, identifier);
        AttemptState state = attempts.computeIfAbsent(key, k -> new AttemptState());

        synchronized (state) {
            LocalDateTime now = LocalDateTime.now();

            if (state.lastFailedAt != null &&
                    Duration.between(state.lastFailedAt, now).compareTo(RESET_AFTER) > 0) {
                state.failedAttempts = 0;
                state.lockedUntil = null;
            }

            state.failedAttempts++;
            state.lastFailedAt = now;

            Duration lockDuration = getLockDuration(state.failedAttempts);

            if (lockDuration.isZero() || lockDuration.isNegative()) {
                state.lockedUntil = null;
                return new LockStatus(false, state.failedAttempts, 0, null);
            }

            state.lockedUntil = now.plus(lockDuration);

            return new LockStatus(
                    true,
                    state.failedAttempts,
                    Math.max(1, lockDuration.getSeconds()),
                    state.lockedUntil
            );
        }
    }

    public void recordSuccess(Long ownerProjectLinkId, String loginType, String identifier) {
        String key = buildKey(ownerProjectLinkId, loginType, identifier);
        attempts.remove(key);
    }

    private Duration getLockDuration(int failedAttempts) {
        if (failedAttempts < 3) return Duration.ZERO;   // ✅ first 1-2 attempts: no lock
        if (failedAttempts <= 4) return Duration.ofSeconds(30);
        if (failedAttempts <= 6) return Duration.ofMinutes(1);
        if (failedAttempts <= 8) return Duration.ofMinutes(5);
        return Duration.ofMinutes(15);
    }

    private String buildKey(Long ownerProjectLinkId, String loginType, String identifier) {
        String normalized = normalize(identifier);
        String tenantPart = ownerProjectLinkId == null ? "GLOBAL" : ownerProjectLinkId.toString();
        return tenantPart + "|" + loginType + "|" + normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static class AttemptState {
        int failedAttempts = 0;
        LocalDateTime lockedUntil;
        LocalDateTime lastFailedAt;
    }

    public record LockStatus(
            boolean blocked,
            int failedAttempts,
            long retryAfterSeconds,
            LocalDateTime lockedUntil
    ) {
        public static LockStatus free() {
            return new LockStatus(false, 0, 0, null);
        }
    }
    
    
}