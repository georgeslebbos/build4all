package com.build4all.payment.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "payment_transactions",
        indexes = {
                // Used when you want to quickly find payments by order id
                @Index(name="idx_pay_tx_order", columnList="order_id"),

                // Used to filter transactions per store/project (multi-tenant)
                @Index(name="idx_pay_tx_owner_project", columnList="owner_project_id"),

                // Used by webhook lookup:
                // Stripe sends payment_intent id (pi_...), so we search by (provider_code + provider_payment_id)
                @Index(name="idx_pay_tx_provider_payment", columnList="provider_code, provider_payment_id")
        }
)
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // Internal DB id for this transaction row

    @Column(name="owner_project_id", nullable=false)
    private Long ownerProjectId;
    // Which store/project (tenant/app) this payment belongs to
    // Important in multi-tenant Build4All

    @Column(name="order_id", nullable=false)
    private Long orderId;
    // Your internal Order id
    // One order can have multiple transactions (retries), so do not assume 1:1 always.

    @Column(name="provider_code", nullable=false, length=50)
    private String providerCode;
    // Gateway code: STRIPE, CASH, PAYPAL...
    // Must match PaymentMethod.name and PaymentGateway.code()

    @Column(name="provider_payment_id", length=200)
    private String providerPaymentId;
    // The gateway's reference id:
    // Stripe: PaymentIntent id "pi_..."
    // PayPal: order id, capture id, etc.
    // Cash: you can store a generated reference like "CASH_ORDER_123"

    @Column(nullable=false, precision=18, scale=2)
    private BigDecimal amount;
    // Transaction amount (for audit + reconciliation)
    // Use BigDecimal to avoid floating point issues.

    @Column(nullable=false, length=10)
    private String currency;
    // Currency code (usually lowercase e.g. "usd")
    // Keep it normalized to avoid inconsistent comparisons.

    @Column(nullable=false, length=50)
    private String status;
    // Your internal status values.
    // Typical recommended values:
    // CREATED           -> you created the transaction row
    // REQUIRES_ACTION   -> client must do something (3DS / confirm, etc.)
    // PAID              -> confirmed (usually via webhook)
    // FAILED            -> payment failed
    // OFFLINE_PENDING   -> cash / bank transfer pending

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Lob
    private String rawProviderPayload;
    // Optional: store raw Stripe webhook payload or provider response
    // Useful for debugging, disputes, reconciliation, and audits.
    // IMPORTANT: keep it reasonable (it can be large), and do not expose it to the frontend.

    @PrePersist
    public void onCreate() {
        // Automatically set timestamps when inserting a new row
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        // Automatically update "updatedAt" whenever row changes
        updatedAt = LocalDateTime.now();
    }

    // ---------------- Getters / Setters ----------------

    public Long getId() { return id; }

    public Long getOwnerProjectId() { return ownerProjectId; }
    public void setOwnerProjectId(Long ownerProjectId) { this.ownerProjectId = ownerProjectId; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getProviderCode() { return providerCode; }
    public void setProviderCode(String providerCode) { this.providerCode = providerCode; }

    public String getProviderPaymentId() { return providerPaymentId; }
    public void setProviderPaymentId(String providerPaymentId) { this.providerPaymentId = providerPaymentId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public String getRawProviderPayload() { return rawProviderPayload; }
    public void setRawProviderPayload(String rawProviderPayload) { this.rawProviderPayload = rawProviderPayload; }
}
