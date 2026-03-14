package com.build4all.app.internaltesting.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.InvalidKeyException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
@Primary
@ConditionalOnProperty(prefix = "build4all.ios-internal.apple", name = "enabled", havingValue = "true")
public class AppStoreConnectAppleInternalTestingGateway implements AppleInternalTestingGateway {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final AppStoreConnectProperties properties;

    public AppStoreConnectAppleInternalTestingGateway(
            WebClient appStoreConnectWebClient,
            ObjectMapper objectMapper,
            AppStoreConnectProperties properties
    ) {
        this.webClient = appStoreConnectWebClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public AppleInternalTestingGatewayResult process(AppleInternalTestingCommand command) {
        validateProperties();

        String appId = findAppIdByBundleId(command.bundleId());
        String existingUserId = findUserIdByEmail(command.appleEmail());

        if (existingUserId != null) {
            ensureInternalTestingAccess(appId, command);
            return new AppleInternalTestingGatewayResult(
                    AppleInternalTestingGatewayOutcome.EXISTING_USER_ADDED,
                    existingUserId,
                    null,
                    "Existing App Store Connect user found and added to internal testing"
            );
        }

        String existingInvitationId = findPendingInvitationIdByEmail(command.appleEmail());
        if (existingInvitationId != null) {
            return new AppleInternalTestingGatewayResult(
                    AppleInternalTestingGatewayOutcome.INVITATION_SENT,
                    null,
                    existingInvitationId,
                    "Invitation already exists and is still pending"
            );
        }

        String invitationId = inviteUser(command);

        return new AppleInternalTestingGatewayResult(
                AppleInternalTestingGatewayOutcome.INVITATION_SENT,
                null,
                invitationId,
                "Apple invitation sent successfully"
        );
    }

    @Override
    public AppleInternalTestingGatewayResult syncInvitation(
            AppleInternalTestingCommand command,
            String invitationId
    ) {
        validateProperties();

        String appId = findAppIdByBundleId(command.bundleId());
        String userId = findUserIdByEmail(command.appleEmail());

        if (userId == null) {
            return new AppleInternalTestingGatewayResult(
                    AppleInternalTestingGatewayOutcome.STILL_WAITING,
                    null,
                    invitationId,
                    "User still has not accepted the App Store Connect invitation"
            );
        }

        ensureInternalTestingAccess(appId, command);

        return new AppleInternalTestingGatewayResult(
                AppleInternalTestingGatewayOutcome.INVITATION_ACCEPTED_AND_ADDED,
                userId,
                invitationId,
                "Invitation accepted and internal testing access is ready"
        );
    }

    private void ensureInternalTestingAccess(String appId, AppleInternalTestingCommand command) {
        String betaGroupId = findOrCreateInternalBetaGroup(appId);
        String betaTesterId = findOrCreateBetaTester(appId, command);

        ensureBetaTesterInGroup(betaGroupId, betaTesterId);

        String latestBuildId = findLatestBuildId(appId);
        if (latestBuildId != null) {
            ensureBuildInGroup(betaGroupId, latestBuildId);
        }
    }

    private String findAppIdByBundleId(String bundleId) {
        JsonNode root = getJson("/v1/apps?filter[bundleId]=" + url(bundleId) + "&limit=1");
        JsonNode data = root.path("data");
        if (!data.isArray() || data.isEmpty()) {
            throw new IllegalStateException("No App Store Connect app found for bundle id: " + bundleId);
        }
        return data.get(0).path("id").asText(null);
    }

    private String findUserIdByEmail(String email) {
        List<JsonNode> users = getAllPages("/v1/users?limit=200");
        String target = normalize(email);

        for (JsonNode item : users) {
            String currentEmail = normalize(item.path("attributes").path("email").asText(null));
            if (target.equals(currentEmail)) {
                return item.path("id").asText(null);
            }
        }
        return null;
    }

    private String findPendingInvitationIdByEmail(String email) {
        List<JsonNode> invitations = getAllPages("/v1/userInvitations?limit=200");
        String target = normalize(email);

        for (JsonNode item : invitations) {
            String currentEmail = normalize(item.path("attributes").path("email").asText(null));
            if (target.equals(currentEmail)) {
                return item.path("id").asText(null);
            }
        }
        return null;
    }

    private String inviteUser(AppleInternalTestingCommand command) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode data = root.putObject("data");
            data.put("type", "userInvitations");

            ObjectNode attributes = data.putObject("attributes");
            attributes.put("email", command.appleEmail());
            attributes.put("firstName", command.firstName());
            attributes.put("lastName", command.lastName());

            ArrayNode roles = attributes.putArray("roles");
            roles.add(properties.getInvitationRole());

            attributes.put("allAppsVisible", true);
            attributes.put("provisioningAllowed", false);

            String body = objectMapper.writeValueAsString(root);

            JsonNode response = postJson("/v1/userInvitations", body);
            String invitationId = response.path("data").path("id").asText(null);

            if (invitationId == null || invitationId.isBlank()) {
                throw new IllegalStateException("Apple invitation created but no id returned");
            }

            return invitationId;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build Apple invitation payload", ex);
        }
    }

    private String findOrCreateInternalBetaGroup(String appId) {
        JsonNode groupsRoot = getJson("/v1/apps/" + appId + "/betaGroups?limit=200");
        JsonNode groups = groupsRoot.path("data");

        if (groups.isArray()) {
            for (JsonNode item : groups) {
                boolean internal = item.path("attributes").path("isInternalGroup").asBoolean(false);
                String name = item.path("attributes").path("name").asText("");
                if (internal && properties.getInternalGroupName().equalsIgnoreCase(name)) {
                    return item.path("id").asText(null);
                }
            }

            for (JsonNode item : groups) {
                boolean internal = item.path("attributes").path("isInternalGroup").asBoolean(false);
                if (internal) {
                    return item.path("id").asText(null);
                }
            }
        }

        try {
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode data = root.putObject("data");
            data.put("type", "betaGroups");

            ObjectNode attributes = data.putObject("attributes");
            attributes.put("name", properties.getInternalGroupName());
            attributes.put("isInternalGroup", true);

            ObjectNode relationships = data.putObject("relationships");
            ObjectNode app = relationships.putObject("app");
            ObjectNode appData = app.putObject("data");
            appData.put("type", "apps");
            appData.put("id", appId);

            String body = objectMapper.writeValueAsString(root);

            JsonNode created = postJson("/v1/betaGroups", body);
            String id = created.path("data").path("id").asText(null);

            if (id == null || id.isBlank()) {
                throw new IllegalStateException("Internal beta group was created but no id returned");
            }

            return id;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build internal beta group payload", ex);
        }
    }

    private String findOrCreateBetaTester(String appId, AppleInternalTestingCommand command) {
        List<JsonNode> testers = getAllPages("/v1/betaTesters?limit=200");
        String target = normalize(command.appleEmail());

        for (JsonNode item : testers) {
            String currentEmail = normalize(item.path("attributes").path("email").asText(null));
            if (target.equals(currentEmail)) {
                return item.path("id").asText(null);
            }
        }

        try {
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode data = root.putObject("data");
            data.put("type", "betaTesters");

            ObjectNode attributes = data.putObject("attributes");
            attributes.put("email", command.appleEmail());
            attributes.put("firstName", command.firstName());
            attributes.put("lastName", command.lastName());

            ObjectNode relationships = data.putObject("relationships");
            ObjectNode apps = relationships.putObject("apps");
            ArrayNode appsData = apps.putArray("data");

            ObjectNode appNode = appsData.addObject();
            appNode.put("type", "apps");
            appNode.put("id", appId);

            String body = objectMapper.writeValueAsString(root);

            JsonNode created = postJson("/v1/betaTesters", body);
            String id = created.path("data").path("id").asText(null);

            if (id == null || id.isBlank()) {
                throw new IllegalStateException("Beta tester created but no id returned");
            }

            return id;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build beta tester payload", ex);
        }
    }

    private void ensureBetaTesterInGroup(String betaGroupId, String betaTesterId) {
        List<JsonNode> existing = getAllPages("/v1/betaGroups/" + betaGroupId + "/betaTesters?limit=200");
        for (JsonNode item : existing) {
            if (betaTesterId.equals(item.path("id").asText(null))) {
                return;
            }
        }

        try {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode data = root.putArray("data");

            ObjectNode testerNode = data.addObject();
            testerNode.put("type", "betaTesters");
            testerNode.put("id", betaTesterId);

            String body = objectMapper.writeValueAsString(root);
            postJson("/v1/betaGroups/" + betaGroupId + "/relationships/betaTesters", body);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to add beta tester to internal group", ex);
        }
    }

    private String findLatestBuildId(String appId) {
        JsonNode root = getJson("/v1/apps/" + appId + "/builds?limit=1&sort=-uploadedDate");
        JsonNode data = root.path("data");
        if (!data.isArray() || data.isEmpty()) {
            return null;
        }
        return data.get(0).path("id").asText(null);
    }

    private void ensureBuildInGroup(String betaGroupId, String buildId) {
        List<JsonNode> existing = getAllPages("/v1/betaGroups/" + betaGroupId + "/builds?limit=200");
        for (JsonNode item : existing) {
            if (buildId.equals(item.path("id").asText(null))) {
                return;
            }
        }

        try {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode data = root.putArray("data");

            ObjectNode buildNode = data.addObject();
            buildNode.put("type", "builds");
            buildNode.put("id", buildId);

            String body = objectMapper.writeValueAsString(root);
            postJson("/v1/betaGroups/" + betaGroupId + "/relationships/builds", body);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to add latest build to internal group", ex);
        }
    }

    private List<JsonNode> getAllPages(String firstPath) {
        List<JsonNode> all = new ArrayList<>();
        String next = firstPath;

        while (next != null && !next.isBlank()) {
            JsonNode root = getJson(next);
            JsonNode data = root.path("data");

            if (data.isArray()) {
                data.forEach(all::add);
            }

            String nextUrl = root.path("links").path("next").asText(null);
            if (nextUrl == null || nextUrl.isBlank() || "null".equalsIgnoreCase(nextUrl)) {
                next = null;
            } else {
                next = toRelativePath(nextUrl);
            }
        }

        return all;
    }

    private JsonNode getJson(String path) {
        String response = webClient.get()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateToken())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            return objectMapper.readTree(response);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse Apple GET response for path: " + path, ex);
        }
    }

    private JsonNode postJson(String path, String body) {
        String response = webClient.post()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateToken())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            return objectMapper.readTree(response);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse Apple POST response for path: " + path, ex);
        }
    }

    private String generateToken() {
        try {
            PrivateKey privateKey = loadPrivateKey();
            Instant now = Instant.now();
            Instant exp = now.plusSeconds(properties.getTokenTtlSeconds());

            return Jwts.builder()
                    .setIssuer(properties.getIssuerId())
                    .setAudience("appstoreconnect-v1")
                    .setIssuedAt(java.util.Date.from(now))
                    .setExpiration(java.util.Date.from(exp))
                    .setHeaderParam("kid", properties.getKeyId())
                    .signWith(privateKey, SignatureAlgorithm.ES256)
                    .compact();

        } catch (InvalidKeyException ex) {
            throw new IllegalStateException("Invalid Apple private key for ES256 signing", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate App Store Connect JWT", ex);
        }
    }

    private PrivateKey loadPrivateKey() throws Exception {
        String pem = resolvePrivateKeyPem();
        String normalized = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] der = Base64.getDecoder().decode(normalized);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        return keyFactory.generatePrivate(spec);
    }

    private String resolvePrivateKeyPem() throws Exception {
        if (!isBlank(properties.getPrivateKeyPem())) {
            return properties.getPrivateKeyPem().trim();
        }

        if (!isBlank(properties.getPrivateKeyB64())) {
            byte[] decoded = Base64.getDecoder().decode(properties.getPrivateKeyB64().trim());
            String decodedText = new String(decoded, StandardCharsets.UTF_8).trim();

            if (decodedText.contains("BEGIN PRIVATE KEY")) {
                return decodedText;
            }

            return """
                    -----BEGIN PRIVATE KEY-----
                    %s
                    -----END PRIVATE KEY-----
                    """.formatted(decodedText.trim());
        }

        if (!isBlank(properties.getPrivateKeyPath())) {
            return Files.readString(Path.of(properties.getPrivateKeyPath())).trim();
        }

        throw new IllegalStateException("Missing Apple private key source (privateKeyPem/privateKeyB64/privateKeyPath)");
    }

    private void validateProperties() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Real Apple integration is disabled");
        }
        if (isBlank(properties.getIssuerId())) {
            throw new IllegalStateException("Missing Apple issuerId");
        }
        if (isBlank(properties.getKeyId())) {
            throw new IllegalStateException("Missing Apple keyId");
        }
        if (isBlank(properties.getPrivateKeyPem())
                && isBlank(properties.getPrivateKeyB64())
                && isBlank(properties.getPrivateKeyPath())) {
            throw new IllegalStateException("Missing Apple private key configuration");
        }
        if (properties.getTokenTtlSeconds() <= 0 || properties.getTokenTtlSeconds() > 1200) {
            throw new IllegalStateException("Apple token ttl must be between 1 and 1200 seconds");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String url(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String toRelativePath(String absoluteUrl) {
        if (absoluteUrl == null || absoluteUrl.isBlank()) {
            return null;
        }

        String base = properties.getBaseUrl();
        if (absoluteUrl.startsWith(base)) {
            return absoluteUrl.substring(base.length());
        }

        return absoluteUrl;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}