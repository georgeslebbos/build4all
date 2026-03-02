package com.build4all.licensing.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_infrastructure")
public class AppInfrastructure {

    @Id
    @Column(name = "aup_id")
    private Long aupId;

    @Enumerated(EnumType.STRING)
    @Column(name = "infra_type", nullable = false, length = 20)
    private InfraType infraType = InfraType.SHARED;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dedicated_server_id", unique = true)
    private DedicatedServer dedicatedServer;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public AppInfrastructure() {}

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getAupId() { return aupId; }
    public void setAupId(Long aupId) { this.aupId = aupId; }

    public InfraType getInfraType() { return infraType; }
    public void setInfraType(InfraType infraType) { this.infraType = infraType; }

    public DedicatedServer getDedicatedServer() { return dedicatedServer; }
    public void setDedicatedServer(DedicatedServer dedicatedServer) { this.dedicatedServer = dedicatedServer; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
