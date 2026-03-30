package com.build4all.notifications.service;

import com.build4all.notifications.domain.AppFirebaseConfig;
import com.build4all.notifications.repository.AppFirebaseConfigRepository;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FirebaseAppRegistry {

    private final AppFirebaseConfigRepository appFirebaseConfigRepository;
    private final FirebaseCredentialResolver firebaseCredentialResolver;

    /**
     * Cache key = ownerProjectLinkId
     */
    private final Map<Long, FirebaseApp> cache = new ConcurrentHashMap<>();

    public FirebaseAppRegistry(AppFirebaseConfigRepository appFirebaseConfigRepository,
                               FirebaseCredentialResolver firebaseCredentialResolver) {
        this.appFirebaseConfigRepository = appFirebaseConfigRepository;
        this.firebaseCredentialResolver = firebaseCredentialResolver;
    }

    public FirebaseApp getOrCreate(Long ownerProjectLinkId) {
        if (ownerProjectLinkId == null) {
            throw new RuntimeException("ownerProjectLinkId is required");
        }

        return cache.computeIfAbsent(ownerProjectLinkId, this::initializeFirebaseApp);
    }

    private FirebaseApp initializeFirebaseApp(Long ownerProjectLinkId) {
        AppFirebaseConfig config = appFirebaseConfigRepository
                .findByOwnerProjectLinkIdAndIsActiveTrue(ownerProjectLinkId)
                .orElseThrow(() -> new RuntimeException(
                        "No active Firebase config found for ownerProjectLinkId=" + ownerProjectLinkId
                ));

        try (InputStream inputStream = firebaseCredentialResolver
                .openServiceAccountStream(config.getServiceAccountSecretRef())) {

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(inputStream))
                    .setProjectId(config.getFirebaseProjectId())
                    .build();

            String appName = "front-app-" + ownerProjectLinkId;

            // if already exists by name, reuse it
            for (FirebaseApp existing : FirebaseApp.getApps()) {
                if (existing.getName().equals(appName)) {
                    return existing;
                }
            }

            return FirebaseApp.initializeApp(options, appName);

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to initialize FirebaseApp for ownerProjectLinkId=" + ownerProjectLinkId,
                    e
            );
        }
    }

    public void evict(Long ownerProjectLinkId) {
        if (ownerProjectLinkId == null) {
            return;
        }

        FirebaseApp app = cache.remove(ownerProjectLinkId);
        if (app != null) {
            try {
                app.delete();
            } catch (Exception ignored) {
            }
        }
    }
}