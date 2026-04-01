package com.build4all.notifications.web;

import com.build4all.notifications.domain.AppFirebaseConfig;
import com.build4all.notifications.service.FirebaseConfigStorageService;
import com.build4all.notifications.service.FirebaseProvisioningService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/debug/firebase-config")
public class FirebaseConfigAdminController {

    private final FirebaseConfigStorageService storageService;
    private final FirebaseProvisioningService provisioningService;

    public FirebaseConfigAdminController(FirebaseConfigStorageService storageService,
                                         FirebaseProvisioningService provisioningService) {
        this.storageService = storageService;
        this.provisioningService = provisioningService;
    }

    @PostMapping(
            value = "/android/{ownerProjectLinkId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> uploadAndroidConfig(@PathVariable Long ownerProjectLinkId,
                                                 @RequestParam("file") MultipartFile file) {
        try {
            String savedPath = storageService.saveAndroidConfig(ownerProjectLinkId, file);
            AppFirebaseConfig config = provisioningService.attachManualConfigPaths(
                    ownerProjectLinkId,
                    savedPath,
                    null
            );

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", "Android Firebase config uploaded successfully");
            body.put("ownerProjectLinkId", ownerProjectLinkId);
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

    @PostMapping(
            value = "/ios/{ownerProjectLinkId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> uploadIosConfig(@PathVariable Long ownerProjectLinkId,
                                             @RequestParam("file") MultipartFile file) {
        try {
            String savedPath = storageService.saveIosConfig(ownerProjectLinkId, file);
            AppFirebaseConfig config = provisioningService.attachManualConfigPaths(
                    ownerProjectLinkId,
                    null,
                    savedPath
            );

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", "iOS Firebase config uploaded successfully");
            body.put("ownerProjectLinkId", ownerProjectLinkId);
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