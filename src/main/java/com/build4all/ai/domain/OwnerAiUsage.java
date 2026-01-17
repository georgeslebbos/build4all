package com.build4all.ai.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "owner_ai_usage")
@IdClass(OwnerAiUsageId.class)
public class OwnerAiUsage {

    @Id
    @Column(name = "owner_id")
    private Long ownerId;

    @Id
    @Column(name = "usage_date")
    private LocalDate usageDate;

    @Column(name = "request_count", nullable = false)
    private int requestCount = 0;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public OwnerAiUsage() {}

    public OwnerAiUsage(Long ownerId, LocalDate usageDate, int requestCount) {
        this.ownerId = ownerId;
        this.usageDate = usageDate;
        this.requestCount = requestCount;
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // getters/setters
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }

    public LocalDate getUsageDate() { return usageDate; }
    public void setUsageDate(LocalDate usageDate) { this.usageDate = usageDate; }

    public int getRequestCount() { return requestCount; }
    public void setRequestCount(int requestCount) { this.requestCount = requestCount; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
