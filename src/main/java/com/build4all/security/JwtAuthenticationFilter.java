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

/**
 * JWT Authentication filter that runs once per request.
 *
 * What it does:
 * - Reads Authorization: Bearer <token>
 * - Extracts subject (email/phone) + role from the JWT
 * - Loads the correct principal object (Users/AdminUser/Businesses) from DB based on the role
 * - Creates a Spring Security Authentication and stores it in SecurityContext
 *
 * Why this matters:
 * - After this filter runs, controllers/services can use:
 *   - SecurityContextHolder.getContext().getAuthentication()
 *   - @AuthenticationPrincipal
 *   - @PreAuthorize("hasRole('...')")
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

        // Read the Authorization header: "Bearer <jwt>"
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        // No Authorization header or not "Bearer ..." --> skip auth and continue request normally.
        // Result: request is treated as anonymous by Spring Security.
        if (authHeader == null || !authHeader.toLowerCase().startsWith("bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract raw JWT string (remove "Bearer ")
        String token = authHeader.substring(7).trim();

        try {
            // subject = identifier stored in JWT subject:
            // - user email or phone
            // - admin email
            // - business email or phone (depending how you generate the token)
            String subject = jwtUtil.extractUsername(token);

            // role = claim "role" inside JWT (e.g. USER, SUPER_ADMIN, BUSINESS, OWNER, MANAGER)
            String roleName = jwtUtil.extractRole(token);

            // If token parsing failed OR already authenticated earlier in the chain, do nothing.
            // Example: another filter already set authentication (rare here, but good safety check).
            if (subject == null || roleName == null ||
                    SecurityContextHolder.getContext().getAuthentication() != null) {
                filterChain.doFilter(request, response);
                return;
            }

            // Default principal is the raw subject string.
            // We try to upgrade it into a full entity object (Users/AdminUser/Businesses).
            Object principal = subject;

            // Attach the correct entity depending on role.
            // This is useful because later you can access principal fields directly in controllers.
            switch (roleName.toUpperCase()) {

                case "USER" -> {
                    // In your system, a USER token subject can be email OR phone.
                    // You try email first, then phone.
                    Users user = usersRepository.findByEmail(subject);
                    if (user == null) {
                        user = usersRepository.findByPhoneNumber(subject);
                    }
                    if (user != null) {
                        principal = user; // principal becomes Users entity
                    }
                }

                case "SUPER_ADMIN", "OWNER" -> {
                    // Admin/Owner tokens use email as subject (from AuthController token generation).
                    Optional<AdminUser> adminOpt = adminUsersRepository.findByEmail(subject);
                    if (adminOpt.isPresent()) {
                        principal = adminOpt.get(); // principal becomes AdminUser entity
                    }
                }

                case "BUSINESS" -> {
                    // Business tokens typically use email or phone as subject depending on token generation.
                    // Here you assume findByEmail exists and subject is email.
                    Optional<Businesses> bizOpt = businessesRepository.findByEmail(subject);
                    if (bizOpt.isPresent()) {
                        principal = bizOpt.get(); // principal becomes Businesses entity
                    }
                    // If subject could be phone too, you may want a fallback:
                    // businessesRepository.findByPhoneNumber(subject)
                }

                default -> {
                    // For roles not handled yet (e.g., MANAGER, etc.),
                    // we keep principal as a String subject for now.
                    // NOTE: If MANAGER is also stored in AdminUser, you can include it in the Admin case.
                }
            }

            // Build authorities list used by Spring Security:
            // - If roleName = "USER" => "ROLE_USER"
            // - If roleName = "SUPER_ADMIN" => "ROLE_SUPER_ADMIN"
            // This matches hasRole('USER') / hasRole('SUPER_ADMIN') usage in @PreAuthorize.
            List<GrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + roleName.toUpperCase())
            );

            // Create Authentication object:
            // - principal: entity or subject string
            // - credentials: null (we are not authenticating by password here; token already proves it)
            // - authorities: derived from JWT role claim
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);

            // Adds request details (IP, session id, etc.) for auditing/logging if needed
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // Store the Authentication into the SecurityContext.
            // After this line:
            // - request is authenticated
            // - controllers can check roles
            // - @AuthenticationPrincipal can resolve the principal object
            SecurityContextHolder.getContext().setAuthentication(auth);

        } catch (Exception ignored) {
            // Any parsing/validation exception:
            // - keep SecurityContext empty
            // - request continues as anonymous
            // (You may log here if you want debugging, but don't expose token details.)
        }

        // Continue filter chain (controller execution happens after this)
        filterChain.doFilter(request, response);
    }
}
