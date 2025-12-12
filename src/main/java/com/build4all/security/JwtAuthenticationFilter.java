package com.build4all.security;

import com.build4all.user.domain.Users;
import com.build4all.user.repository.UsersRepository;
import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.repository.AdminUsersRepository;
import com.build4all.business.domain.Businesses;
import com.build4all.business.repository.BusinessesRepository;

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
import java.util.List;
import java.util.Optional;

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

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        // No Authorization header or not Bearer --> just continue
        if (authHeader == null || !authHeader.toLowerCase().startsWith("bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7).trim();

        try {
            // subject = email / phone / admin email / business email ...
            String subject = jwtUtil.extractUsername(token);
            String roleName = jwtUtil.extractRole(token); // "USER", "SUPER_ADMIN", "BUSINESS", "OWNER", "MANAGER"

            // If token is bad or security context already set, skip
            if (subject == null || roleName == null ||
                    SecurityContextHolder.getContext().getAuthentication() != null) {
                filterChain.doFilter(request, response);
                return;
            }

            Object principal = subject; // default: just the String

            // Attach the correct entity depending on role
            switch (roleName.toUpperCase()) {
                case "USER" -> {
                    // Try email, then phone
                    Users user = usersRepository.findByEmail(subject);
                    if (user == null) {
                        user = usersRepository.findByPhoneNumber(subject);
                    }
                    if (user != null) {
                        principal = user; // Users entity
                    }
                }

                case "SUPER_ADMIN", "OWNER" -> {
                    // AdminUser is identified by email in your AuthController
                    Optional<AdminUser> adminOpt = adminUsersRepository.findByEmail(subject);
                    if (adminOpt.isPresent()) {
                        principal = adminOpt.get(); // AdminUser entity
                    }
                }

                case "BUSINESS" , "MANAGER"-> {
                    // Adapt to your repository; here I assume findByEmail(...) exists
                    Optional<Businesses> bizOpt = businessesRepository.findByEmail(subject);
                    if (bizOpt.isPresent()) {
                        principal = bizOpt.get(); // Businesses entity
                    }
                    // If your BusinessesRepository uses another method name
                    // just replace with the correct one.
                }

                default -> {
                    // Other roles in the future: keep principal as String
                }
            }

            // Create authorities: ROLE_USER, ROLE_SUPER_ADMIN, ROLE_BUSINESS, ROLE_OWNER, ROLE_MANAGER
            List<GrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + roleName.toUpperCase())
            );

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(auth);

        } catch (Exception ignored) {
            // invalid token -> leave context empty, request stays anonymous
        }

        filterChain.doFilter(request, response);
    }
}
