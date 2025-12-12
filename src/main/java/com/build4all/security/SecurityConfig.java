package com.build4all.security;

import com.build4all.user.repository.UsersRepository;
import com.build4all.admin.repository.AdminUsersRepository;      // 👈 NEW
import com.build4all.business.repository.BusinessesRepository;  // 👈 NEW

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * - Exposes PasswordEncoder bean
 * - Registers JwtAuthenticationFilter
 * - Configures which endpoints are public / secured
 * - Enables method-level security (@PreAuthorize, etc.)
 * - Configures CORS
 * - Serves /uploads/** from the local filesystem
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig implements WebMvcConfigurer {

    /* =========================================================
     * 1) Static resources mapping (/uploads/** → local folder)
     * ========================================================= */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Folder where you save uploaded images (profile pictures, etc.)
        String uploadPath = Paths.get("uploads").toAbsolutePath().toUri().toString();

        registry
                .addResourceHandler("/uploads/**")
                .addResourceLocations(uploadPath);
    }

    /* =========================================================
     * 2) PasswordEncoder bean
     * ========================================================= */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /* =========================================================
     * 3) JWT Authentication Filter bean
     *
     *    Now we inject:
     *    - UsersRepository (for app users)
     *    - AdminUsersRepository (for SUPER_ADMIN / OWNER / MANAGER)
     *    - BusinessesRepository (for BUSINESS)
     * ========================================================= */
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
     * ========================================================= */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter)
            throws Exception {

        http
                // We use JWT tokens, not session or login form, so CSRF is disabled
                .csrf(csrf -> csrf.disable())

                // Enable CORS with the bean defined below
                .cors(cors -> {})

                // No HTTP session: every request must carry its own JWT
                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints (no token required)
                        .requestMatchers(
                                "/api/auth/**",   // login, register, OTP, etc.
                                "/api/public/**", // any public APIs
                                "/api/ci/**",     // CI callbacks
                                "/uploads/**",    // serve images
                                "/ws-chat/**"     // websocket endpoint
                        ).permitAll()

                        // Everything else must be authenticated (valid JWT)
                        .anyRequest().authenticated()
                )

                // Plug JwtAuthenticationFilter before UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /* =========================================================
     * 5) CORS configuration
     * ========================================================= */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // For now: allow all origins. In prod, use your domains.
        config.addAllowedOriginPattern("*");

        // Allowed HTTP methods
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Allowed headers (Authorization, Content-Type, etc.)
        config.setAllowedHeaders(List.of("*"));

        // Allow credentials (Authorization header)
        config.setAllowCredentials(true);

        // Apply this configuration to all paths
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }
}
