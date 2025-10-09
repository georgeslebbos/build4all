package com.build4all.booking.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "booking_status")
public class BookingStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 32)
    private String name; // e.g. "PENDING", "APPROVED", "REJECTED"

    // --- Constructors
    public BookingStatus() {}
    public BookingStatus(String name) { this.name = name; }

    public BookingStatus(Long id, String name)
    {
        this.id = id;
        this.name = name;
    }

    // --- Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
