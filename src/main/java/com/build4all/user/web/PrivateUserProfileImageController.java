package com.build4all.user.web;

import com.build4all.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.net.MalformedURLException;
import java.nio.file.*;
import java.time.Duration;

@RestController
public class PrivateUserProfileImageController {

    private final JwtUtil jwtUtil;

    public PrivateUserProfileImageController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    private static final Path USER_PROFILE_DIR =
            Paths.get("uploadsPrivate", "users", "profiles").toAbsolutePath().normalize();

    @GetMapping("/private-users/profiles/{filename:.+}")
    public ResponseEntity<Resource> getProfileImage(
            @PathVariable String filename,
            HttpServletRequest request
    ) throws Exception {

        // ✅ require valid JWT
        String token = jwtUtil.extractTokenFromRequest(request);
        jwtUtil.extractRole(token);

        // ✅ prevent path traversal
        String safeName = Paths.get(filename).getFileName().toString();
        Path filePath = USER_PROFILE_DIR.resolve(safeName).normalize();

        if (!filePath.startsWith(USER_PROFILE_DIR)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource;
        try {
            resource = new UrlResource(filePath.toUri());
        } catch (MalformedURLException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        String contentType = Files.probeContentType(filePath);
        if (contentType == null) contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                // ✅ PRIVATE caching 
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(resource);
    }
}