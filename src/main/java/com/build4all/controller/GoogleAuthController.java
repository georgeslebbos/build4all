package com.build4all.controller;

import com.build4all.repositories.*;
import com.build4all.entities.*;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;

import com.build4all.dto.UserDto;
import com.build4all.entities.Users;
import com.build4all.security.JwtUtil;
import com.build4all.services.UserService;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
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

    @Autowired
    private UserService userService;
    
    @Autowired
    private UserStatusRepository userStatusRepository;


    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@RequestBody Map<String, String> request) {
        String idTokenString = request.get("idToken");

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
            String email = payload.getEmail();
            String fullName = (String) payload.get("name");
            String pictureUrl = (String) payload.get("picture");
            String googleId = payload.getSubject(); // ✅ extract Google ID

            AtomicBoolean wasInactive = new AtomicBoolean(false);
            AtomicBoolean isNewUser = new AtomicBoolean(false);
            Users user = userService.handleGoogleUser(email, fullName, pictureUrl, googleId, wasInactive, isNewUser);

            String currentStatus = user.getStatus().getName();
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
                    "status", user.getStatus().getName(),
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


}
