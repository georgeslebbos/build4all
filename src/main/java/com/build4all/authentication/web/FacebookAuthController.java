package com.build4all.authentication.web;

import com.build4all.user.domain.Users;
import com.build4all.security.JwtUtil;
import com.build4all.authentication.service.FacebookAuthService;
import com.build4all.user.service.UserService;
import com.build4all.user.repository.UserStatusRepository;

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
    @Autowired private UserStatusRepository userStatusRepository;

    @PostMapping("/facebook")
    public ResponseEntity<?> loginWithFacebook(@RequestBody Map<String, String> request) {
        String accessToken = request.get("access_token");

        if (!facebookAuthService.verifyToken(accessToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Facebook token.");
        }

        Map<String, Object> fbUser = facebookAuthService.getUserData(accessToken);
        String facebookId = (String) fbUser.get("id");
        String name = (String) fbUser.get("name");
        String email = (String) fbUser.get("email");
        String picture = ((Map)((Map)fbUser.get("picture")).get("data")).get("url").toString();

        String[] nameParts = name != null ? name.split(" ", 2) : new String[]{"", ""};
        String firstName = nameParts[0];
        String lastName = nameParts.length > 1 ? nameParts[1] : "";

        AtomicBoolean wasInactive = new AtomicBoolean(false);
        AtomicBoolean isNewUser = new AtomicBoolean(false);

        Users user = userService.handleFacebookUser(email, facebookId, firstName, lastName, picture, wasInactive, isNewUser);

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
                "status", user.getStatus().getName(),
                "lastLogin", user.getLastLogin(),
                "publicProfile", user.isPublicProfile(),
                "facebookId", user.getFacebookId()
            )
        ));
    }
}
