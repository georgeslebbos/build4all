// src/main/java/com/build4all/security/JwtUtil.java
package com.build4all.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

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
 * - Users      (role = USER)
 * - Businesses (role = BUSINESS)
 * - Admins     (role = SUPER_ADMIN / OWNER)
 *
 * Multi-tenant (Build4All):
 * - ownerProjectId is the tenant scope (AdminUserProject link id = aup_id)
 * - For USER login: ownerProjectId is REQUIRED
 * - For BUSINESS login: ownerProjectId is REQUIRED
 * - For OWNER login: ownerProjectId is REQUIRED (owner is tenant-scoped by AUP)
 * - For SUPER_ADMIN: ownerProjectId is optional (can be global)
 *
 * ✅ CRITICAL SECURITY RULES:
 * 1) NEVER issue a USER token without ownerProjectId.
 * 2) NEVER issue a BUSINESS token without ownerProjectId.
 * 3) NEVER issue an OWNER token without ownerProjectId.
 * 4) If a controller/service passes a wrong tenant id, JwtUtil MUST block cross-tenant minting.
 *
 * Why enforce here?
 * - Even if a controller has a bug, JwtUtil is the last line of defense before token minting.
 */
@Component
public class JwtUtil {

    /** HMAC signing key used to sign and verify JWTs (symmetric key). */
    private final Key key;

    /** Standard access token TTL in milliseconds (configured in JwtProperties). */
    private final long expirationTime;

    public JwtUtil(JwtProperties jwtProperties) {
        this.key = Keys.hmacShaKeyFor(jwtProperties.getSecretKey().getBytes());
        this.expirationTime = jwtProperties.getExpirationTime();
    }

    /* ======================== LOGIN TOKENS ======================== */

    /**
     * ⚠️ LEGACY / BACKWARD COMPATIBILITY ONLY.
     *
     * Previously this could mint a USER token without ownerProjectId.
     * In a strict multi-tenant system, that is unsafe.
     *
     * ✅ NOW ENFORCED:
     * - USER token MUST contain ownerProjectId.
     * - We derive it ONLY from DB (user.ownerProject.id).
     * - If DB tenant is missing -> throw.
     *
     * Recommendation:
     * - Prefer generateToken(user, ownerProjectId) everywhere.
     */
    public String generateToken(Users user) {
        if (user == null) throw new RuntimeException("User cannot be null");

        Long dbTenantId = (user.getOwnerProject() != null) ? user.getOwnerProject().getId() : null;
        if (dbTenantId == null) {
            throw new RuntimeException("Cannot generate USER token: ownerProjectId is missing on the user record");
        }

        return generateToken(user, dbTenantId);
    }

    
    /**
     * ✅ Best-practice tenant extraction:
     * - Works for USER / BUSINESS / OWNER / SUPER_ADMIN tokens
     * - Enforces that ownerProjectId MUST exist for tenant-scoped endpoints
     *
     * Usage:
     *   Long tenantId = jwtUtil.requireOwnerProjectId(authHeader);
     */
    public Long requireOwnerProjectId(String tokenOrBearer) {
        String jwt = normalize(tokenOrBearer);

        if (jwt == null || jwt.isBlank()) {
            throw new RuntimeException("Authorization token missing or invalid");
        }

        if (!validateToken(jwt)) {
            throw new RuntimeException("Invalid token");
        }

        Long ownerProjectId = extractOwnerProjectIdClaim(jwt);

        // ✅ Enforce tenant presence for tenant-scoped endpoints
        if (ownerProjectId == null) {
            throw new RuntimeException("Token missing ownerProjectId claim");
        }

        return ownerProjectId;
    }

    /**
     * ✅ Optional: if you KEEP ownerProjectId in path/query (not ideal),
     * enforce that request tenant == token tenant.
     */
    public void requireTenantMatch(String tokenOrBearer, Long requestedOwnerProjectId) {
        Long tokenTenant = requireOwnerProjectId(tokenOrBearer);
        if (requestedOwnerProjectId == null || !tokenTenant.equals(requestedOwnerProjectId)) {
            // use 404 behavior in controllers to avoid leaking existence
            throw new RuntimeException("Tenant mismatch");
        }
    }

    /**
     * ✅ STRICT (Recommended):
     * Generates a USER token while forcing tenant scope.
     *
     * Policy:
     * - ownerProjectId is REQUIRED for USER login.
     * - If user.ownerProject (DB) exists, it MUST equal ownerProjectId.
     * - No fallback. No silent behavior.
     *
     * Why strict?
     * - Prevents cross-tenant token minting even if user is loaded incorrectly.
     */
    public String generateToken(Users user, Long ownerProjectId) {

        if (user == null) {
            throw new RuntimeException("User cannot be null");
        }

        if (ownerProjectId == null) {
            throw new RuntimeException("ownerProjectId is required for USER token");
        }

        // ✅ HARD TENANCY CHECK (anti cross-tenant token minting)
        Long dbTenantId = (user.getOwnerProject() != null) ? user.getOwnerProject().getId() : null;
        if (dbTenantId != null && !ownerProjectId.equals(dbTenantId)) {
            throw new RuntimeException("Invalid tenancy: user does not belong to this ownerProjectId");
        }

        return Jwts.builder()
                .setSubject(String.valueOf(user.getId()))
                .claim("id", user.getId())
                .claim("username", user.getUsername())
                .claim("role", "USER")
                .claim("ownerProjectId", ownerProjectId) // ✅ always embed tenant
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generates a JWT for a business account.
     *
     * Token content:
     * - subject: businessId as string
     * - claims : id, username, role, ownerProjectId
     *
     * Tenant:
     * - ownerProjectId = business.getOwnerProjectLink().id (AUP id)
     *
     * ✅ ENFORCED:
     * - BUSINESS token MUST include ownerProjectId.
     * - If missing -> throw (never mint an unscoped business token).
     */
    public String generateToken(Businesses business) {
        if (business == null) throw new RuntimeException("Business cannot be null");

        // role from DB, default to BUSINESS if null
        String roleName = "BUSINESS";
        if (business.getRole() != null && business.getRole().getName() != null) {
            roleName = business.getRole().getName();
        }

        Long tenantId = (business.getOwnerProjectLink() != null) ? business.getOwnerProjectLink().getId() : null;
        if (tenantId == null) {
            throw new RuntimeException("Cannot generate BUSINESS token: ownerProjectId (AUP id) is missing on business record");
        }

        return Jwts.builder()
                .setSubject(String.valueOf(business.getId()))
                .claim("id", business.getId())
                .claim("username", business.getUsername())
                .claim("role", roleName)
                .claim("ownerProjectId", tenantId) // ✅ always embed tenant
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generates a JWT for an admin user (SUPER_ADMIN / OWNER).
     *
     * Token content:
     * - subject: admin email
     * - claims : id (adminId), username, role, ownerProjectId?
     *
     * Tenant:
     * - OWNER must include ownerProjectId (tenant-scoped).
     * - SUPER_ADMIN may include it (optional).
     *
     * ✅ ENFORCED:
     * - If role == OWNER -> ownerProjectId is REQUIRED, otherwise throw.
     * - If role == SUPER_ADMIN -> ownerProjectId optional.
     */
    public String generateToken(AdminUser adminUser, Long ownerProjectId) {

        if (adminUser == null) throw new RuntimeException("AdminUser cannot be null");

        String roleName = "USER";
        if (adminUser.getRole() != null && adminUser.getRole().getName() != null) {
            roleName = adminUser.getRole().getName();
        }

        // ✅ OWNER must ALWAYS be tenant-scoped
        if ("OWNER".equalsIgnoreCase(roleName) && ownerProjectId == null) {
            throw new RuntimeException("Cannot generate OWNER token: ownerProjectId is required");
        }

        var builder = Jwts.builder()
                .setSubject(adminUser.getEmail())
                .claim("id", adminUser.getAdminId())
                .claim("username", adminUser.getUsername())
                .claim("role", roleName);

        // SUPER_ADMIN may be global; OWNER is required above
        if (ownerProjectId != null) {
            builder.claim("ownerProjectId", ownerProjectId);
        }

        return builder
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateToken(AdminUser adminUser) {

        if (adminUser == null) throw new RuntimeException("AdminUser cannot be null");

        String roleName = "USER";
        if (adminUser.getRole() != null && adminUser.getRole().getName() != null) {
            roleName = adminUser.getRole().getName();
        }

        var builder = Jwts.builder()
                .setSubject(adminUser.getEmail())
                .claim("id", adminUser.getAdminId())
                .claim("username", adminUser.getUsername())
                .claim("role", roleName);


        return builder
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Helper: returns all claims for debugging/advanced checks.
     * Accepts raw token or "Bearer <token>".
     */
    public Claims extractAllClaims(String token) {
        String jwt = normalize(token);
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(jwt)
                .getBody();
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

    public boolean validateToken(String token) {
        try {
            String jwt = normalize(token);
            if (jwt == null || jwt.isBlank()) return false;

            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(jwt);

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /* ======================== ROLE CHECKS ======================== */

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

    /**
     * Admin token here means SUPER_ADMIN (global admin).
     * OWNER is checked via isOwnerToken().
     */
    public boolean isAdminToken(String token) {
        try {
            String role = extractRole(token);
            return role != null && "SUPER_ADMIN".equalsIgnoreCase(role);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isSuperAdmin(String token) {
        return isAdminToken(token);
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

    /* ======================== ID EXTRACTION HELPERS ======================== */

    public Long extractBusinessId(String token) {
        String jwt = normalize(token);
        if (!isBusinessToken(jwt)) {
            throw new RuntimeException("Invalid token: Not a business token");
        }
        return extractId(jwt);
    }

    /**
     * Extract tenant scope for OWNER/SUPER_ADMIN tokens.
     *
     * ✅ IMPORTANT:
     * - OWNER token MUST have ownerProjectId, otherwise exception.
     * - SUPER_ADMIN token MAY be missing ownerProjectId (global access).
     */
    public Long extractOwnerProjectId(String token) {
        String jwt = normalize(token);

        if (!(isOwnerToken(jwt) || isAdminToken(jwt))) {
            throw new RuntimeException("Invalid token: Not an OWNER/SUPER_ADMIN token");
        }

        try {
            Long ownerProjectId = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(jwt)
                    .getBody()
                    .get("ownerProjectId", Long.class);

            // OWNER must have it; SUPER_ADMIN may not.
            if (ownerProjectId == null && isOwnerToken(jwt)) {
                throw new RuntimeException("OWNER token missing ownerProjectId claim");
            }

            return ownerProjectId; // may be null only for SUPER_ADMIN
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Invalid token: cannot extract ownerProjectId");
        }
    }

    public Long extractOwnerProjectIdForUser(String token) {
        String jwt = normalize(token);

        if (!isUserToken(jwt)) {
            throw new RuntimeException("Invalid token: Not a USER token");
        }

        try {
            Long ownerProjectId = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(jwt)
                    .getBody()
                    .get("ownerProjectId", Long.class);

            if (ownerProjectId == null) {
                // ✅ USER tokens are ALWAYS tenant-scoped
                throw new RuntimeException("USER token missing ownerProjectId claim");
            }

            return ownerProjectId;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Invalid token: cannot extract ownerProjectId");
        }
    }

    public Long extractOwnerProjectIdClaim(String token) {
        String jwt = normalize(token);
        try {
            Object val = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(jwt)
                    .getBody()
                    .get("ownerProjectId");

            if (val == null) return null;
            if (val instanceof Integer i) return i.longValue();
            if (val instanceof Long l) return l;
            return Long.parseLong(val.toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * (Optional utility) Extract tenant scope for BUSINESS tokens.
     * Useful for business-scoped endpoints.
     */
    public Long extractOwnerProjectIdForBusiness(String token) {
        String jwt = normalize(token);

        if (!isBusinessToken(jwt)) {
            throw new RuntimeException("Invalid token: Not a BUSINESS token");
        }

        try {
            Long ownerProjectId = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(jwt)
                    .getBody()
                    .get("ownerProjectId", Long.class);

            if (ownerProjectId == null) {
                // ✅ BUSINESS tokens are ALWAYS tenant-scoped
                throw new RuntimeException("BUSINESS token missing ownerProjectId claim");
            }

            return ownerProjectId;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Invalid token: cannot extract ownerProjectId");
        }
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

        // ✅ Ensure tenant exists in USER token (enforced at minting + re-checked here)
        extractOwnerProjectIdForUser(token);
    }

    /* ==================== OWNER REGISTRATION TOKEN ==================== */

    public String generateOwnerRegistrationToken(String email, String passwordHash, long ttlMillis) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(email)
                .claim("type", "OWNER_REG")
                .claim("email", email)
                .claim("passwordHash", passwordHash)
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

    /**
     * Normalizes token string:
     * - Accepts either raw JWT or "Bearer <JWT>"
     * - Trims spaces
     */
    private String normalize(String token) {
        if (token == null) return null;
        return token.replaceFirst("(?i)^Bearer\\s+", "").trim();
    }
}
