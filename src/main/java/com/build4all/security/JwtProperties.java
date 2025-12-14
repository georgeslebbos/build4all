package com.build4all.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds JWT configuration values from application.properties / application.yml.
 *
 * With @ConfigurationProperties(prefix = "security.jwt"), Spring will map:
 * - security.jwt.secret-key      -> secretKey
 * - security.jwt.expiration-time -> expirationTime
 *
 * Example (application.yml):
 * security:
 *   jwt:
 *     secret-key: "a-very-long-secret..."
 *     expiration-time: 86400000   # 24h in milliseconds
 *
 * This class is used by JwtUtil to:
 * - build the HMAC signing key (secretKey)
 * - decide token lifetime/TTL (expirationTime)
 */
@Component
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {

    /**
     * Secret key used to sign JWT tokens (HMAC).
     * IMPORTANT:
     * - Must be long enough for the chosen algorithm (HS256 requires a sufficiently long key).
     * - Keep it private (use env vars / secrets in production).
     */
    private String secretKey;

    /**
     * Access token expiration time in milliseconds.
     * Example: 86400000 = 24 hours.
     */
    private long expirationTime;

    // Getters and Setters

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public long getExpirationTime() {
        return expirationTime;
    }

    public void setExpirationTime(long expirationTime) {
        this.expirationTime = expirationTime;
    }
}
