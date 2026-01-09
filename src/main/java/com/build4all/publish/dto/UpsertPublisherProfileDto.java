package com.build4all.publish.dto;

import com.build4all.publish.domain.PublishStore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class UpsertPublisherProfileDto {

    @NotNull
    private PublishStore store; // PLAY_STORE / APP_STORE

    @NotBlank
    private String developerName;

    @NotBlank
    private String developerEmail;

    @NotBlank
    private String privacyPolicyUrl;

    public PublishStore getStore() { return store; }
    public void setStore(PublishStore store) { this.store = store; }

    public String getDeveloperName() { return developerName; }
    public void setDeveloperName(String developerName) { this.developerName = developerName; }

    public String getDeveloperEmail() { return developerEmail; }
    public void setDeveloperEmail(String developerEmail) { this.developerEmail = developerEmail; }

    public String getPrivacyPolicyUrl() { return privacyPolicyUrl; }
    public void setPrivacyPolicyUrl(String privacyPolicyUrl) { this.privacyPolicyUrl = privacyPolicyUrl; }
}
