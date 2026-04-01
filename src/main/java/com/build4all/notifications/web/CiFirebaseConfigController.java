package com.build4all.notifications.web;

import com.build4all.notifications.domain.AppFirebaseConfig;
import com.build4all.notifications.domain.FirebaseProvisioningStatus;
import com.build4all.notifications.dto.CiFirebaseConfigResponse;
import com.build4all.notifications.repository.AppFirebaseConfigRepository;
import com.build4all.notifications.service.FirebaseConfigStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ci/firebase-config")
public class CiFirebaseConfigController {

    private final AppFirebaseConfigRepository appFirebaseConfigRepository;
    private final FirebaseConfigStorageService storageService;

    @Value("${app.ci.auth-token}")
    private String ciAuthToken;

    public CiFirebaseConfigController(AppFirebaseConfigRepository appFirebaseConfigRepository,
                                      FirebaseConfigStorageService storageService) {
        this.appFirebaseConfigRepository = appFirebaseConfigRepository;
        this.storageService = storageService;
    }

    @GetMapping("/android")
    public ResponseEntity<?> downloadAndroidConfig(@RequestParam Long linkId,
                                                   @RequestHeader(value = "X-Auth-Token", required = false) String authToken) {
        try {
            validateAuth(authToken);

            AppFirebaseConfig config = loadReadyConfig(linkId);

            if (config.getAndroidConfigPath() == null || config.getAndroidConfigPath().isBlank()) {
                return badRequest("Android Firebase config path is missing for linkId=" + linkId);
            }

            byte[] bytes = storageService.readAndroidConfig(config.getAndroidConfigPath());
            String base64 = Base64.getEncoder().encodeToString(bytes);

            return ResponseEntity.ok(
                    new CiFirebaseConfigResponse("google-services.json", base64)
            );
        } catch (Exception e) {
            return badRequest(e.getMessage());
        }
    }

    @GetMapping("/ios")
    public ResponseEntity<?> downloadIosConfig(@RequestParam Long linkId,
                                               @RequestHeader(value = "X-Auth-Token", required = false) String authToken) {
        try {
            validateAuth(authToken);

            AppFirebaseConfig config = loadReadyConfig(linkId);

            if (config.getIosConfigPath() == null || config.getIosConfigPath().isBlank()) {
                return badRequest("iOS Firebase config path is missing for linkId=" + linkId);
            }

            byte[] bytes = storageService.readIosConfig(config.getIosConfigPath());
            String base64 = Base64.getEncoder().encodeToString(bytes);

            return ResponseEntity.ok(
                    new CiFirebaseConfigResponse("GoogleService-Info.plist", base64)
            );
        } catch (Exception e) {
            return badRequest(e.getMessage());
        }
    }

    private void validateAuth(String authToken) {
        if (authToken == null || authToken.isBlank()) {
            throw new RuntimeException("Missing X-Auth-Token header");
        }

        if (!authToken.equals(ciAuthToken)) {
            throw new RuntimeException("Invalid CI auth token");
        }
    }

    private AppFirebaseConfig loadReadyConfig(Long linkId) {
        AppFirebaseConfig config = appFirebaseConfigRepository
                .findByOwnerProjectLinkIdAndIsActiveTrue(linkId)
                .orElseThrow(() -> new RuntimeException(
                        "No active AppFirebaseConfig found for linkId=" + linkId
                ));

        if (config.getProvisioningStatus() != FirebaseProvisioningStatus.READY) {
            throw new RuntimeException(
                    "Firebase config is not READY for linkId=" + linkId + ", current status=" + config.getProvisioningStatus()
            );
        }

        return config;
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}