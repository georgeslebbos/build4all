package com.build4all.tutorial.web;

import com.build4all.security.JwtUtil;
import com.build4all.tutorial.dto.SavePlatformTutorialRequest;
import com.build4all.tutorial.service.PlatformTutorialService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/superadmin/tutorial")
public class SuperAdminTutorialController {

    private final PlatformTutorialService service;
    private final JwtUtil jwtUtil;

    public SuperAdminTutorialController(PlatformTutorialService service, JwtUtil jwtUtil) {
        this.service = service;
        this.jwtUtil = jwtUtil;
    }

    private String strip(String auth) {
        return auth == null ? "" : auth.replace("Bearer ", "").trim();
    }

    private boolean isSuperAdmin(String token) {
        String role = jwtUtil.extractRole(token);
        return role != null && "SUPER_ADMIN".equalsIgnoreCase(role);
    }

    @PutMapping("/owner-guide")
    public ResponseEntity<?> saveOwnerGuide(
            @RequestHeader("Authorization") String auth,
            @RequestBody SavePlatformTutorialRequest body
    ) {
        String token = strip(auth);
        if (!isSuperAdmin(token)) {
            return ResponseEntity.status(403).body(Map.of("error", "SUPER_ADMIN required"));
        }

        var saved = service.upsertOwnerGuide(body == null ? null : body.videoUrl);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("code", saved.getCode());
        data.put("videoUrl", saved.getVideoUrl()); // ✅ can be null
        data.put("updatedAt", saved.getUpdatedAt().toString());

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("message", "OK");
        res.put("data", data);

        return ResponseEntity.ok(res);
    }

    @PostMapping(value = "/owner-guide/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadOwnerGuide(
            @RequestHeader("Authorization") String auth,
            @RequestPart("file") MultipartFile file
    ) {
        try {
            String token = strip(auth);
            if (!isSuperAdmin(token)) {
                return ResponseEntity.status(403).body(Map.of("error", "SUPER_ADMIN required"));
            }

            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "file is required"));
            }

            String original = (file.getOriginalFilename() == null)
                    ? ""
                    : file.getOriginalFilename().toLowerCase();

            if (!original.endsWith(".mp4")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Only .mp4 is allowed"));
            }

            Path dir = Paths.get("uploads", "tutorials").toAbsolutePath();
            Files.createDirectories(dir);

            Path target = dir.resolve("owner_guide.mp4");
            file.transferTo(target.toFile());

            String publicPath = "/uploads/tutorials/owner_guide.mp4";

            service.upsertOwnerGuide(publicPath);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("videoUrl", publicPath);

            Map<String, Object> res = new LinkedHashMap<>();
            res.put("message", "OK");
            res.put("data", data);

            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}