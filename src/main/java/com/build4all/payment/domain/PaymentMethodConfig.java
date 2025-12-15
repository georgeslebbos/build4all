package com.build4all.payment.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "payment_method_configs",
        // This ensures ONE config row per (project, gateway).
        // Example: ownerProjectId=100 can have only one STRIPE config row.
        uniqueConstraints = @UniqueConstraint(columnNames = {"owner_project_id", "payment_method_id"})
)
public class PaymentMethodConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // Internal DB id for this config record

    @Column(name = "owner_project_id", nullable = false)
    private Long ownerProjectId;
    // Which store/project this config belongs to (Build4All tenant/app)
    // This is the key that makes it "WooCommerce per store".

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id", nullable = false)
    private PaymentMethod paymentMethod;
    // FK reference to the PLATFORM gateway catalog (PaymentMethod table).
    // Example: PaymentMethod(name="STRIPE")

    @Column(nullable = false)
    private boolean enabled;
    // Project-level toggle:
    // true  => this project will show Stripe at checkout
    // false => Stripe hidden for this project even if platform supports it

    @Lob
    @Column(name = "config_json")
    private String configJson;
    // IMPORTANT: JSON string containing gateway-specific settings.
    // For Stripe, you typically store:
    // {
    //   "secretKey": "sk_test_...",
    //   "publishableKey": "pk_test_...",
    //   "webhookSecret": "whsec_...",
    //   "platformFeePct": 10
    // }
    //
    // For another gateway (e.g. PayPal), it could store:
    // {
    //   "clientId": "...",
    //   "clientSecret": "...",
    //   "mode": "SANDBOX"
    // }
    //
    // That is why this is plugin-friendly and doesn't require code changes
    // when adding new providers.

    private LocalDateTime updatedAt;
    // Tracking last update time (useful for audit + debug)

    @PrePersist
    @PreUpdate
    public void touch() {
        // Automatically update timestamp whenever row is inserted/updated
        updatedAt = LocalDateTime.now();
    }

    // ---------------- Getters / Setters ----------------

    public Long getId() { return id; }

    public Long getOwnerProjectId() { return ownerProjectId; }
    public void setOwnerProjectId(Long ownerProjectId) { this.ownerProjectId = ownerProjectId; }

    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
