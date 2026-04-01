package com.build4all.notifications.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class FirebaseManagementClient {

    private static final String FIREBASE_BASE_URL = "https://firebase.googleapis.com";

    private final FirebaseManagementAccessTokenService accessTokenService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public FirebaseManagementClient(FirebaseManagementAccessTokenService accessTokenService,
                                    ObjectMapper objectMapper) {
        this.accessTokenService = accessTokenService;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(FIREBASE_BASE_URL)
                .build();
    }

    public String findAndroidAppIdByPackage(String firebaseProjectId,
                                            String packageName,
                                            String serviceAccountSecretRef) {
        try {
            String token = accessTokenService.getAccessToken(serviceAccountSecretRef);

            String body = restClient.get()
                    .uri("/v1beta1/projects/{projectId}/androidApps", firebaseProjectId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(body);
            JsonNode apps = root.path("apps");

            if (apps.isArray()) {
                for (JsonNode app : apps) {
                    String currentPackage = app.path("packageName").asText();
                    if (packageName.equals(currentPackage)) {
                        return app.path("appId").asText(null);
                    }
                }
            }

            return null;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to list Android Firebase apps: " + e.getMessage(), e);
        }
    }

    public String findIosAppIdByBundle(String firebaseProjectId,
                                       String bundleId,
                                       String serviceAccountSecretRef) {
        try {
            String token = accessTokenService.getAccessToken(serviceAccountSecretRef);

            String body = restClient.get()
                    .uri("/v1beta1/projects/{projectId}/iosApps", firebaseProjectId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(body);
            JsonNode apps = root.path("apps");

            if (apps.isArray()) {
                for (JsonNode app : apps) {
                    String currentBundle = app.path("bundleId").asText();
                    if (bundleId.equals(currentBundle)) {
                        return app.path("appId").asText(null);
                    }
                }
            }

            return null;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to list iOS Firebase apps: " + e.getMessage(), e);
        }
    }

    public String startCreateAndroidApp(String firebaseProjectId,
                                        String packageName,
                                        String displayName,
                                        String serviceAccountSecretRef) {
        try {
            String token = accessTokenService.getAccessToken(serviceAccountSecretRef);

            JsonNode payload = objectMapper.createObjectNode()
                    .put("packageName", packageName)
                    .put("displayName", displayName);

            String body = restClient.post()
                    .uri("/v1beta1/projects/{projectId}/androidApps", firebaseProjectId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(payload))
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(body);
            return root.path("name").asText();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start Android Firebase app creation: " + e.getMessage(), e);
        }
    }

    public String startCreateIosApp(String firebaseProjectId,
                                    String bundleId,
                                    String displayName,
                                    String serviceAccountSecretRef) {
        try {
            String token = accessTokenService.getAccessToken(serviceAccountSecretRef);

            JsonNode payload = objectMapper.createObjectNode()
                    .put("bundleId", bundleId)
                    .put("displayName", displayName);

            String body = restClient.post()
                    .uri("/v1beta1/projects/{projectId}/iosApps", firebaseProjectId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(payload))
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(body);
            return root.path("name").asText();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start iOS Firebase app creation: " + e.getMessage(), e);
        }
    }

    public JsonNode getOperation(String operationName,
            String serviceAccountSecretRef) {
		try {
		String token = accessTokenService.getAccessToken(serviceAccountSecretRef);
		
		String normalizedOperationName = normalizeOperationName(operationName);
		
		String body = restClient.get()
		.uri("/v1beta1/" + normalizedOperationName)
		.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
		.retrieve()
		.body(String.class);
		
		return objectMapper.readTree(body);
		} catch (Exception e) {
		throw new IllegalStateException("Failed to fetch Firebase operation: " + e.getMessage(), e);
		}
		}
		
		private String normalizeOperationName(String operationName) {
		if (operationName == null || operationName.isBlank()) {
		throw new IllegalArgumentException("operationName is required");
		}
		
		String normalized = operationName.trim();
		
		if (normalized.startsWith("https://")) {
		int idx = normalized.indexOf("/v1beta1/");
		if (idx >= 0) {
		normalized = normalized.substring(idx + "/v1beta1/".length());
		}
		}
		
		if (normalized.startsWith("/")) {
		normalized = normalized.substring(1);
		}
		
		if (normalized.startsWith("v1beta1/")) {
		normalized = normalized.substring("v1beta1/".length());
		}
		
		return normalized;
		}
}