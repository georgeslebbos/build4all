package com.build4all.order.domain;

import com.build4all.business.domain.BusinessUser;
import com.build4all.user.domain.Users;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "booking_cancellation_requests",
        // Consider removing the unique constraint (see note #4)
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_bcr_booking_state", columnNames = {"booking_id", "state_id"})
        },
        indexes = {
                @Index(name = "idx_bcr_booking", columnList = "booking_id"),
                @Index(name = "idx_bcr_requested_by", columnList = "requested_by_user_id"),
                @Index(name = "idx_bcr_decided_by", columnList = "decided_by_business_user_id"),
                @Index(name = "idx_bcr_state", columnList = "state_id")
        }
)
public class BookingCancellationRequest {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // Per-line (ItemBooking) cancellation:
  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "booking_id", nullable = false)
  private OrderItem booking;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "requested_by_user_id", nullable = false)
  private Users requestedBy;

  @Column(length = 500)
  private String userReason;

  @Column(length = 500)
  private String decisionNote;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "decided_by_business_user_id")
  private BusinessUser decidedBy;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "state_id", nullable = false)
  private OrderStatus state;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "decided_at")
  private LocalDateTime decidedAt;

  @Version
  private Long version;

  @PrePersist
  void onCreate() {
    if (this.createdAt == null) this.createdAt = LocalDateTime.now();
  }

  // getters/setters...
  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public OrderItem getBooking() { return booking; }
  public void setBooking(OrderItem booking) { this.booking = booking; }

  public Users getRequestedBy() { return requestedBy; }
  public void setRequestedBy(Users requestedBy) { this.requestedBy = requestedBy; }

  public String getUserReason() { return userReason; }
  public void setUserReason(String userReason) { this.userReason = userReason; }

  public String getDecisionNote() { return decisionNote; }
  public void setDecisionNote(String decisionNote) { this.decisionNote = decisionNote; }

  public BusinessUser getDecidedBy() { return decidedBy; }
  public void setDecidedBy(BusinessUser decidedBy) { this.decidedBy = decidedBy; }

  public OrderStatus getState() { return state; }
  public void setState(OrderStatus state) { this.state = state; }

  public LocalDateTime getCreatedAt() { return createdAt; }
  public LocalDateTime getDecidedAt() { return decidedAt; }
  public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }

  public Long getVersion() { return version; }
  public void setVersion(Long version) { this.version = version; }
}
