package com.build4all.publish.dto;

import com.build4all.publish.domain.PublishStore;

public class PublisherProfileDto {
    private Long id;
    private PublishStore store;
    private String developerName;
    private String developerEmail;
    private String privacyPolicyUrl;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public PublishStore getStore() { return store; }
    public void setStore(PublishStore store) { this.store = store; }
    public String getDeveloperName() { return developerName; }
    public void setDeveloperName(String developerName) { this.developerName = developerName; }
    public String getDeveloperEmail() { return developerEmail; }
    public void setDeveloperEmail(String developerEmail) { this.developerEmail = developerEmail; }
    public String getPrivacyPolicyUrl() { return privacyPolicyUrl; }
    public void setPrivacyPolicyUrl(String privacyPolicyUrl) { this.privacyPolicyUrl = privacyPolicyUrl; }
}
