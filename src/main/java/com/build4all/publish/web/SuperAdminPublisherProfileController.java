package com.build4all.publish.web;

import com.build4all.publish.dto.UpsertPublisherProfileDto;
import com.build4all.publish.service.StorePublisherProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/superadmin/publisher-profiles")
public class SuperAdminPublisherProfileController {

    private final StorePublisherProfileService service;

    public SuperAdminPublisherProfileController(StorePublisherProfileService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> list(HttpServletRequest request) {
        return ResponseEntity.ok(Map.of(
                "message", "OK",
                "data", service.listAll()
        ));
    }

    @PostMapping("/upsert")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> upsert(HttpServletRequest request, @Valid @RequestBody UpsertPublisherProfileDto dto) {
        return ResponseEntity.ok(Map.of(
                "message", "Saved",
                "data", service.upsert(dto)
        ));
    }

    @PostMapping("/seed")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> seed(HttpServletRequest request, @RequestBody Map<String, String> body) {

        String developerName = body.getOrDefault("developerName", "Build4All");
        String developerEmail = body.getOrDefault("developerEmail", "support@build4all.com");
        String privacyPolicyUrl = body.getOrDefault("privacyPolicyUrl", "https://example.com/privacy");

        service.seedDefaultsIfMissing(developerName, developerEmail, privacyPolicyUrl);

        return ResponseEntity.ok(Map.of("message", "Seeded defaults"));
    }
}