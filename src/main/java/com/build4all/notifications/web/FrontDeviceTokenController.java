package com.build4all.notifications.web;

import com.build4all.notifications.domain.NotificationActorType;
import com.build4all.notifications.dto.FrontDeviceTokenRequest;
import com.build4all.notifications.service.FrontDeviceTokenService;
import com.build4all.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/front")
public class FrontDeviceTokenController {

    private final FrontDeviceTokenService frontDeviceTokenService;
    private final JwtUtil jwtUtil;

    public FrontDeviceTokenController(FrontDeviceTokenService frontDeviceTokenService,
                                      JwtUtil jwtUtil) {
        this.frontDeviceTokenService = frontDeviceTokenService;
        this.jwtUtil = jwtUtil;
    }

    private String extractBearerToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }
        return authHeader.substring(7);
    }

    @PutMapping("/device-token")
    public ResponseEntity<?> upsertFrontDeviceToken(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody FrontDeviceTokenRequest request
    ) {
        try {
            String token = extractBearerToken(authHeader);

            NotificationActorType actorType;
            Long actorId;
            Long tokenOwnerProjectLinkId;

            if (jwtUtil.isUserToken(token)) {
                actorType = NotificationActorType.USER;
                actorId = jwtUtil.extractId(token);
                tokenOwnerProjectLinkId = jwtUtil.extractOwnerProjectIdForUser(token);
            } else if (jwtUtil.isOwnerToken(token)) {
                actorType = NotificationActorType.OWNER;
                actorId = jwtUtil.extractId(token);
                tokenOwnerProjectLinkId = jwtUtil.requireOwnerProjectId(token);
            } else {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Only USER or OWNER tokens can register FRONT device tokens"));
            }

            if (request.getOwnerProjectLinkId() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "ownerProjectLinkId is required"));
            }

            if (!request.getOwnerProjectLinkId().equals(tokenOwnerProjectLinkId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "ownerProjectLinkId does not match token tenant scope"));
            }

            frontDeviceTokenService.upsertFrontToken(
                    actorId,
                    actorType,
                    request.getOwnerProjectLinkId(),
                    request
            );

            return ResponseEntity.ok(Map.of(
                    "message", "Front device token registered successfully"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to register front device token"));
        }
    }
}