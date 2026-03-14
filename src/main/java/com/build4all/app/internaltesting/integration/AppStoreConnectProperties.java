package com.build4all.app.internaltesting.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "build4all.ios-internal.apple")
public class AppStoreConnectProperties {

    private boolean enabled = false;

    private String baseUrl = "https://api.appstoreconnect.apple.com";

    private String issuerId;

    private String keyId;

    /**
     * Preferred for CI/CD: base64 of the .p8 file content or PEM text.
     */
    private String privateKeyB64;

    /**
     * Optional raw PEM content.
     */
    private String privateKeyPem;

    /**
     * Optional local/server path fallback.
     */
    private String privateKeyPath;

    private String invitationRole = "DEVELOPER";

    private String internalGroupName = "Internal";

    private long tokenTtlSeconds = 900;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getIssuerId() {
        return issuerId;
    }

    public void setIssuerId(String issuerId) {
        this.issuerId = issuerId;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getPrivateKeyB64() {
        return privateKeyB64;
    }

    public void setPrivateKeyB64(String privateKeyB64) {
        this.privateKeyB64 = privateKeyB64;
    }

    public String getPrivateKeyPem() {
        return privateKeyPem;
    }

    public void setPrivateKeyPem(String privateKeyPem) {
        this.privateKeyPem = privateKeyPem;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    public String getInvitationRole() {
        return invitationRole;
    }

    public void setInvitationRole(String invitationRole) {
        this.invitationRole = invitationRole;
    }

    public String getInternalGroupName() {
        return internalGroupName;
    }

    public void setInternalGroupName(String internalGroupName) {
        this.internalGroupName = internalGroupName;
    }

    public long getTokenTtlSeconds() {
        return tokenTtlSeconds;
    }

    public void setTokenTtlSeconds(long tokenTtlSeconds) {
        this.tokenTtlSeconds = tokenTtlSeconds;
    }
}