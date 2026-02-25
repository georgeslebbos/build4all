package com.build4all.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple in-memory rate limiter (single-instance).
 *
 * Enforces both:
 * - per-IP limit
 * - per-user limit (if Authorization JWT is valid)
 *
 * Returns HTTP 429 with JSON body + Retry-After header.
 *
 * NOTE:
 * - Good quick protection for one app instance.
 * - For multi-instance deployment, move this to Redis/Bucket4j at gateway/app layer.
 */
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private static final long ONE_MINUTE_MS = 60_000L;

    // ---- Tune these ----
    private static final int AUTH_IP_LIMIT_PER_MIN = 20;    // stricter for login/register/reset
    private static final int AUTH_USER_LIMIT_PER_MIN = 40;

    private static final int API_IP_LIMIT_PER_MIN = 180;    // general endpoints
    private static final int API_USER_LIMIT_PER_MIN = 300;

    // Memory safety cleanup
    private static final int CLEANUP_EVERY_N_REQUESTS = 500;
    private static final long STALE_KEY_AFTER_MS = 5 * ONE_MINUTE_MS;
    private static final int MAX_TRACKED_KEYS_BEFORE_FORCED_CLEANUP = 100_000;

    private final JwtUtil jwtUtil;
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final AtomicLong requestCounter = new AtomicLong(0);

    public ApiRateLimitFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) return true;

        // Skip non-API paths (uploads, websocket, static, etc.)
        if (!path.startsWith("/api/")) return true;

        // Skip preflight
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        final long now = System.currentTimeMillis();
        maybeCleanup(now);

        String path = safe(request.getRequestURI());
        boolean authPath = path.startsWith("/api/auth/")
                || path.startsWith("/api/users/reset-password")
                || path.startsWith("/api/users/verify-reset-code")
                || path.startsWith("/api/users/update-password");

        String ip = resolveClientIp(request);
        String userKey = resolveUserKey(request); // may be null

        Limit ipLimit = authPath
                ? new Limit(AUTH_IP_LIMIT_PER_MIN, ONE_MINUTE_MS)
                : new Limit(API_IP_LIMIT_PER_MIN, ONE_MINUTE_MS);

        Limit userLimit = authPath
                ? new Limit(AUTH_USER_LIMIT_PER_MIN, ONE_MINUTE_MS)
                : new Limit(API_USER_LIMIT_PER_MIN, ONE_MINUTE_MS);

        // Bucket namespaces include route type so auth traffic doesn't consume general quota
        String routeGroup = authPath ? "AUTH" : "API";

        Decision ipDecision = consume("IP|" + routeGroup + "|" + ip, ipLimit, now);
        if (!ipDecision.allowed()) {
            write429(response, "ip", ipDecision, ipLimit);
            return;
        }

        Decision effectiveDecision = ipDecision;
        Limit effectiveLimit = ipLimit;

        if (userKey != null) {
            Decision userDecision = consume("USER|" + routeGroup + "|" + userKey, userLimit, now);
            if (!userDecision.allowed()) {
                write429(response, "user", userDecision, userLimit);
                return;
            }

            // Expose the tighter remaining quota in headers
            if (userDecision.remaining() < ipDecision.remaining()) {
                effectiveDecision = userDecision;
                effectiveLimit = userLimit;
            }
        }

        // Helpful headers (optional but nice for clients/debugging)
        response.setHeader("X-RateLimit-Limit", String.valueOf(effectiveLimit.maxRequests()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(effectiveDecision.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(effectiveDecision.resetEpochSeconds()));

        filterChain.doFilter(request, response);
    }

    private Decision consume(String key, Limit limit, long now) {
        Counter c = counters.computeIfAbsent(key, k -> new Counter(now));

        synchronized (c) {
            // Reset fixed window if expired
            if ((now - c.windowStartMs) >= limit.windowMs()) {
                c.windowStartMs = now;
                c.count = 0;
            }

            c.lastSeenMs = now;
            c.count++;

            boolean allowed = c.count <= limit.maxRequests();
            int remaining = Math.max(0, limit.maxRequests() - Math.min(c.count, limit.maxRequests()));
            long resetAtMs = c.windowStartMs + limit.windowMs();
            long retryAfterSec = Math.max(1, (resetAtMs - now + 999) / 1000);

            return new Decision(
                    allowed,
                    remaining,
                    retryAfterSec,
                    resetAtMs / 1000
            );
        }
    }

    private void write429(HttpServletResponse response, String scope, Decision decision, Limit limit) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit.maxRequests()));
        response.setHeader("X-RateLimit-Remaining", "0");
        response.setHeader("X-RateLimit-Reset", String.valueOf(decision.resetEpochSeconds()));

        String body = """
                {
                  "error":"Too many requests",
                  "code":"RATE_LIMITED",
                  "scope":"%s",
                  "message":"Rate limit exceeded. Please retry later.",
                  "retryAfterSeconds":%d,
                  "timestamp":"%s"
                }
                """.formatted(scope, decision.retryAfterSeconds(), Instant.now().toString());

        response.getWriter().write(body);
    }

    private String resolveUserKey(HttpServletRequest request) {
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth == null || auth.isBlank()) return null;

        String token = auth;
        if (token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }

        try {
            if (!jwtUtil.validateToken(token)) return null;

            String role = jwtUtil.extractRole(token);
            if (role == null || role.isBlank()) role = "UNKNOWN";

            Long id = null;
            try {
                id = jwtUtil.extractId(token);
            } catch (Exception ignored) {
                // some token types may not have "id"
            }

            return (id != null) ? role.toUpperCase() + ":" + id : role.toUpperCase();
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        // If app is behind reverse proxy/load balancer, configure forwarded headers in Spring too.
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // XFF can be "client, proxy1, proxy2"
            String first = xff.split(",")[0].trim();
            if (!first.isBlank()) return first;
        }

        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) return xri.trim();

        String remote = request.getRemoteAddr();
        return (remote == null || remote.isBlank()) ? "unknown" : remote;
    }

    private void maybeCleanup(long now) {
        long n = requestCounter.incrementAndGet();

        boolean periodic = (n % CLEANUP_EVERY_N_REQUESTS == 0);
        boolean forced = counters.size() > MAX_TRACKED_KEYS_BEFORE_FORCED_CLEANUP;

        if (!periodic && !forced) return;

        counters.entrySet().removeIf(e -> (now - e.getValue().lastSeenMs) > STALE_KEY_AFTER_MS);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private record Limit(int maxRequests, long windowMs) {}

    private record Decision(boolean allowed, int remaining, long retryAfterSeconds, long resetEpochSeconds) {}

    private static final class Counter {
        volatile long windowStartMs;
        volatile long lastSeenMs;
        int count;

        Counter(long now) {
            this.windowStartMs = now;
            this.lastSeenMs = now;
            this.count = 0;
        }
    }
}