package com.build4all.payment.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "payment_methods")
// This table is the PLATFORM CATALOG of payment gateways (like WooCommerce plugins).
// It answers: "Does the platform support STRIPE / PAYPAL / CASH at all?"
public class PaymentMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // Internal DB id (not used by the mobile app directly)

    @Column(nullable = false, unique = true)
    private String name;
    // The gateway CODE (very important)
    // Examples: "STRIPE", "PAYPAL", "CASH"
    // This code should match your gateway plugin code() in PaymentGateway
    // Example: StripeGateway.code() returns "STRIPE"

    @Column(nullable = false)
    private boolean enabled;
    // Platform-level toggle:
    // true  => platform allows this gateway to be used by any project
    // false => gateway is globally disabled, even if an owner wants it

    public PaymentMethod() {}

    public PaymentMethod(String name, boolean enabled) {
        // Recommended: always store normalized uppercase names
        // this.name = name == null ? null : name.trim().toUpperCase();
        this.name = name;
        this.enabled = enabled;
    }

    // ---------------- Getters / Setters ----------------

    public Long getId() { return id; }

    public String getName() { return name; }

    public void setName(String name) {
        // Recommended normalization:
        // this.name = name == null ? null : name.trim().toUpperCase();
        this.name = name;
    }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
