package com.build4all.authentication.web;

import com.build4all.user.domain.Users;
import com.build4all.security.JwtUtil;
import com.build4all.user.service.UserService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class GoogleAuthController {

    private static final String CLIENT_ID = "851915342014-j24igdgk6pvfqh4hu6pbs65jtp6a1r0k.apps.googleusercontent.com";

    @Autowired private UserService userService;
    @Autowired private JwtUtil jwtUtil;

    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@RequestBody Map<String, Object> request) {
        String idTokenString = (String) request.get("idToken");

        // 👇 tenant: ownerProjectLinkId بدل adminId/projectId
        Long ownerProjectLinkId = toLongOrNull(request.get("ownerProjectLinkId"));
        if (ownerProjectLinkId == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "message", "ownerProjectLinkId is required"
            ));
        }

        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    JacksonFactory.getDefaultInstance()
            ).setAudience(Collections.singletonList(CLIENT_ID)).build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid ID token.");
            }

            Payload payload = idToken.getPayload();
            String email      = payload.getEmail();
            String fullName   = (String) payload.get("name");
            String pictureUrl = (String) payload.get("picture");
            String googleId   = payload.getSubject();

            AtomicBoolean wasInactive = new AtomicBoolean(false);
            AtomicBoolean isNewUser   = new AtomicBoolean(false);

            
            Users user = userService.handleGoogleUser(
                email, fullName, pictureUrl, googleId, wasInactive, isNewUser, ownerProjectLinkId
            );

            String currentStatus = user.getStatus() != null ? user.getStatus().getName() : null;
            if ("DELETED".equalsIgnoreCase(currentStatus)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "This account has been deleted and cannot be accessed."));
            }

            String token = jwtUtil.generateToken(user);

            if ("INACTIVE".equalsIgnoreCase(currentStatus)) {
                return ResponseEntity.ok(Map.of(
                    "wasInactive", true,
                    "token", token,
                    "user", Map.of(
                        "id", user.getId(),
                        "email", user.getEmail(),
                        "firstName", user.getFirstName(),
                        "lastName", user.getLastName(),
                        "username", user.getUsername(),
                        "profilePictureUrl", user.getProfilePictureUrl()
                    )
                ));
            }

            user.setLastLogin(LocalDateTime.now());
            userService.save(user);

            return ResponseEntity.ok(Map.of(
                "token", token,
                "wasInactive", false,
                "isNewUser", isNewUser.get(),
                "user", Map.of(
                    "id", user.getId(),
                    "firstName", user.getFirstName(),
                    "lastName", user.getLastName(),
                    "email", user.getEmail(),
                    "profileImageUrl", user.getProfilePictureUrl(),
                    "username", user.getUsername(),
                    "status", user.getStatus() != null ? user.getStatus().getName() : null,
                    "lastLogin", user.getLastLogin(),
                    "publicProfile", user.isPublicProfile(),
                    "googleId", user.getGoogleId()
                )
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Google login failed.");
        }
    }

    private Long toLongOrNull(Object v) {
        if (v == null) return null;
        try { return Long.valueOf(v.toString()); } catch (Exception e) { return null; }
    }
}
