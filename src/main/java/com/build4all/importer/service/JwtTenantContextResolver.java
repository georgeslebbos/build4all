package com.build4all.importer.service;

import com.build4all.common.errors.ApiException;
import com.build4all.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class JwtTenantContextResolver implements TenantContextResolver {

    private final JwtUtil jwtUtil;

    public JwtTenantContextResolver(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Long resolveOwnerProjectId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");

        if (auth == null || auth.isBlank() || !auth.toLowerCase().startsWith("bearer ")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                    "Missing Authorization Bearer token");
        }

        String token = auth.substring(7).trim();

        if (!jwtUtil.validateToken(token)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                    "Invalid or expired token");
        }

        // ✅ safer: don’t throw RuntimeException internally
        Long ownerProjectId = jwtUtil.extractOwnerProjectIdClaim(token);

        // ✅ for import endpoints we ALWAYS need tenant
        if (ownerProjectId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "TOKEN_MISSING_TENANT",
                    "Token missing ownerProjectId. Login again as OWNER (tenant-scoped).");
        }

        return ownerProjectId;
    }
}