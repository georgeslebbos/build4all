// src/main/java/com/build4all/security/JwtUtil.java
package com.build4all.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;

import com.build4all.admin.domain.AdminUser;
import com.build4all.business.domain.Businesses;
import com.build4all.user.domain.Users;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for generating and parsing JWTs for:
 * - Users  (role = USER)
 * - Businesses (role = BUSINESS)
 * - Admins (role = SUPER_ADMIN / OWNER / MANAGER)
 *
 * Also supports a short-lived "Owner Registration Token" used between OTP verification
 * and final owner account creation.
 */
@Component
public class JwtUtil {

    /** HMAC signing key. */
    private final Key key;

    /** Standard access token TTL in milliseconds. */
    private final long expirationTime;

    public JwtUtil(JwtProperties jwtProperties) {
        this.key = Keys.hmacShaKeyFor(jwtProperties.getSecretKey().getBytes());
        this.expirationTime = jwtProperties.getExpirationTime();
    }

    /* ======================== LOGIN TOKENS ======================== */

    public String generateToken(Users user) {
        String subject = user.getEmail() != null ? user.getEmail() : user.getPhoneNumber();

        return Jwts.builder()
                .setSubject(subject)
                .claim("id", user.getId())
                .claim("username", user.getUsername())
                .claim("firstName", user.getFirstName())
                .claim("lastName", user.getLastName())
                .claim("profileImageUrl", user.getProfilePictureUrl())
                .claim("role", "USER")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateToken(Businesses business) {
        String subject = (business.getEmail() != null && !business.getEmail().isEmpty())
                ? business.getEmail()
                : business.getPhoneNumber();

        if (subject == null || subject.isEmpty()) {
            throw new IllegalArgumentException("Business must have either email or phone number to generate token");
        }

        return Jwts.builder()
                .setSubject(subject)
                .claim("id", business.getId())
                .claim("businessName", business.getBusinessName())
                .claim("logoUrl", business.getBusinessLogoUrl())
                .claim("bannerUrl", business.getBusinessBannerUrl())
                .claim("role", "BUSINESS")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateToken(AdminUser adminUser) {
        return Jwts.builder()
                .setSubject(adminUser.getEmail())
                .claim("id", adminUser.getAdminId())
                .claim("username", adminUser.getUsername())
                .claim("role", adminUser.getRole().getName())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /* ======================== EXTRACTION HELPERS ======================== */

    public String extractRole(String token) {
        try {
            String jwt = normalize(token);
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(jwt)
                    .getBody()
                    .get("role", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    public Long extractId(String token) {
        String jwt = normalize(token);
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(jwt)
                .getBody()
                .get("id", Long.class);
    }

    public Long extractAdminId(String token) {
        return extractId(token);
    }

    public String extractUsername(String token) {
        String jwt = normalize(token);
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(jwt)
                .getBody()
                .getSubject();
    }

    public boolean isBusinessToken(String token) {
        try {
            String role = extractRole(token);
            return role != null && "BUSINESS".equalsIgnoreCase(role);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isUserToken(String token) {
        try {
            String role = extractRole(token);
            return role != null && "USER".equalsIgnoreCase(role);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isAdminToken(String token) {
        try {
            String role = extractRole(token);
            return role != null && ("SUPER_ADMIN".equalsIgnoreCase(role) || "MANAGER".equalsIgnoreCase(role));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isSuperAdmin(String token) {
        try {
            String role = extractRole(token);
            return role != null && "SUPER_ADMIN".equalsIgnoreCase(role);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isManagerToken(String token) {
        try {
            String role = extractRole(token);
            return role != null && "MANAGER".equalsIgnoreCase(role);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isOwnerToken(String token) {
        try {
            String role = extractRole(token);
            return role != null && "OWNER".equalsIgnoreCase(role);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isAdminOrOwner(String token) {
        return isAdminToken(token) || isOwnerToken(token);
    }

    public Long extractBusinessId(String token) {
        String jwt = normalize(token);
        if (!isBusinessToken(jwt)) {
            throw new RuntimeException("Invalid token: Not a business token");
        }
        return extractId(jwt);
    }

    public String extractTokenFromRequest(jakarta.servlet.http.HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.toLowerCase().startsWith("bearer ")) {
            return bearerToken.substring(7).trim();
        }
        throw new RuntimeException("Authorization token missing or invalid");
    }

    public void validateUserToken(String token, Long userId) {
        if (!isUserToken(token)) {
            throw new RuntimeException("Invalid token for user");
        }
        Long tokenUserId = extractId(token);
        if (!tokenUserId.equals(userId)) {
            throw new RuntimeException("Token user ID does not match request user ID");
        }
    }

    /* ==================== OWNER REGISTRATION TOKEN ==================== */

    public String generateOwnerRegistrationToken(String email, String passwordHash, long ttlMillis) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(email)                      // subject = email
                .claim("type", "OWNER_REG")             // marker for registration
                .claim("email", email)
                .claim("passwordHash", passwordHash)    // already encoded!
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + ttlMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Map<String, Object> parseOwnerRegistrationToken(String token) {
        var claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();

        String type = (String) claims.get("type");
        if (!"OWNER_REG".equals(type)) {
            throw new RuntimeException("Invalid registration token type");
        }
        Map<String, Object> out = new HashMap<>();
        out.put("email", claims.get("email"));
        out.put("passwordHash", claims.get("passwordHash"));
        out.put("subject", claims.getSubject());
        return out;
    }

    /* ==================== internal ==================== */

    private String normalize(String token) {
        if (token == null) return null;
        return token.replaceFirst("(?i)^Bearer\\s+", "").trim();
    }
}
