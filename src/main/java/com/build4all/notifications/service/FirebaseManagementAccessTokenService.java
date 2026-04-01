package com.build4all.notifications.service;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

@Service
public class FirebaseManagementAccessTokenService {

    private static final List<String> SCOPES = List.of(
            "https://www.googleapis.com/auth/cloud-platform"
    );

    private final ResourceLoader resourceLoader;

    public FirebaseManagementAccessTokenService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public String getAccessToken(String serviceAccountSecretRef) {
        if (serviceAccountSecretRef == null || serviceAccountSecretRef.isBlank()) {
            throw new IllegalArgumentException("serviceAccountSecretRef is required");
        }

        try {
            Resource resource = resourceLoader.getResource(serviceAccountSecretRef);

            if (!resource.exists()) {
                throw new IllegalStateException("Service account resource not found: " + serviceAccountSecretRef);
            }

            try (InputStream in = resource.getInputStream()) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(in).createScoped(SCOPES);
                credentials.refreshIfExpired();

                AccessToken token = credentials.getAccessToken();
                if (token == null || token.getTokenValue() == null || token.getTokenValue().isBlank()) {
                    throw new IllegalStateException("Failed to obtain Google access token");
                }

                return token.getTokenValue();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load Google access token: " + e.getMessage(), e);
        }
    }
}