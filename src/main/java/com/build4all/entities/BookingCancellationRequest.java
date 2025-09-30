package com.build4all.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "booking_cancellation_requests",
       uniqueConstraints = @UniqueConstraint(columnNames = {"booking_id", "state"}))
public class BookingCancellationRequest {

  public enum State { PENDING, APPROVED, REJECTED }

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "booking_id")
  private ItemBooking booking;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "requested_by_user_id")
  private Users requestedBy;

  @Column(length = 500)
  private String userReason;

  @Column(length = 500)
  private String decisionNote;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "decided_by_business_user_id")
  private BusinessUser decidedBy;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private State state = State.PENDING;

  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  private LocalDateTime decidedAt;

  @Version
  private Long version;

  @PrePersist
  void onCreate() { this.createdAt = LocalDateTime.now(); }

  // Getters & Setters
  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public ItemBooking getBooking() { return booking; }
  public void setBooking(ItemBooking booking) { this.booking = booking; }
  public Users getRequestedBy() { return requestedBy; }
  public void setRequestedBy(Users requestedBy) { this.requestedBy = requestedBy; }
  public String getUserReason() { return userReason; }
  public void setUserReason(String userReason) { this.userReason = userReason; }
  public String getDecisionNote() { return decisionNote; }
  public void setDecisionNote(String decisionNote) { this.decisionNote = decisionNote; }
  public BusinessUser getDecidedBy() { return decidedBy; }
  public void setDecidedBy(BusinessUser decidedBy) { this.decidedBy = decidedBy; }
  public State getState() { return state; }
  public void setState(State state) { this.state = state; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public LocalDateTime getDecidedAt() { return decidedAt; }
  public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }
  public Long getVersion() { return version; }
  public void setVersion(Long version) { this.version = version; }
}
