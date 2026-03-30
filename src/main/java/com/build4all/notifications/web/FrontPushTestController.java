package com.build4all.notifications.web;

import com.build4all.notifications.service.FrontPushService;
import com.build4all.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/front/push")
public class FrontPushTestController {

    private final FrontPushService frontPushService;
    private final JwtUtil jwtUtil;

    public FrontPushTestController(FrontPushService frontPushService, JwtUtil jwtUtil) {
        this.frontPushService = frontPushService;
        this.jwtUtil = jwtUtil;
    }

    private String extractBearerToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }
        return authHeader.substring(7);
    }

    @PostMapping("/test")
    public ResponseEntity<?> sendTestPush(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> requestBody
    ) {
        try {
            String token = extractBearerToken(authHeader);

            Long tokenOwnerProjectId = jwtUtil.requireOwnerProjectId(token);

            String fcmToken = requestBody.get("fcmToken");
            String title = requestBody.getOrDefault("title", "Build4All Test");
            String body = requestBody.getOrDefault("body", "Backend push test ✅");

            String response = frontPushService.sendPush(
                    tokenOwnerProjectId,
                    fcmToken,
                    title,
                    body,
                    Map.of(
                            "type", "TEST_PUSH",
                            "ownerProjectLinkId", String.valueOf(tokenOwnerProjectId)
                    )
            );

            return ResponseEntity.ok(Map.of(
                    "message", "Test push sent successfully",
                    "firebaseMessageId", response
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to send test push: " + e.getMessage()));
        }
    }
}