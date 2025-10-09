package com.build4all.notifications.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "notification_types")
public class NotificationTypeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code; // e.g. "BOOKING_CREATED"

    @Column(nullable = false)
    private String description; // e.g. "Booking Created"

    public NotificationTypeEntity(String code)
    {
        this.code = code;
    }


    public NotificationTypeEntity(Long id, String code, String description)
    {
        this.id = id;
        this.code = code;
        this.description = description;
    }

    public NotificationTypeEntity()
    {

    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
