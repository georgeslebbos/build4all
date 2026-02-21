package com.build4all.security;

import com.build4all.user.domain.Users;
import com.build4all.user.repository.UsersRepository;
import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.repository.AdminUsersRepository;
import com.build4all.business.domain.Businesses;
import com.build4all.business.repository.BusinessesRepository;

import io.jsonwebtoken.Claims;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * JWT Authentication filter that runs once per request.
 *
 * Fixes:
 * - USER/BUSINESS tokens use subject=id => load principal by claim "id" (NOT subject email/phone)
 * - Prevent DB-reset / ID reuse hijack:
 *     if token.iat < entity.createdAt => token is stale => do NOT authenticate
 * - Clears TenantContext to avoid ThreadLocal leak
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UsersRepository usersRepository;
    private final AdminUsersRepository adminUsersRepository;
    private final BusinessesRepository businessesRepository;

    public JwtAuthenticationFilter(
            JwtUtil jwtUtil,
            UsersRepository usersRepository,
            AdminUsersRepository adminUsersRepository,
            BusinessesRepository businessesRepository
    ) {
        this.jwtUtil = jwtUtil;
        this.usersRepository = usersRepository;
        this.adminUsersRepository = adminUsersRepository;
        this.businessesRepository = businessesRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

            // No Authorization header or not "Bearer ..." --> skip auth and continue.
            if (authHeader == null || !authHeader.toLowerCase().startsWith("bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }

            String token = authHeader.substring(7).trim();

            // If token is invalid/expired, treat as anonymous (don’t block public endpoints).
            if (!jwtUtil.validateToken(token)) {
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            // Parse claims once
            Claims claims = jwtUtil.extractAllClaims(token);

            String roleName = claims.get("role", String.class);
            String subject = claims.getSubject(); // USER/BIZ => id string; ADMIN => email
            Long idClaim = asLong(claims.get("id"));
            Date issuedAt = claims.getIssuedAt();

            // Set tenant early for downstream repos/services (if present)
            Long ownerProjectId = asLong(claims.get("ownerProjectId"));
            if (ownerProjectId != null) {
                TenantContext.setOwnerProjectId(ownerProjectId);
            }

            // If role missing OR already authenticated earlier -> continue
            if (roleName == null || roleName.isBlank()
                    || SecurityContextHolder.getContext().getAuthentication() != null) {
                filterChain.doFilter(request, response);
                return;
            }

            Object principal = null;

            switch (roleName.toUpperCase()) {

                case "USER" -> {
                    // ✅ USER tokens: subject is id; use claim "id" as source of truth
                    if (idClaim == null) {
                        // fallback (very old tokens): maybe subject was email/phone
                        Users u = tryFindUserBySubject(subject);
                        if (u == null) {
                            SecurityContextHolder.clearContext();
                            filterChain.doFilter(request, response);
                            return;
                        }
                        idClaim = u.getId();
                        principal = u;
                    } else {
                        Optional<Users> userOpt = usersRepository.findById(idClaim);
                        if (userOpt.isEmpty()) {
                            SecurityContextHolder.clearContext();
                            filterChain.doFilter(request, response);
                            return;
                        }
                        Users user = userOpt.get();

                        // ✅ Prevent DB reset / ID reuse hijack
                        if (isStaleToken(issuedAt, user.getCreatedAt())) {
                            SecurityContextHolder.clearContext();
                            filterChain.doFilter(request, response);
                            return;
                        }

                        // ✅ Extra safety: token tenant must match DB tenant (when present)
                        if (ownerProjectId != null
                                && user.getOwnerProject() != null
                                && user.getOwnerProject().getId() != null
                                && !ownerProjectId.equals(user.getOwnerProject().getId())) {
                            SecurityContextHolder.clearContext();
                            filterChain.doFilter(request, response);
                            return;
                        }

                        principal = user;
                    }
                }

                case "BUSINESS" -> {
                    if (idClaim == null) {
                        // fallback (old tokens): maybe subject was email/phone
                        Businesses b = tryFindBusinessBySubject(subject, ownerProjectId);
                        if (b == null) {
                            SecurityContextHolder.clearContext();
                            filterChain.doFilter(request, response);
                            return;
                        }
                        idClaim = b.getId();
                        principal = b;
                    } else {
                        Optional<Businesses> bizOpt = businessesRepository.findById(idClaim);
                        if (bizOpt.isEmpty()) {
                            SecurityContextHolder.clearContext();
                            filterChain.doFilter(request, response);
                            return;
                        }
                        Businesses biz = bizOpt.get();

                        if (isStaleToken(issuedAt, biz.getCreatedAt())) {
                            SecurityContextHolder.clearContext();
                            filterChain.doFilter(request, response);
                            return;
                        }

                        if (ownerProjectId != null
                                && biz.getOwnerProjectLink() != null
                                && biz.getOwnerProjectLink().getId() != null
                                && !ownerProjectId.equals(biz.getOwnerProjectLink().getId())) {
                            SecurityContextHolder.clearContext();
                            filterChain.doFilter(request, response);
                            return;
                        }

                        principal = biz;
                    }
                }

                // ✅ treat MANAGER like admin user too (if you use it)
                case "SUPER_ADMIN", "OWNER", "MANAGER" -> {
                    AdminUser admin = null;

                    // Prefer ID claim (adminId) if present
                    if (idClaim != null) {
                        admin = adminUsersRepository.findByAdminId(idClaim).orElse(null);
                    }

                    // Fallback to subject email
                    if (admin == null && subject != null && !subject.isBlank()) {
                        admin = adminUsersRepository.findByEmail(subject).orElse(null);
                    }

                    if (admin == null) {
                        SecurityContextHolder.clearContext();
                        filterChain.doFilter(request, response);
                        return;
                    }

                    if (isStaleToken(issuedAt, admin.getCreatedAt())) {
                        SecurityContextHolder.clearContext();
                        filterChain.doFilter(request, response);
                        return;
                    }

                    // Optional extra: if OWNER/MANAGER token includes ownerProjectId,
                    // ensure this admin is actually linked to that AUP.
                    if (ownerProjectId != null && (roleName.equalsIgnoreCase("OWNER") || roleName.equalsIgnoreCase("MANAGER"))) {
                        boolean linked = admin.getProjectLinks() != null
                                && admin.getProjectLinks().stream().anyMatch(l -> ownerProjectId.equals(l.getId()));
                        if (!linked) {
                            SecurityContextHolder.clearContext();
                            filterChain.doFilter(request, response);
                            return;
                        }
                    }

                    principal = admin;
                }

                default -> {
                    // Unknown role => do not authenticate
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }
            }

            // If principal still null, don’t authenticate
            if (principal == null) {
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            List<GrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + roleName.toUpperCase())
            );

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);

            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);

            filterChain.doFilter(request, response);

        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            // dev log
            System.out.println("❌ JWT FAILED: " + request.getMethod() + " " + request.getRequestURI()
                    + " | " + e.getClass().getSimpleName() + ": " + e.getMessage());

            filterChain.doFilter(request, response);

        } finally {
            // ✅ prevent tenant leak across requests
            TenantContext.clear();
        }
    }

    private boolean isStaleToken(Date issuedAt, LocalDateTime createdAt) {
        if (issuedAt == null || createdAt == null) return false;

        // tiny buffer to avoid minor clock skew
        var tokenIat = issuedAt.toInstant();
        var accountCreated = createdAt.atZone(ZoneId.systemDefault()).toInstant().minusSeconds(10);

        return tokenIat.isBefore(accountCreated);
    }

    private Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Long l) return l;
        if (v instanceof Integer i) return i.longValue();
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return null; }
    }

    private Users tryFindUserBySubject(String subject) {
        if (subject == null || subject.isBlank()) return null;
        Users u = usersRepository.findByEmail(subject);
        if (u == null) u = usersRepository.findByPhoneNumber(subject);
        return u;
    }

    private Businesses tryFindBusinessBySubject(String subject, Long ownerProjectId) {
        if (subject == null || subject.isBlank()) return null;

        // Prefer tenant-aware if you have it
        if (ownerProjectId != null) {
            var byTenantEmail = businessesRepository.findByOwnerProjectLink_IdAndEmailIgnoreCase(ownerProjectId, subject);
            if (byTenantEmail.isPresent()) return byTenantEmail.get();

            var byTenantPhone = businessesRepository.findByOwnerProjectLink_IdAndPhoneNumber(ownerProjectId, subject);
            if (byTenantPhone.isPresent()) return byTenantPhone.get();
        }

        // Legacy fallback
        var byEmail = businessesRepository.findByEmailIgnoreCase(subject);
        if (byEmail.isPresent()) return byEmail.get();

        var byPhone = businessesRepository.findByPhoneNumber(subject);
        return byPhone.orElse(null);
    }
}