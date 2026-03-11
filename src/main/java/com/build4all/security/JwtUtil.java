package com.build4all.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.build4all.admin.domain.AdminUser;
import com.build4all.business.domain.Businesses;
import com.build4all.common.errors.ApiException;
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
 */
@Component
public class JwtUtil {

    private final Key key;
    private final long expirationTime;

    public JwtUtil(JwtProperties jwtProperties) {
        this.key = Keys.hmacShaKeyFor(jwtProperties.getSecretKey().getBytes());
        this.expirationTime = jwtProperties.getExpirationTime();
    }

    /* ======================== LOGIN TOKENS ======================== */

    public String generateToken(Users user) {
        if (user == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USER_REQUIRED", "User cannot be null");
        }

        Long dbTenantId = (user.getOwnerProject() != null) ? user.getOwnerProject().getId() : null;
        if (dbTenantId == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "USER_TENANT_MISSING",
                    "Cannot generate USER token: ownerProjectId is missing on the user record"
            );
        }

        return generateToken(user, dbTenantId);
    }

    /**
     * Works for USER / BUSINESS / OWNER / SUPER_ADMIN tokens.
     * Enforces that ownerProjectId MUST exist for tenant-scoped endpoints.
     */
    public Long requireOwnerProjectId(String tokenOrBearer) {
        String jwt = stripBearer(tokenOrBearer);

        if (jwt == null || jwt.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Missing token");
        }

        Long ownerProjectId = extractOwnerProjectIdClaim(jwt);

        if (ownerProjectId == null) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "TENANT_SCOPE_MISSING",
                    "Token missing ownerProjectId claim"
            );
        }

        return ownerProjectId;
    }

    public void requireTenantMatch(String tokenOrBearer, Long requestedOwnerProjectId) {
        Long tokenTenant = requireOwnerProjectId(tokenOrBearer);

        if (requestedOwnerProjectId == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "AUP_ID_REQUIRED",
                    "aupId is required"
            );
        }

        if (!tokenTenant.equals(requestedOwnerProjectId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "TENANT_MISMATCH",
                    "You are not allowed to access this app"
            );
        }
    }

    public String generateToken(Users user, Long ownerProjectId) {
        if (user == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USER_REQUIRED", "User cannot be null");
        }

        if (ownerProjectId == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "OWNER_PROJECT_ID_REQUIRED",
                    "ownerProjectId is required for USER token"
            );
        }

        Long dbTenantId = (user.getOwnerProject() != null) ? user.getOwnerProject().getId() : null;
        if (dbTenantId != null && !ownerProjectId.equals(dbTenantId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "INVALID_TENANCY",
                    "Invalid tenancy: user does not belong to this ownerProjectId"
            );
        }

        return Jwts.builder()
                .setSubject(String.valueOf(user.getId()))
                .claim("id", user.getId())
                .claim("username", user.getUsername())
                .claim("role", "USER")
                .claim("ownerProjectId", ownerProjectId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateToken(Businesses business) {
        if (business == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BUSINESS_REQUIRED", "Business cannot be null");
        }

        String roleName = "BUSINESS";
        if (business.getRole() != null && business.getRole().getName() != null) {
            roleName = business.getRole().getName();
        }

        Long tenantId = (business.getOwnerProjectLink() != null) ? business.getOwnerProjectLink().getId() : null;
        if (tenantId == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "BUSINESS_TENANT_MISSING",
                    "Cannot generate BUSINESS token: ownerProjectId (AUP id) is missing on business record"
            );
        }

        return Jwts.builder()
                .setSubject(String.valueOf(business.getId()))
                .claim("id", business.getId())
                .claim("username", business.getUsername())
                .claim("role", roleName)
                .claim("ownerProjectId", tenantId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateToken(AdminUser adminUser, Long ownerProjectId) {
        if (adminUser == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ADMIN_REQUIRED", "AdminUser cannot be null");
        }

        String roleName = "USER";
        if (adminUser.getRole() != null && adminUser.getRole().getName() != null) {
            roleName = adminUser.getRole().getName();
        }

        if ("OWNER".equalsIgnoreCase(roleName) && ownerProjectId == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "OWNER_TENANT_MISSING",
                    "Cannot generate OWNER token: ownerProjectId is required"
            );
        }

        var builder = Jwts.builder()
                .setSubject(adminUser.getEmail())
                .claim("id", adminUser.getAdminId())
                .claim("username", adminUser.getUsername())
                .claim("role", roleName);

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
        if (adminUser == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ADMIN_REQUIRED", "AdminUser cannot be null");
        }

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
            throw new ApiException(HttpStatus.FORBIDDEN, "INVALID_BUSINESS_TOKEN", "Invalid token: Not a business token");
        }
        return extractId(jwt);
    }

    /**
     * Extract tenant scope for OWNER/SUPER_ADMIN tokens.
     * - OWNER token MUST have ownerProjectId
     * - SUPER_ADMIN token may be global
     */
    public Long extractOwnerProjectId(String token) {
        String jwt = normalize(token);

        if (!(isOwnerToken(jwt) || isAdminToken(jwt))) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "INVALID_OWNER_ADMIN_TOKEN",
                    "Invalid token: Not an OWNER/SUPER_ADMIN token"
            );
        }

        try {
            Long ownerProjectId = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(jwt)
                    .getBody()
                    .get("ownerProjectId", Long.class);

            if (ownerProjectId == null && isOwnerToken(jwt)) {
                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "OWNER_TENANT_MISSING",
                        "OWNER token missing ownerProjectId claim"
                );
            }

            return ownerProjectId;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_TOKEN",
                    "Invalid token: cannot extract ownerProjectId"
            );
        }
    }

    public Long extractOwnerProjectIdForUser(String token) {
        String jwt = normalize(token);

        if (!isUserToken(jwt)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INVALID_USER_TOKEN", "Invalid token: Not a USER token");
        }

        try {
            Long ownerProjectId = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(jwt)
                    .getBody()
                    .get("ownerProjectId", Long.class);

            if (ownerProjectId == null) {
                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "USER_TENANT_MISSING",
                        "USER token missing ownerProjectId claim"
                );
            }

            return ownerProjectId;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_TOKEN",
                    "Invalid token: cannot extract ownerProjectId"
            );
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

    public Long extractOwnerProjectIdForBusiness(String token) {
        String jwt = normalize(token);

        if (!isBusinessToken(jwt)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INVALID_BUSINESS_TOKEN", "Invalid token: Not a BUSINESS token");
        }

        try {
            Long ownerProjectId = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(jwt)
                    .getBody()
                    .get("ownerProjectId", Long.class);

            if (ownerProjectId == null) {
                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "BUSINESS_TENANT_MISSING",
                        "BUSINESS token missing ownerProjectId claim"
                );
            }

            return ownerProjectId;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_TOKEN",
                    "Invalid token: cannot extract ownerProjectId"
            );
        }
    }

    public String extractTokenFromRequest(jakarta.servlet.http.HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.toLowerCase().startsWith("bearer ")) {
            return bearerToken.substring(7).trim();
        }
        throw new ApiException(
                HttpStatus.UNAUTHORIZED,
                "AUTH_TOKEN_MISSING",
                "Authorization token missing or invalid"
        );
    }

    public void validateUserToken(String token, Long userId) {
        if (!isUserToken(token)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INVALID_USER_TOKEN", "Invalid token for user");
        }

        Long tokenUserId = extractId(token);
        if (!tokenUserId.equals(userId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "USER_ID_MISMATCH",
                    "Token user ID does not match request user ID"
            );
        }

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
        try {
            var claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String type = (String) claims.get("type");
            if (!"OWNER_REG".equals(type)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_REGISTRATION_TOKEN_TYPE",
                        "Invalid registration token type"
                );
            }

            Map<String, Object> out = new HashMap<>();
            out.put("email", claims.get("email"));
            out.put("passwordHash", claims.get("passwordHash"));
            out.put("subject", claims.getSubject());
            return out;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_REGISTRATION_TOKEN",
                    "Invalid or expired registration token"
            );
        }
    }

    public String generateOwnerBootstrapToken(AdminUser adminUser) {
        if (adminUser == null) {
            throw new RuntimeException("AdminUser cannot be null");
        }

        String roleName = "OWNER";
        if (adminUser.getRole() != null && adminUser.getRole().getName() != null) {
            roleName = adminUser.getRole().getName();
        }

        if (!"OWNER".equalsIgnoreCase(roleName)) {
            throw new RuntimeException("Bootstrap token is allowed only for OWNER");
        }

        return Jwts.builder()
                .setSubject(adminUser.getEmail())
                .claim("id", adminUser.getAdminId())
                .claim("username", adminUser.getUsername())
                .claim("role", "OWNER")
                .claim("setupMode", true)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
    
    /* ==================== internal ==================== */

    private String normalize(String token) {
        if (token == null) return null;
        return token.replaceFirst("(?i)^Bearer\\s+", "").trim();
    }

    private String stripBearer(String token) {
        return normalize(token);
    }
}