// src/main/java/com/build4all/security/JwtUtil.java
package com.build4all.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
// import jakarta.servlet.http.HttpServletRequest; // (unused here because you used fully-qualified type below)

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
 *
 * How this class is used in your project:
 * - Controllers/filters generate tokens at login (generateToken(...))
 * - Controllers/filters validate/authorize requests by reading claims (extractRole/extractId/isXToken)
 * - "Owner registration token" is a special temporary token used to carry data after OTP verification.
 */
@Component
public class JwtUtil {

    /** HMAC signing key used to sign and verify JWTs (symmetric key). */
    private final Key key;

    /** Standard access token TTL in milliseconds (configured in JwtProperties). */
    private final long expirationTime;

    public JwtUtil(JwtProperties jwtProperties) {
        // Secret must be long enough for HS256, otherwise jjwt will throw an exception at startup.
        this.key = Keys.hmacShaKeyFor(jwtProperties.getSecretKey().getBytes());
        this.expirationTime = jwtProperties.getExpirationTime();
    }

    /* ======================== LOGIN TOKENS ======================== */

    /**
     * Generates a JWT for a normal end-user.
     *
     * Token content:
     * - subject: user email if exists, otherwise phone
     * - claims: id, username, firstName, lastName, profileImageUrl, role
     *
     * Note:
     * - "role" is taken from DB (user.getRole().getName()) and defaults to "USER" if null.
     */
    public String generateToken(Users user) {
        String subject = user.getEmail() != null ? user.getEmail() : user.getPhoneNumber();

        // 👇 read role from DB, default to USER if null
        String roleName = "USER";
        if (user.getRole() != null && user.getRole().getName() != null) {
            roleName = user.getRole().getName();
        }

        return Jwts.builder()
                .setSubject(subject)
                .claim("id", user.getId()) // used later by extractId()
                .claim("username", user.getUsername())
                .claim("firstName", user.getFirstName())
                .claim("lastName", user.getLastName())
                .claim("profileImageUrl", user.getProfilePictureUrl())
                .claim("role", roleName)   // used later by extractRole() and isUserToken()
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(key, SignatureAlgorithm.HS256) // HS256 = HMAC-SHA256 symmetric signing
                .compact();
    }

    /**
     * Generates a JWT for a business account.
     *
     * Token content:
     * - subject: business email if exists, otherwise phone (must exist)
     * - claims: id, businessName, logoUrl, bannerUrl, role
     */
    public String generateToken(Businesses business) {
        String subject = (business.getEmail() != null && !business.getEmail().isEmpty())
                ? business.getEmail()
                : business.getPhoneNumber();

        // Business must have an identifier used as "subject" so later you can call extractUsername().
        if (subject == null || subject.isEmpty()) {
            throw new IllegalArgumentException("Business must have either email or phone number to generate token");
        }

        // 👇 read role from DB, default to USER if null
        String roleName = "BUSINESS";
        if (business.getRole() != null && business.getRole().getName() != null) {
            roleName = business.getRole().getName();
        }

        return Jwts.builder()
                .setSubject(subject)
                .claim("id", business.getId())
                .claim("businessName", business.getBusinessName())
                .claim("logoUrl", business.getBusinessLogoUrl())
                .claim("bannerUrl", business.getBusinessBannerUrl())
                .claim("role", roleName) // expected to be "BUSINESS"
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generates a JWT for an admin user (SUPER_ADMIN / OWNER / MANAGER ...).
     *
     * Token content:
     * - subject: admin email
     * - claims: id (adminId), username, role
     *
     * NOTE:
     * - The default roleName="USER" is just a fallback; usually adminUser.role is never null.
     * - extractAdminId() simply delegates to extractId() (same claim name "id").
     */
    public String generateToken(AdminUser adminUser) {

        // 👇 read role from DB, default to USER if null
        String roleName = "USER";
        if (adminUser.getRole() != null && adminUser.getRole().getName() != null) {
            roleName = adminUser.getRole().getName();
        }

        return Jwts.builder()
                .setSubject(adminUser.getEmail())
                .claim("id", adminUser.getAdminId())
                .claim("username", adminUser.getUsername())
                .claim("role", roleName)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /* ======================== EXTRACTION HELPERS ======================== */

    /**
     * Reads the "role" claim from the token.
     *
     * Returns null if token is invalid/expired/signature mismatch/etc.
     * (You intentionally swallow exceptions and return null to simplify callers.)
     */
    public String extractRole(String token) {
        try {
            String jwt = normalize(token); // removes optional "Bearer " prefix
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

    /**
     * Reads the "id" claim from the token.
     *
     * Used for:
     * - Users: userId
     * - Businesses: businessId
     * - Admins: adminId
     */
    public Long extractId(String token) {
        String jwt = normalize(token);
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(jwt)
                .getBody()
                .get("id", Long.class);
    }

    /**
     * Semantic alias for admin tokens. In your tokens, adminId is stored in the same claim "id".
     */
    public Long extractAdminId(String token) {
        return extractId(token);
    }

    /**
     * Returns the JWT subject (setSubject(...)).
     *
     * In your project you use it as:
     * - Users: email or phone
     * - Businesses: email or phone
     * - Admins: email
     */
    public String extractUsername(String token) {
        String jwt = normalize(token);
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(jwt)
                .getBody()
                .getSubject();
    }

    /**
     * Checks if token role == BUSINESS (case-insensitive).
     * This relies on extractRole(...) which returns null if token invalid.
     */
    public boolean isBusinessToken(String token) {
        try {
            String role = extractRole(token);
            return role != null && "BUSINESS".equalsIgnoreCase(role);
        } catch (Exception e) {
            return false;
        }
    }

    /** Checks if token role == USER. */
    public boolean isUserToken(String token) {
        try {
            String role = extractRole(token);
            return role != null && "USER".equalsIgnoreCase(role);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if token belongs to an admin (SUPER_ADMIN or MANAGER).
     *
     * NOTE:
     * - OWNER is NOT included here intentionally, that's why you later have isOwnerToken() and isAdminOrOwner().
     */
    public boolean isAdminToken(String token) {
        try {
            String role = extractRole(token);
            return role != null && ("SUPER_ADMIN".equalsIgnoreCase(role) || "MANAGER".equalsIgnoreCase(role));
        } catch (Exception e) {
            return false;
        }
    }

    /** Checks if token role == SUPER_ADMIN. */
    public boolean isSuperAdmin(String token) {
        try {
            String role = extractRole(token);
            return role != null && "SUPER_ADMIN".equalsIgnoreCase(role);
        } catch (Exception e) {
            return false;
        }
    }

    /** Checks if token role == MANAGER. */
    public boolean isManagerToken(String token) {
        try {
            String role = extractRole(token);
            return role != null && "MANAGER".equalsIgnoreCase(role);
        } catch (Exception e) {
            return false;
        }
    }

    /** Checks if token role == OWNER. */
    public boolean isOwnerToken(String token) {
        try {
            String role = extractRole(token);
            return role != null && "OWNER".equalsIgnoreCase(role);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Convenience check used by controllers:
     * - allow both "admin" roles (SUPER_ADMIN/MANAGER) and "OWNER".
     */
    public boolean isAdminOrOwner(String token) {
        return isAdminToken(token) || isOwnerToken(token);
    }

    /**
     * Extracts businessId from token after asserting it's a BUSINESS token.
     *
     * If it's not a business token, you throw RuntimeException to stop controller flow.
     */
    public Long extractBusinessId(String token) {
        String jwt = normalize(token);
        if (!isBusinessToken(jwt)) {
            throw new RuntimeException("Invalid token: Not a business token");
        }
        return extractId(jwt);
    }

    /**
     * Helper to read Authorization header and return the raw JWT (without "Bearer ").
     * Throws if header is missing or not bearer.
     */
    public String extractTokenFromRequest(jakarta.servlet.http.HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.toLowerCase().startsWith("bearer ")) {
            return bearerToken.substring(7).trim();
        }
        throw new RuntimeException("Authorization token missing or invalid");
    }

    /**
     * Validates that:
     * - token is a USER token
     * - token's id matches the requested userId
     *
     * Used for "user can only access their own data" authorization checks.
     */
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

    /**
     * Generates a short-lived token used during owner registration flow.
     *
     * What makes it special:
     * - claim("type", "OWNER_REG") so you can distinguish it from normal login tokens
     * - includes encoded passwordHash so you can finish registration later without storing plaintext
     *
     * Typical flow:
     * 1) OTP verified -> generateOwnerRegistrationToken(...)
     * 2) client calls "complete registration" endpoint with this token
     * 3) parseOwnerRegistrationToken(...) to retrieve email + passwordHash
     */
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

    /**
     * Parses the owner registration token and extracts the needed values.
     *
     * Security logic:
     * - Enforces claim("type") == "OWNER_REG" so normal login tokens cannot be used here.
     */
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

    /**
     * Normalizes token string:
     * - Accepts either raw JWT or "Bearer <JWT>"
     * - Trims spaces
     *
     * Regex "(?i)" makes "Bearer" case-insensitive.
     */
    private String normalize(String token) {
        if (token == null) return null;
        return token.replaceFirst("(?i)^Bearer\\s+", "").trim();
    }
}
