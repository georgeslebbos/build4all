package com.build4all.publish.web;

import com.build4all.publish.dto.UpsertPublisherProfileDto;
import com.build4all.publish.service.StorePublisherProfileService;
import com.build4all.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/superadmin/publisher-profiles")
public class SuperAdminPublisherProfileController {

    private final StorePublisherProfileService service;
    private final JwtUtil jwtUtil;

    public SuperAdminPublisherProfileController(StorePublisherProfileService service, JwtUtil jwtUtil) {
        this.service = service;
        this.jwtUtil = jwtUtil;
    }

    private boolean isSuperAdmin(HttpServletRequest request) {
        String token = jwtUtil.extractTokenFromRequest(request);
        return jwtUtil.isSuperAdmin(token);
    }

    @GetMapping
    public ResponseEntity<?> list(HttpServletRequest request) {
        if (!isSuperAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of("error", "Only SUPER_ADMIN allowed"));
        }
        return ResponseEntity.ok(Map.of("message", "OK", "data", service.listAll()));
    }

    @PostMapping("/upsert")
    public ResponseEntity<?> upsert(HttpServletRequest request, @Valid @RequestBody UpsertPublisherProfileDto dto) {
        if (!isSuperAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of("error", "Only SUPER_ADMIN allowed"));
        }
        return ResponseEntity.ok(Map.of("message", "Saved", "data", service.upsert(dto)));
    }

    // ✅ Seed endpoint (fills required NOT NULL fields)
    @PostMapping("/seed")
    public ResponseEntity<?> seed(HttpServletRequest request, @RequestBody Map<String, String> body) {
        if (!isSuperAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of("error", "Only SUPER_ADMIN allowed"));
        }

        String developerName = body.getOrDefault("developerName", "Build4All");
        String developerEmail = body.getOrDefault("developerEmail", "support@build4all.com");
        String privacyPolicyUrl = body.getOrDefault("privacyPolicyUrl", "https://example.com/privacy");

        service.seedDefaultsIfMissing(developerName, developerEmail, privacyPolicyUrl);

        return ResponseEntity.ok(Map.of("message", "Seeded defaults"));
    }
}
