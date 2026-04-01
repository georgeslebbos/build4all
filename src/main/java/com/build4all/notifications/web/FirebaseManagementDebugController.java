package com.build4all.notifications.web;

import com.build4all.notifications.domain.AppFirebaseConfig;
import com.build4all.notifications.service.FirebaseProvisioningService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/debug/firebase-management")
public class FirebaseManagementDebugController {

    private final FirebaseProvisioningService firebaseProvisioningService;

    public FirebaseManagementDebugController(FirebaseProvisioningService firebaseProvisioningService) {
        this.firebaseProvisioningService = firebaseProvisioningService;
    }

    @PostMapping("/ensure-apps/{ownerProjectLinkId}")
    public ResponseEntity<?> ensureApps(@PathVariable Long ownerProjectLinkId) {
        try {
            AppFirebaseConfig config = firebaseProvisioningService.ensureFirebaseAppsExist(ownerProjectLinkId);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", "Firebase apps ensured successfully");
            body.put("ownerProjectLinkId", config.getOwnerProjectLinkId());
            body.put("androidPackageName", config.getAndroidPackageName());
            body.put("iosBundleId", config.getIosBundleId());
            body.put("androidFirebaseAppId", config.getAndroidFirebaseAppId());
            body.put("iosFirebaseAppId", config.getIosFirebaseAppId());
            body.put("provisioningStatus", config.getProvisioningStatus());

            return ResponseEntity.ok(body);
        } catch (Exception e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(body);
        }
    }
}