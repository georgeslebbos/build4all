package com.build4all.notifications.service;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.notifications.config.FirebaseEnvProperties;
import com.build4all.notifications.domain.AppFirebaseConfig;
import com.build4all.notifications.domain.FirebaseProvisioningStatus;
import com.build4all.notifications.repository.AppFirebaseConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

@Service
public class FirebaseProvisioningService {

    private final AdminUserProjectRepository adminUserProjectRepository;
    private final AppFirebaseConfigRepository appFirebaseConfigRepository;
    private final FirebaseEnvProperties firebaseEnvProperties;
    private final FirebaseAppRegistry firebaseAppRegistry;
    private final FirebaseManagementClient firebaseManagementClient;
    private final FirebaseConfigFetcherService firebaseConfigFetcherService;
    private final FirebaseConfigStorageService firebaseConfigStorageService;

    public FirebaseProvisioningService(AdminUserProjectRepository adminUserProjectRepository,
                                       AppFirebaseConfigRepository appFirebaseConfigRepository,
                                       FirebaseEnvProperties firebaseEnvProperties,
                                       FirebaseAppRegistry firebaseAppRegistry,
                                       FirebaseManagementClient firebaseManagementClient,
                                       FirebaseConfigFetcherService firebaseConfigFetcherService,
                                       FirebaseConfigStorageService firebaseConfigStorageService) {
        this.adminUserProjectRepository = adminUserProjectRepository;
        this.appFirebaseConfigRepository = appFirebaseConfigRepository;
        this.firebaseEnvProperties = firebaseEnvProperties;
        this.firebaseAppRegistry = firebaseAppRegistry;
        this.firebaseManagementClient = firebaseManagementClient;
        this.firebaseConfigFetcherService = firebaseConfigFetcherService;
        this.firebaseConfigStorageService = firebaseConfigStorageService;
    }

    @Transactional
    public AppFirebaseConfig ensureConfigRecord(Long ownerProjectLinkId) {
        if (ownerProjectLinkId == null) {
            throw new IllegalArgumentException("ownerProjectLinkId is required");
        }

        AdminUserProject link = adminUserProjectRepository.findById(ownerProjectLinkId)
                .orElseThrow(() -> new RuntimeException(
                        "AdminUserProject not found for id=" + ownerProjectLinkId
                ));

        if (link.getAndroidPackageName() == null || link.getAndroidPackageName().isBlank()) {
            link.ensureAndroidPackageName();
        }

        if (link.getIosBundleId() == null || link.getIosBundleId().isBlank()) {
            link.ensureIosBundleId();
        }

        String env = normalizeEnv(link.getEnvSuffix());
        FirebaseEnvProperties.EnvConfig envConfig = resolveEnvConfig(env);

        validateEnvConfig(env, envConfig);

        AppFirebaseConfig config = appFirebaseConfigRepository
                .findByOwnerProjectLinkIdAndIsActiveTrue(ownerProjectLinkId)
                .orElseGet(AppFirebaseConfig::new);

        config.setOwnerProjectLinkId(ownerProjectLinkId);
        config.setFirebaseProjectId(envConfig.getProjectId());
        config.setFirebaseProjectName(envConfig.getProjectName());
        config.setServiceAccountSecretRef(envConfig.getServiceAccountSecretRef());

        config.setAndroidPackageName(link.getAndroidPackageName());
        config.setIosBundleId(link.getIosBundleId());

        if (config.getProvisioningStatus() == null) {
            config.setProvisioningStatus(FirebaseProvisioningStatus.PENDING);
        }

        config.setLastError(null);
        config.setLastSyncedAt(LocalDateTime.now());
        config.setActive(true);

        AppFirebaseConfig saved = appFirebaseConfigRepository.save(config);

        // very important:
        // if projectId / credentials changed later, clear cached FirebaseApp
        firebaseAppRegistry.evict(ownerProjectLinkId);

        return saved;
    }

    @Transactional
    public AppFirebaseConfig ensureFirebaseAppsExist(Long ownerProjectLinkId) {
        AppFirebaseConfig config = ensureConfigRecord(ownerProjectLinkId);

        String projectId = config.getFirebaseProjectId();
        String secretRef = config.getServiceAccountSecretRef();

        if (projectId == null || projectId.isBlank()) {
            throw new IllegalStateException("Firebase projectId is missing for linkId=" + ownerProjectLinkId);
        }

        if (secretRef == null || secretRef.isBlank()) {
            throw new IllegalStateException("Firebase service account ref is missing for linkId=" + ownerProjectLinkId);
        }

        String androidAppId = config.getAndroidFirebaseAppId();
        if (androidAppId == null || androidAppId.isBlank()) {
            androidAppId = firebaseManagementClient.findAndroidAppIdByPackage(
                    projectId,
                    config.getAndroidPackageName(),
                    secretRef
            );

            if (androidAppId == null || androidAppId.isBlank()) {
                String operationName = firebaseManagementClient.startCreateAndroidApp(
                        projectId,
                        config.getAndroidPackageName(),
                        "Android " + config.getAndroidPackageName(),
                        secretRef
                );

                androidAppId = waitForCreatedAppId(operationName, secretRef);
            }

            config.setAndroidFirebaseAppId(androidAppId);
        }

        String iosAppId = config.getIosFirebaseAppId();
        if (iosAppId == null || iosAppId.isBlank()) {
            iosAppId = firebaseManagementClient.findIosAppIdByBundle(
                    projectId,
                    config.getIosBundleId(),
                    secretRef
            );

            if (iosAppId == null || iosAppId.isBlank()) {
                String operationName = firebaseManagementClient.startCreateIosApp(
                        projectId,
                        config.getIosBundleId(),
                        "iOS " + config.getIosBundleId(),
                        secretRef
                );

                iosAppId = waitForCreatedAppId(operationName, secretRef);
            }

            config.setIosFirebaseAppId(iosAppId);
        }

        config.setLastError(null);
        config.setLastSyncedAt(LocalDateTime.now());

        AppFirebaseConfig saved = appFirebaseConfigRepository.save(config);
        firebaseAppRegistry.evict(ownerProjectLinkId);
        return saved;
    }
    
    @Transactional
    public AppFirebaseConfig ensureFirebaseProvisioned(Long ownerProjectLinkId) {
        AppFirebaseConfig config = ensureFirebaseAppsExist(ownerProjectLinkId);

        String secretRef = config.getServiceAccountSecretRef();

        if (config.getAndroidFirebaseAppId() == null || config.getAndroidFirebaseAppId().isBlank()) {
            throw new IllegalStateException("Android Firebase appId is missing for linkId=" + ownerProjectLinkId);
        }

        if (config.getIosFirebaseAppId() == null || config.getIosFirebaseAppId().isBlank()) {
            throw new IllegalStateException("iOS Firebase appId is missing for linkId=" + ownerProjectLinkId);
        }

        try {
            String androidBody = firebaseConfigFetcherService.fetchAndroidConfig(
                    config.getAndroidFirebaseAppId(),
                    secretRef
            );

            String iosBody = firebaseConfigFetcherService.fetchIosConfig(
                    config.getIosFirebaseAppId(),
                    secretRef
            );

            String androidBase64 = extractConfigFileContents(androidBody, "Android");
            String iosBase64 = extractConfigFileContents(iosBody, "iOS");

            String androidPath = firebaseConfigStorageService.saveAndroidConfigFromBase64(
                    ownerProjectLinkId,
                    androidBase64
            );

            String iosPath = firebaseConfigStorageService.saveIosConfigFromBase64(
                    ownerProjectLinkId,
                    iosBase64
            );

            config.setAndroidConfigPath(androidPath);
            config.setIosConfigPath(iosPath);
            config.setProvisioningStatus(FirebaseProvisioningStatus.READY);
            config.setLastError(null);
            config.setLastSyncedAt(LocalDateTime.now());

            AppFirebaseConfig saved = appFirebaseConfigRepository.save(config);
            firebaseAppRegistry.evict(ownerProjectLinkId);
            return saved;

        } catch (Exception e) {
            config.setProvisioningStatus(FirebaseProvisioningStatus.FAILED);
            config.setLastError(e.getMessage());
            config.setLastSyncedAt(LocalDateTime.now());
            appFirebaseConfigRepository.save(config);

            throw new IllegalStateException(
                    "Failed to fully provision Firebase config for linkId=" + ownerProjectLinkId + ": " + e.getMessage(),
                    e
            );
        }
    }
    
    private String extractConfigFileContents(String body, String platform) {
        try {
            JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
            String base64 = root.path("configFileContents").asText(null);

            if (base64 == null || base64.isBlank()) {
                throw new IllegalStateException(platform + " configFileContents is missing");
            }

            return base64;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse " + platform + " Firebase config response: " + e.getMessage(), e);
        }
    }
    
    private String waitForCreatedAppId(String operationName, String serviceAccountSecretRef) {
        for (int i = 0; i < 20; i++) {
            JsonNode op = firebaseManagementClient.getOperation(operationName, serviceAccountSecretRef);

            boolean done = op.path("done").asBoolean(false);
            if (!done) {
                sleepSilently(1500);
                continue;
            }

            JsonNode error = op.path("error");
            if (!error.isMissingNode() && !error.isNull() && error.size() > 0) {
                throw new IllegalStateException("Firebase operation failed: " + error.toString());
            }

            JsonNode response = op.path("response");

            String appId = response.path("appId").asText(null);
            if (appId != null && !appId.isBlank()) {
                return appId;
            }

            appId = response.path("result").path("appId").asText(null);
            if (appId != null && !appId.isBlank()) {
                return appId;
            }

            JsonNode nameNode = response.path("name");
            if (!nameNode.isMissingNode() && !nameNode.isNull()) {
                String fullName = nameNode.asText();
                if (fullName != null && fullName.contains("/")) {
                    return fullName.substring(fullName.lastIndexOf('/') + 1);
                }
            }

            throw new IllegalStateException("Firebase operation completed but appId was not found in response");
        }

        throw new IllegalStateException("Timed out waiting for Firebase app creation operation");
    }

    private void sleepSilently(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Firebase operation", e);
        }
    }
    
    @Transactional
    public AppFirebaseConfig markReady(Long ownerProjectLinkId,
                                       String androidFirebaseAppId,
                                       String iosFirebaseAppId,
                                       String androidConfigPath,
                                       String iosConfigPath) {
        AppFirebaseConfig config = appFirebaseConfigRepository
                .findByOwnerProjectLinkIdAndIsActiveTrue(ownerProjectLinkId)
                .orElseThrow(() -> new RuntimeException(
                        "No active AppFirebaseConfig found for ownerProjectLinkId=" + ownerProjectLinkId
                ));

        config.setAndroidFirebaseAppId(androidFirebaseAppId);
        config.setIosFirebaseAppId(iosFirebaseAppId);
        config.setAndroidConfigPath(androidConfigPath);
        config.setIosConfigPath(iosConfigPath);
        config.setProvisioningStatus(FirebaseProvisioningStatus.READY);
        config.setLastError(null);
        config.setLastSyncedAt(LocalDateTime.now());

        AppFirebaseConfig saved = appFirebaseConfigRepository.save(config);
        firebaseAppRegistry.evict(ownerProjectLinkId);
        return saved;
    }

    @Transactional
    public AppFirebaseConfig markFailed(Long ownerProjectLinkId, String errorMessage) {
        AppFirebaseConfig config = appFirebaseConfigRepository
                .findByOwnerProjectLinkIdAndIsActiveTrue(ownerProjectLinkId)
                .orElseThrow(() -> new RuntimeException(
                        "No active AppFirebaseConfig found for ownerProjectLinkId=" + ownerProjectLinkId
                ));

        config.setProvisioningStatus(FirebaseProvisioningStatus.FAILED);
        config.setLastError(errorMessage);
        config.setLastSyncedAt(LocalDateTime.now());

        return appFirebaseConfigRepository.save(config);
    }
    
    @Transactional
    public AppFirebaseConfig attachManualConfigPaths(Long ownerProjectLinkId,
                                                     String androidConfigPath,
                                                     String iosConfigPath) {
        AppFirebaseConfig config = appFirebaseConfigRepository
                .findByOwnerProjectLinkIdAndIsActiveTrue(ownerProjectLinkId)
                .orElseThrow(() -> new RuntimeException(
                        "No active AppFirebaseConfig found for ownerProjectLinkId=" + ownerProjectLinkId
                ));

        if (androidConfigPath != null && !androidConfigPath.isBlank()) {
            config.setAndroidConfigPath(androidConfigPath);
        }

        if (iosConfigPath != null && !iosConfigPath.isBlank()) {
            config.setIosConfigPath(iosConfigPath);
        }

        boolean hasAndroid = config.getAndroidConfigPath() != null && !config.getAndroidConfigPath().isBlank();
        boolean hasIos = config.getIosConfigPath() != null && !config.getIosConfigPath().isBlank();

        if (hasAndroid && hasIos) {
            config.setProvisioningStatus(FirebaseProvisioningStatus.READY);
            config.setLastError(null);
        } else {
            config.setProvisioningStatus(FirebaseProvisioningStatus.PENDING);
        }

        config.setLastSyncedAt(LocalDateTime.now());

        AppFirebaseConfig saved = appFirebaseConfigRepository.save(config);
        firebaseAppRegistry.evict(ownerProjectLinkId);
        return saved;
    }

    private String normalizeEnv(String envSuffix) {
        if (envSuffix == null || envSuffix.isBlank()) {
            return "test";
        }
        String normalized = envSuffix.trim().toLowerCase();
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private FirebaseEnvProperties.EnvConfig resolveEnvConfig(String env) {
        return switch (env) {
            case "prod" -> firebaseEnvProperties.getProd();
            case "dev" -> firebaseEnvProperties.getDev();
            case "test" -> firebaseEnvProperties.getTest();
            default -> firebaseEnvProperties.getTest();
        };
    }

    private void validateEnvConfig(String env, FirebaseEnvProperties.EnvConfig envConfig) {
        if (envConfig == null) {
            throw new IllegalStateException("No Firebase env config found for env=" + env);
        }
        if (isBlank(envConfig.getProjectId())) {
            throw new IllegalStateException("Missing Firebase projectId for env=" + env);
        }
        if (isBlank(envConfig.getServiceAccountSecretRef())) {
            throw new IllegalStateException("Missing Firebase serviceAccountSecretRef for env=" + env);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}