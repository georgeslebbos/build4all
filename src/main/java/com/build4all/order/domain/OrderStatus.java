package com.build4all.order.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "order_status")
public class OrderStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 32)
    private String name; // e.g. "PENDING", "COMPLETED", "CANCELED", ...

    public OrderStatus() {}

    public OrderStatus(String name) {
        this.name = name;
    }

    public OrderStatus(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    // --- Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
