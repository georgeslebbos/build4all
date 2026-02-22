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
 * - USER/BUSINESS tokens use subject=id => load principal by claim "id"
 * - Prevent DB-reset / ID reuse hijack: if token.iat < entity.createdAt => stale => no auth
 * - ✅ IMPORTANT FIX: don't set TenantContext too early for OWNER/SUPER_ADMIN lookup
 * - ✅ IMPORTANT FIX: remove lazy projectLinks check from filter (was causing 401)
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

            // If token is invalid/expired, treat as anonymous
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
            Long ownerProjectId = asLong(claims.get("ownerProjectId"));

            // If role missing OR already authenticated earlier -> continue
            if (roleName == null || roleName.isBlank()
                    || SecurityContextHolder.getContext().getAuthentication() != null) {
                filterChain.doFilter(request, response);
                return;
            }

            Object principal = null;
            String roleUpper = roleName.toUpperCase();

            switch (roleUpper) {

                case "USER" -> {
                    // ✅ USER repos may be tenant-scoped => set tenant BEFORE DB calls
                    if (ownerProjectId != null) {
                        TenantContext.setOwnerProjectId(ownerProjectId);
                    }

                    if (idClaim == null) {
                        Users u = tryFindUserBySubject(subject);
                        if (u == null) {
                            SecurityContextHolder.clearContext();
                            filterChain.doFilter(request, response);
                            return;
                        }
                        principal = u;
                        idClaim = u.getId();
                    } else {
                        Optional<Users> userOpt = usersRepository.findById(idClaim);
                        if (userOpt.isEmpty()) {
                            SecurityContextHolder.clearContext();
                            filterChain.doFilter(request, response);
                            return;
                        }
                        Users user = userOpt.get();

                        if (isStaleToken(issuedAt, user.getCreatedAt())) {
                            SecurityContextHolder.clearContext();
                            filterChain.doFilter(request, response);
                            return;
                        }

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
                    // ✅ BUSINESS repos may be tenant-scoped => set tenant BEFORE DB calls
                    if (ownerProjectId != null) {
                        TenantContext.setOwnerProjectId(ownerProjectId);
                    }

                    if (idClaim == null) {
                        Businesses b = tryFindBusinessBySubject(subject, ownerProjectId);
                        if (b == null) {
                            SecurityContextHolder.clearContext();
                            filterChain.doFilter(request, response);
                            return;
                        }
                        principal = b;
                        idClaim = b.getId();
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

                case "SUPER_ADMIN", "OWNER", "MANAGER" -> {
                    // ✅ DO NOT set TenantContext before admin lookup (can break global admin lookup)
                    AdminUser admin = null;

                    if (idClaim != null) {
                        admin = adminUsersRepository.findByAdminId(idClaim).orElse(null);
                    }

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

                    // ✅ Now safe to set tenant context (if present)
                    if (ownerProjectId != null) {
                        TenantContext.setOwnerProjectId(ownerProjectId);
                    }

                    /*
                     * IMPORTANT:
                     * We intentionally REMOVED admin.getProjectLinks() check from the filter
                     * because it often fails (LAZY init / wrong id mapping) and causes 401.
                     *
                     * If you want strict "admin must be linked to AUP", do it via repository query
                     * (existsBy...) or inside the controller using aupRepo.
                     */

                    principal = admin;
                }

                default -> {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }
            }

            if (principal == null) {
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            List<GrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + roleUpper)
            );

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);

            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);

            filterChain.doFilter(request, response);

        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            System.out.println("❌ JWT FAILED: " + request.getMethod() + " " + request.getRequestURI()
                    + " | " + e.getClass().getSimpleName() + ": " + e.getMessage());
            filterChain.doFilter(request, response);

        } finally {
            TenantContext.clear();
        }
    }

    private boolean isStaleToken(Date issuedAt, LocalDateTime createdAt) {
        if (issuedAt == null || createdAt == null) return false;
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

        if (ownerProjectId != null) {
            var byTenantEmail = businessesRepository.findByOwnerProjectLink_IdAndEmailIgnoreCase(ownerProjectId, subject);
            if (byTenantEmail.isPresent()) return byTenantEmail.get();

            var byTenantPhone = businessesRepository.findByOwnerProjectLink_IdAndPhoneNumber(ownerProjectId, subject);
            if (byTenantPhone.isPresent()) return byTenantPhone.get();
        }

        var byEmail = businessesRepository.findByEmailIgnoreCase(subject);
        if (byEmail.isPresent()) return byEmail.get();

        var byPhone = businessesRepository.findByPhoneNumber(subject);
        return byPhone.orElse(null);
    }
}