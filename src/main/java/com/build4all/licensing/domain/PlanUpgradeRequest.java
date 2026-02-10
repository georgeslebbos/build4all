package com.build4all.licensing.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "plan_upgrade_requests")
public class PlanUpgradeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aup_id", nullable = false)
    private Long aupId;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_plan_code", nullable = false, length = 30)
    private PlanCode requestedPlanCode;

    @Column(name = "users_allowed_override")
    private Integer usersAllowedOverride; // null = use plan default

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanUpgradeRequestStatus status = PlanUpgradeRequestStatus.PENDING;

    @Column(name = "requested_by_user_id")
    private Long requestedByUserId;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "decided_by_user_id")
    private Long decidedByUserId;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_note", length = 255)
    private String decisionNote;

    @PrePersist
    public void onCreate() {
        this.requestedAt = LocalDateTime.now();
    }

    // getters/setters
    public Long getId() { return id; }

    public Long getAupId() { return aupId; }
    public void setAupId(Long aupId) { this.aupId = aupId; }

    public PlanCode getRequestedPlanCode() { return requestedPlanCode; }
    public void setRequestedPlanCode(PlanCode requestedPlanCode) { this.requestedPlanCode = requestedPlanCode; }

    public Integer getUsersAllowedOverride() { return usersAllowedOverride; }
    public void setUsersAllowedOverride(Integer usersAllowedOverride) { this.usersAllowedOverride = usersAllowedOverride; }

    public PlanUpgradeRequestStatus getStatus() { return status; }
    public void setStatus(PlanUpgradeRequestStatus status) { this.status = status; }

    public Long getRequestedByUserId() { return requestedByUserId; }
    public void setRequestedByUserId(Long requestedByUserId) { this.requestedByUserId = requestedByUserId; }

    public LocalDateTime getRequestedAt() { return requestedAt; }

    public Long getDecidedByUserId() { return decidedByUserId; }
    public void setDecidedByUserId(Long decidedByUserId) { this.decidedByUserId = decidedByUserId; }

    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }

    public String getDecisionNote() { return decisionNote; }
    public void setDecisionNote(String decisionNote) { this.decisionNote = decisionNote; }
}
