package com.build4all.notifications.service;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class FirebaseConfigFetcherService {

    private final FirebaseManagementAccessTokenService accessTokenService;
    private final RestClient restClient;

    public FirebaseConfigFetcherService(FirebaseManagementAccessTokenService accessTokenService) {
        this.accessTokenService = accessTokenService;
        this.restClient = RestClient.builder()
                .baseUrl("https://firebase.googleapis.com")
                .build();
    }

    public String fetchAndroidConfig(String androidAppId, String serviceAccountSecretRef) {
        try {
            String token = accessTokenService.getAccessToken(serviceAccountSecretRef);

            return restClient.get()
                    .uri("/v1beta1/projects/-/androidApps/{appId}/config", androidAppId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to fetch Android Firebase config: " + e.getMessage(), e);
        }
    }

    public String fetchIosConfig(String iosAppId, String serviceAccountSecretRef) {
        try {
            String token = accessTokenService.getAccessToken(serviceAccountSecretRef);

            return restClient.get()
                    .uri("/v1beta1/projects/-/iosApps/{appId}/config", iosAppId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to fetch iOS Firebase config: " + e.getMessage(), e);
        }
    }
}