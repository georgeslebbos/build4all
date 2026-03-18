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
import org.springframework.web.reactive.function.client.WebClientResponseException;

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
import java.util.function.BooleanSupplier;

@Component
@Primary
@ConditionalOnProperty(prefix = "build4all.ios-internal.apple", name = "enabled", havingValue = "true")
public class AppStoreConnectAppleInternalTestingGateway implements AppleInternalTestingGateway {

    private static final int VERIFY_ATTEMPTS = 15;
    private static final long VERIFY_SLEEP_MS = 1000L;

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

        if (!isBlank(existingUserId)) {
            boolean internalReady = safeEnsureInternalTestingAccess(appId, command);

            if (internalReady) {
                return new AppleInternalTestingGatewayResult(
                        AppleInternalTestingGatewayOutcome.EXISTING_USER_ADDED,
                        existingUserId,
                        null,
                        "Existing App Store Connect user found and internal testing access is ready"
                );
            }

            return new AppleInternalTestingGatewayResult(
                    AppleInternalTestingGatewayOutcome.INTERNAL_ACCESS_PENDING,
                    existingUserId,
                    null,
                    "Existing App Store Connect user found, but internal testing access is not confirmed yet"
            );
        }

        String existingInvitationId = findPendingInvitationIdByEmail(command.appleEmail());
        if (!isBlank(existingInvitationId)) {
            return new AppleInternalTestingGatewayResult(
                    AppleInternalTestingGatewayOutcome.INVITATION_SENT,
                    null,
                    existingInvitationId,
                    "Invitation already exists and is still pending"
            );
        }

        try {
            String invitationId = inviteUser(command);

            return new AppleInternalTestingGatewayResult(
                    AppleInternalTestingGatewayOutcome.INVITATION_SENT,
                    null,
                    invitationId,
                    "Apple invitation sent successfully"
            );

        } catch (WebClientResponseException.Conflict ex) {
            System.out.println("INVITE CONFLICT => " + ex.getResponseBodyAsString());

            boolean internalReady = safeEnsureInternalTestingAccess(appId, command);
            String resolvedUserId = resolveAcceptedUserId(command.appleEmail(), null);

            if (internalReady) {
                return new AppleInternalTestingGatewayResult(
                        AppleInternalTestingGatewayOutcome.EXISTING_USER_ADDED,
                        resolvedUserId,
                        null,
                        "Invitation conflicted, but internal testing access was confirmed directly"
                );
            }

            return new AppleInternalTestingGatewayResult(
                    AppleInternalTestingGatewayOutcome.INTERNAL_ACCESS_PENDING,
                    resolvedUserId,
                    null,
                    "Invitation conflicted and Apple user likely exists, but internal testing access is not confirmed yet"
            );
        }
    }

    @Override
    public AppleInternalTestingGatewayResult syncInvitation(
            AppleInternalTestingCommand command,
            String invitationId
    ) {
        validateProperties();

        String appId = findAppIdByBundleId(command.bundleId());

        System.out.println("SYNC invitationId => " + invitationId);
        System.out.println("SYNC email => " + command.appleEmail());

        if (!isBlank(invitationId)) {
            boolean stillPending = isInvitationStillPending(invitationId);
            System.out.println("SYNC stillPending => " + stillPending);

            if (stillPending) {
                return new AppleInternalTestingGatewayResult(
                        AppleInternalTestingGatewayOutcome.STILL_WAITING,
                        null,
                        invitationId,
                        "User still has not accepted the App Store Connect invitation"
                );
            }

            System.out.println("SYNC invitation no longer pending => assume accepted");
        } else {
            System.out.println("SYNC without invitationId => retry internal access only");
        }

        boolean internalReady = safeEnsureInternalTestingAccess(appId, command);
        String resolvedUserId = resolveAcceptedUserId(command.appleEmail(), invitationId);

        System.out.println("SYNC resolvedUserId => " + resolvedUserId);
        System.out.println("SYNC internalReady => " + internalReady);

        if (internalReady) {
            return new AppleInternalTestingGatewayResult(
                    AppleInternalTestingGatewayOutcome.INVITATION_ACCEPTED_AND_ADDED,
                    resolvedUserId,
                    invitationId,
                    "Invitation accepted/user exists and internal testing access is ready"
            );
        }

        return new AppleInternalTestingGatewayResult(
                AppleInternalTestingGatewayOutcome.INTERNAL_ACCESS_PENDING,
                resolvedUserId,
                invitationId,
                "Invitation accepted/user exists, but internal testing access is not confirmed yet"
        );
    }

    private boolean safeEnsureInternalTestingAccess(String appId, AppleInternalTestingCommand command) {
        try {
            return ensureInternalTestingAccess(appId, command);
        } catch (Exception ex) {
            System.out.println("❌ ensureInternalTestingAccess failed => " + detailedError(ex));
            ex.printStackTrace();
            return false;
        }
    }

    private boolean isInvitationStillPending(String invitationId) {
        if (isBlank(invitationId)) {
            return false;
        }

        List<JsonNode> invitations = getAllPages("/v1/userInvitations?limit=200");
        for (JsonNode item : invitations) {
            String currentId = item.path("id").asText(null);
            if (invitationId.equals(currentId)) {
                return true;
            }
        }

        return false;
    }

    private String resolveAcceptedUserId(String appleEmail, String invitationId) {
        if (!isBlank(invitationId)) {
            String matchedById = findUserIdById(invitationId);
            if (!isBlank(matchedById)) {
                return matchedById;
            }
        }

        String userId = findUserIdByEmail(appleEmail);
        if (!isBlank(userId)) {
            return userId;
        }

        return null;
    }

    private String findUserIdById(String userId) {
        if (isBlank(userId)) {
            return null;
        }

        List<JsonNode> users = getAllPages("/v1/users?limit=200");
        for (JsonNode item : users) {
            String currentId = item.path("id").asText(null);
            if (userId.equals(currentId)) {
                return currentId;
            }
        }

        return null;
    }

    private boolean ensureInternalTestingAccess(String appId, AppleInternalTestingCommand command) {
        System.out.println("ENSURE INTERNAL ACCESS START appId => " + appId);

        String betaGroupId = findOrCreateInternalBetaGroup(appId);
        System.out.println("betaGroupId => " + betaGroupId);

        String latestBuildId = findLatestBuildId(appId);
        System.out.println("latestBuildId => " + latestBuildId);

        boolean buildReady = true;
        if (!isBlank(latestBuildId)) {
            buildReady = ensureBuildInGroup(betaGroupId, latestBuildId);
            System.out.println("buildReady => " + buildReady);
        }

        String betaTesterId = findOrCreateBetaTester(betaGroupId, command);
        System.out.println("betaTesterId => " + betaTesterId);

        boolean testerReady = false;
        if (!isBlank(betaTesterId)) {
            testerReady = ensureBetaTesterInGroup(betaGroupId, betaTesterId);
            System.out.println("testerReady => " + testerReady);
        } else {
            System.out.println("betaTesterId unresolved => internal access not confirmed");
        }

        boolean internalReady = buildReady && testerReady;
        System.out.println("ENSURE INTERNAL ACCESS DONE => " + internalReady);

        return internalReady;
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

        System.out.println("TARGET EMAIL => " + target);
        System.out.println("USERS COUNT => " + users.size());

        for (JsonNode item : users) {
            String currentEmail = normalize(item.path("attributes").path("email").asText(null));
            String id = item.path("id").asText(null);

            System.out.println("APPLE USER => id=" + id + ", email=" + currentEmail);

            if (target.equals(currentEmail)) {
                System.out.println("MATCHED APPLE USER ID => " + id);
                return id;
            }
        }

        System.out.println("NO APPLE USER MATCH FOUND FOR => " + target);
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

            if (isBlank(invitationId)) {
                throw new IllegalStateException("Apple invitation created but no id returned");
            }

            return invitationId;
        } catch (WebClientResponseException.Conflict ex) {
            throw ex;
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

            if (isBlank(id)) {
                throw new IllegalStateException("Internal beta group was created but no id returned");
            }

            return id;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build internal beta group payload", ex);
        }
    }

    private String findOrCreateBetaTester(String betaGroupId, AppleInternalTestingCommand command) {
        String target = normalize(command.appleEmail());

        System.out.println("FIND/CREATE BETA TESTER START => " + target);

        List<JsonNode> testers = getAllPages("/v1/betaTesters?limit=200");
        System.out.println("BETA TESTERS COUNT => " + testers.size());

        for (JsonNode item : testers) {
            String currentEmail = normalize(item.path("attributes").path("email").asText(null));
            String currentId = item.path("id").asText(null);

            System.out.println("BETA TESTER => id=" + currentId + ", email=" + currentEmail);

            if (target.equals(currentEmail)) {
                System.out.println("MATCHED EXISTING BETA TESTER ID => " + currentId);
                return currentId;
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
            ObjectNode betaGroups = relationships.putObject("betaGroups");
            ArrayNode betaGroupsData = betaGroups.putArray("data");

            ObjectNode groupNode = betaGroupsData.addObject();
            groupNode.put("type", "betaGroups");
            groupNode.put("id", betaGroupId);

            String body = objectMapper.writeValueAsString(root);
            System.out.println("CREATE BETA TESTER BODY => " + body);

            JsonNode created = postJson("/v1/betaTesters", body);
            System.out.println("CREATE BETA TESTER RESPONSE => " + created);

            String id = created.path("data").path("id").asText(null);

            if (isBlank(id)) {
                throw new IllegalStateException("Beta tester created but no id returned");
            }

            System.out.println("CREATED BETA TESTER ID => " + id);
            return id;

        } catch (WebClientResponseException.Conflict ex) {
            System.out.println("BETA TESTER CREATE CONFLICT => " + ex.getResponseBodyAsString());

            List<JsonNode> testersAfterConflict = getAllPages("/v1/betaTesters?limit=200");
            System.out.println("BETA TESTERS COUNT AFTER CONFLICT => " + testersAfterConflict.size());

            for (JsonNode item : testersAfterConflict) {
                String currentEmail = normalize(item.path("attributes").path("email").asText(null));
                String currentId = item.path("id").asText(null);

                System.out.println("BETA TESTER AFTER CONFLICT => id=" + currentId + ", email=" + currentEmail);

                if (target.equals(currentEmail)) {
                    System.out.println("MATCHED BETA TESTER AFTER CONFLICT => " + currentId);
                    return currentId;
                }
            }

            System.out.println("Could not resolve beta tester after conflict; treating as existing hidden beta tester");
            return null;

        } catch (Exception ex) {
            System.out.println("❌ findOrCreateBetaTester failed => "
                    + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()));
            ex.printStackTrace();
            throw new IllegalStateException("Failed to find or create beta tester: " + detailedError(ex), ex);
        }
    }

    private boolean ensureBetaTesterInGroup(String betaGroupId, String betaTesterId) {
        if (isBlank(betaTesterId)) {
            System.out.println("betaTesterId is blank => cannot confirm tester-group link");
            return false;
        }

        if (isBetaTesterInGroup(betaGroupId, betaTesterId)) {
            System.out.println("beta tester already in group");
            return true;
        }

        boolean relationshipPosted = false;

        try {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode data = root.putArray("data");

            ObjectNode testerNode = data.addObject();
            testerNode.put("type", "betaTesters");
            testerNode.put("id", betaTesterId);

            String body = objectMapper.writeValueAsString(root);
            System.out.println("ADD BETA TESTER TO GROUP BODY => " + body);

            postJson("/v1/betaGroups/" + betaGroupId + "/relationships/betaTesters", body);
            relationshipPosted = true;

            System.out.println("ADD BETA TESTER TO GROUP POSTED");

        } catch (WebClientResponseException.Conflict ex) {
            relationshipPosted = true;
            System.out.println("ADD BETA TESTER TO GROUP CONFLICT => maybe already linked");
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to add beta tester to internal group: " + detailedError(ex),
                    ex
            );
        }

        boolean found = waitUntil(
                () -> isBetaTesterInGroup(betaGroupId, betaTesterId),
                VERIFY_ATTEMPTS,
                VERIFY_SLEEP_MS
        );

        if (found) {
            System.out.println("beta tester verified inside group");
            return true;
        }

        
        if (relationshipPosted) {
            System.out.println("WARNING: relationship posted but tester not visible yet => treat as NOT CONFIRMED");
            return false;
        }

        return false;
    }
    

    private boolean isBetaTesterInGroup(String betaGroupId, String betaTesterId) {
        if (isBlank(betaTesterId)) {
            return false;
        }

        List<JsonNode> existing = getAllPages("/v1/betaGroups/" + betaGroupId + "/betaTesters?limit=200");
        for (JsonNode item : existing) {
            if (betaTesterId.equals(item.path("id").asText(null))) {
                return true;
            }
        }
        return false;
    }

    private String findLatestBuildId(String appId) {
        List<JsonNode> builds = getAllPages("/v1/apps/" + appId + "/builds?limit=200");

        if (builds.isEmpty()) {
            return null;
        }

        JsonNode latest = null;
        Instant latestUploadedAt = null;

        for (JsonNode build : builds) {
            String uploadedDateText = build.path("attributes").path("uploadedDate").asText(null);
            Instant uploadedAt = parseInstantSafe(uploadedDateText);

            if (latest == null) {
                latest = build;
                latestUploadedAt = uploadedAt;
                continue;
            }

            if (uploadedAt != null && (latestUploadedAt == null || uploadedAt.isAfter(latestUploadedAt))) {
                latest = build;
                latestUploadedAt = uploadedAt;
            }
        }

        return latest != null ? latest.path("id").asText(null) : null;
    }

    private Instant parseInstantSafe(String value) {
        if (isBlank(value)) {
            return null;
        }

        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean ensureBuildInGroup(String betaGroupId, String buildId) {
        if (isBlank(buildId)) {
            return true;
        }

        if (isBuildInGroup(betaGroupId, buildId)) {
            return true;
        }

        try {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode data = root.putArray("data");

            ObjectNode buildNode = data.addObject();
            buildNode.put("type", "builds");
            buildNode.put("id", buildId);

            String body = objectMapper.writeValueAsString(root);
            postJson("/v1/betaGroups/" + betaGroupId + "/relationships/builds", body);

        } catch (WebClientResponseException.Conflict ex) {
            System.out.println("ADD BUILD TO GROUP CONFLICT => maybe already linked");
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to add latest build to internal group: " + detailedError(ex),
                    ex
            );
        }

        boolean found = waitUntil(() -> isBuildInGroup(betaGroupId, buildId), VERIFY_ATTEMPTS, VERIFY_SLEEP_MS);
        if (found) {
            return true;
        }

        System.out.println("WARNING: build relationship posted but build not visible yet => treat as NOT CONFIRMED");
        return false;
    }

    private boolean isBuildInGroup(String betaGroupId, String buildId) {
        List<JsonNode> existing = getAllPages("/v1/betaGroups/" + betaGroupId + "/builds?limit=200");
        for (JsonNode item : existing) {
            if (buildId.equals(item.path("id").asText(null))) {
                return true;
            }
        }
        return false;
    }

    private boolean waitUntil(BooleanSupplier check, int attempts, long sleepMs) {
        for (int i = 0; i < attempts; i++) {
            try {
                if (check.getAsBoolean()) {
                    return true;
                }
            } catch (Exception ignored) {
            }

            if (i < attempts - 1) {
                sleepQuietly(sleepMs);
            }
        }
        return false;
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
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
                .defaultIfEmpty("")
                .block();

        try {
            if (response == null || response.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(response);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse Apple POST response for path: " + path, ex);
        }
    }

    private String detailedError(Throwable ex) {
        if (ex instanceof WebClientResponseException webEx) {
            String body = webEx.getResponseBodyAsString();
            return "HTTP " + webEx.getStatusCode().value()
                    + " from Apple: "
                    + ((body == null || body.isBlank()) ? webEx.getStatusText() : body);
        }
        return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
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
        if (base != null && !base.isBlank() && absoluteUrl.startsWith(base)) {
            return absoluteUrl.substring(base.length());
        }

        return absoluteUrl;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}