package com.build4all.security;

import com.build4all.user.repository.UsersRepository;
import com.build4all.admin.repository.AdminUsersRepository;      // 👈 NEW
import com.build4all.business.repository.BusinessesRepository;  // 👈 NEW

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;
import java.util.List;

/**
 * Main Spring Security configuration.
 *
 * What this class does (high level):
 * 1) Exposes PasswordEncoder bean (BCrypt)
 * 2) Registers JwtAuthenticationFilter (your custom JWT → SecurityContext loader)
 * 3) Configures which endpoints are public vs secured
 * 4) Enables method-level security (@PreAuthorize / @PostAuthorize / etc.)
 * 5) Configures CORS (cross-origin requests)
 * 6) Serves /uploads/** from the local filesystem (so images can be accessed via HTTP)
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true) // enables @PreAuthorize / @PostAuthorize on methods
public class SecurityConfig implements WebMvcConfigurer {

    /* =========================================================
     * 1) Static resources mapping (/uploads/** → local folder)
     * =========================================================
     *
     * Why needed?
     * - When you store uploaded files under a local "uploads/" directory,
     *   Spring needs a resource handler to serve them as static content.
     * - Example:
     *   file saved at:   uploads/owner/1/2/app/logo.png
     *   accessible via:  http://<host>/uploads/owner/1/2/app/logo.png
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Absolute filesystem location of the "uploads" directory (converted to a "file:" URI)
        String uploadPath = Paths.get("uploads").toAbsolutePath().toUri().toString();

        registry
                .addResourceHandler("/uploads/**")     // URL path pattern
                .addResourceLocations(uploadPath);     // filesystem folder as static resource root
    }

    /* =========================================================
     * 2) PasswordEncoder bean
     * =========================================================
     *
     * BCrypt is used to hash passwords securely.
     * Any service/controller that needs to encode or match passwords can @Autowired PasswordEncoder.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /* =========================================================
     * 3) JWT Authentication Filter bean
     * =========================================================
     *
     * This creates your JwtAuthenticationFilter instance and injects:
     * - JwtUtil: parse/validate token + read claims (role, id, subject...)
     * - UsersRepository: load Users principal if token role is USER
     * - AdminUsersRepository: load AdminUser principal if role is SUPER_ADMIN / OWNER / MANAGER
     * - BusinessesRepository: load Businesses principal if role is BUSINESS
     *
     * The filter is expected to:
     * - read Authorization: Bearer <token>
     * - validate token signature/expiry
     * - determine role
     * - load the correct entity (User/Admin/Business)
     * - build Authentication object + store it in SecurityContextHolder
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtUtil jwtUtil,
            UsersRepository usersRepository,
            AdminUsersRepository adminUsersRepository,
            BusinessesRepository businessesRepository
    ) {
        return new JwtAuthenticationFilter(
                jwtUtil,
                usersRepository,
                adminUsersRepository,
                businessesRepository
        );
    }

    /* =========================================================
     * 4) Main Security Filter Chain
     * =========================================================
     *
     * This is the core security pipeline:
     * - Stateless (no server session)
     * - JWT authentication via your filter
     * - Defines which endpoints are public vs authenticated
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter)
            throws Exception {

        http
                // Since you're using JWT (stateless API), CSRF protection is typically disabled.
                // CSRF mainly protects cookie-based browser sessions, not Bearer token APIs.
                .csrf(csrf -> csrf.disable())

                // Enables CORS using the CorsConfigurationSource bean below.
                .cors(cors -> {})

                // Stateless: Spring Security won't create or use HTTP sessions.
                // Every request must include the token again.
                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // ✅ IMPORTANT: return clear JSON responses for 401/403 instead of empty bodies.
                .exceptionHandling(ex -> ex
                        // 401 → not authenticated (missing/invalid token)
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(HttpStatus.UNAUTHORIZED.value());
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            res.getWriter().write("{\"error\":\"Unauthorized - missing or invalid token\"}");
                        })
                        // 403 → authenticated, but not allowed
                        .accessDeniedHandler((req, res, e) -> {
                            res.setStatus(HttpStatus.FORBIDDEN.value());
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            res.getWriter().write("{\"error\":\"Forbidden - access denied\"}");
                        })
                )

                // Authorization rules: decide which endpoints need authentication.
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints (no token required)
                        .requestMatchers(
                                "/api/auth/**",   // login, register, OTP, etc.
                                "/api/public/**", // any public APIs (catalog browsing, etc.)
                                "/api/ci/**",     // CI callbacks or build webhooks
                                "/uploads/**",    // serve uploaded images/files
                                "/ws-chat/**",    // websocket endpoint (handshake might be public)
                                "/error"          // Spring default error endpoint (avoids weird blocking)
                        ).permitAll()

                        // Any other endpoint requires authentication (JWT must be valid).
                        .anyRequest().authenticated()
                )

                // Insert your JWT filter before the built-in UsernamePasswordAuthenticationFilter.
                // This ensures JWT auth happens early and sets the SecurityContext.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /* =========================================================
     * 5) CORS configuration
     * =========================================================
     *
     * This controls which frontends (origins) can call your API from the browser.
     *
     * Current configuration:
     * - allow all origins (wildcard pattern)
     * - allow common methods
     * - allow all headers
     * - allow credentials
     *
     * Note (behavior detail):
     * - When allowCredentials=true, browsers are stricter with wildcard "*".
     *   Using addAllowedOriginPattern("*") is the correct approach vs addAllowedOrigin("*").
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Allow all origins (dev-friendly). In production, restrict to real domains.
        config.addAllowedOriginPattern("*");

        // Allowed HTTP methods for cross-origin calls
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Allowed headers for cross-origin calls (Authorization, Content-Type, etc.)
        config.setAllowedHeaders(List.of("*"));

        // Allows sending Authorization header (and cookies if you ever use them)
        config.setAllowCredentials(true);

        // Apply CORS config to all endpoints
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }
}
