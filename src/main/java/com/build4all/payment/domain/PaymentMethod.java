package com.build4all.payment.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "payment_methods")
public class PaymentMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name; // e.g., STRIPE, PAYPAL, CASH

    @Column(nullable = false)
    private boolean enabled;

    public PaymentMethod() {}

    public PaymentMethod(String name, boolean enabled) {
        this.name = name;
        this.enabled = enabled;
    }

    // Getters and Setters
    public Long getId() { return id; }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
