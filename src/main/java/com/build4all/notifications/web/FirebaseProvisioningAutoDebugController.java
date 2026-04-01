package com.build4all.notifications.web;

import com.build4all.notifications.domain.AppFirebaseConfig;
import com.build4all.notifications.service.FirebaseProvisioningService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/debug/firebase-auto")
public class FirebaseProvisioningAutoDebugController {

    private final FirebaseProvisioningService firebaseProvisioningService;

    public FirebaseProvisioningAutoDebugController(FirebaseProvisioningService firebaseProvisioningService) {
        this.firebaseProvisioningService = firebaseProvisioningService;
    }

    @PostMapping("/provision/{ownerProjectLinkId}")
    public ResponseEntity<?> provision(@PathVariable Long ownerProjectLinkId) {
        try {
            AppFirebaseConfig config = firebaseProvisioningService.ensureFirebaseProvisioned(ownerProjectLinkId);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", "Firebase fully provisioned successfully");
            body.put("ownerProjectLinkId", config.getOwnerProjectLinkId());
            body.put("androidFirebaseAppId", config.getAndroidFirebaseAppId());
            body.put("iosFirebaseAppId", config.getIosFirebaseAppId());
            body.put("androidConfigPath", config.getAndroidConfigPath());
            body.put("iosConfigPath", config.getIosConfigPath());
            body.put("provisioningStatus", config.getProvisioningStatus());

            return ResponseEntity.ok(body);
        } catch (Exception e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(body);
        }
    }
}