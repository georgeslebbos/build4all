package com.build4all.importer.service;

import com.build4all.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * Reads ownerProjectId from Authorization Bearer token.
 *
 * Assumption: JwtUtil exposes a method extractAllClaims(token) or extractOwnerProjectId(token).
 * Adapt the marked line to match your JwtUtil.
 */
@Service
public class JwtTenantContextResolver implements TenantContextResolver {

    private final JwtUtil jwtUtil;

    public JwtTenantContextResolver(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Long resolveOwnerProjectId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new IllegalStateException("Missing Authorization Bearer token");
        }

        String token = auth.substring("Bearer ".length()).trim();

        // ✅ CHANGE THIS LINE to match your JwtUtil:
        Long ownerProjectId = jwtUtil.extractOwnerProjectId(token);

        if (ownerProjectId == null) {
            throw new IllegalStateException("Token does not contain ownerProjectId");
        }
        return ownerProjectId;
    }
}
