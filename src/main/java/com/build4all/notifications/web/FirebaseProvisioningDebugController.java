package com.build4all.notifications.web;

import com.build4all.notifications.domain.AppFirebaseConfig;
import com.build4all.notifications.service.FirebaseProvisioningService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/debug/firebase-provisioning")
public class FirebaseProvisioningDebugController {

    private final FirebaseProvisioningService firebaseProvisioningService;

    public FirebaseProvisioningDebugController(FirebaseProvisioningService firebaseProvisioningService) {
        this.firebaseProvisioningService = firebaseProvisioningService;
    }

    @PostMapping("/ensure/{ownerProjectLinkId}")
    public ResponseEntity<?> ensure(@PathVariable Long ownerProjectLinkId) {
        try {
            AppFirebaseConfig config = firebaseProvisioningService.ensureConfigRecord(ownerProjectLinkId);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", "Firebase config record ensured successfully");
            body.put("ownerProjectLinkId", config.getOwnerProjectLinkId());
            body.put("firebaseProjectId", config.getFirebaseProjectId());
            body.put("firebaseProjectName", config.getFirebaseProjectName());
            body.put("androidPackageName", config.getAndroidPackageName());
            body.put("iosBundleId", config.getIosBundleId());
            body.put("provisioningStatus", config.getProvisioningStatus());
            body.put("serviceAccountSecretRef", config.getServiceAccountSecretRef());

            return ResponseEntity.ok(body);
        } catch (Exception e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(body);
        }
    }
}