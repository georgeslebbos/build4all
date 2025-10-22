package com.build4all.authentication.web;

import com.build4all.user.domain.Users;
import com.build4all.security.JwtUtil;
import com.build4all.authentication.service.FacebookAuthService;
import com.build4all.user.service.UserService;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class FacebookAuthController {

    @Autowired private FacebookAuthService facebookAuthService;
    @Autowired private UserService userService;
    @Autowired private JwtUtil jwtUtil;

    @PostMapping("/facebook")
    public ResponseEntity<?> loginWithFacebook(@RequestBody Map<String, Object> request) {
        String accessToken = (String) request.get("access_token");
        if (accessToken == null || !facebookAuthService.verifyToken(accessToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Facebook token.");
        }

        // REQUIRED: tenant scope by link id
        Long ownerProjectLinkId = toLongOrNull(request.get("ownerProjectLinkId"));
        if (ownerProjectLinkId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "ownerProjectLinkId is required"));
        }

        Map<String, Object> fbUser = facebookAuthService.getUserData(accessToken);
        String facebookId = (String) fbUser.get("id");
        String name = (String) fbUser.get("name");
        String email = (String) fbUser.get("email");

        String picture = null;
        try {
            Object picObj = fbUser.get("picture");
            if (picObj instanceof Map<?, ?> p1) {
                Object dataObj = p1.get("data");
                if (dataObj instanceof Map<?, ?> p2) {
                    Object url = p2.get("url");
                    picture = url != null ? url.toString() : null;
                }
            }
        } catch (Exception ignore) {}

        String[] parts = name != null ? name.split(" ", 2) : new String[]{"", ""};
        String firstName = parts[0];
        String lastName  = parts.length > 1 ? parts[1] : "";

        AtomicBoolean wasInactive = new AtomicBoolean(false);
        AtomicBoolean isNewUser   = new AtomicBoolean(false);

        Users user = userService.handleFacebookUser(
            email, facebookId, firstName, lastName, picture,
            wasInactive, isNewUser, ownerProjectLinkId
        );

        String token = jwtUtil.generateToken(user);

        return ResponseEntity.ok(Map.of(
            "token", token,
            "wasInactive", wasInactive.get(),
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
                "facebookId", user.getFacebookId()
            )
        ));
    }

    private Long toLongOrNull(Object v) {
        if (v == null) return null;
        try { return Long.valueOf(v.toString()); } catch (Exception e) { return null; }
    }
}
