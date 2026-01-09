package com.build4all.publish.domain;


import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "store_publisher_profile")
public class StorePublisherProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "store", nullable = false, length = 20)
    private PublishStore store;

    @Column(name = "developer_name", nullable = false, length = 150)
    private String developerName;

    @Column(name = "developer_email", nullable = false, length = 255)
    private String developerEmail;

    @Column(name = "privacy_policy_url", nullable = false, columnDefinition = "TEXT")
    private String privacyPolicyUrl;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // getters/setters
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

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
