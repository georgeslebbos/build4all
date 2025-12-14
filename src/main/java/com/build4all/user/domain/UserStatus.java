package com.build4all.user.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "user_status")
public class UserStatus {

    /**
     * Primary key for the status row (AUTO-INCREMENT).
     * Example rows:
     *   (1, "ACTIVE"), (2, "INACTIVE"), (3, "DELETED"), ...
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Business-readable status name.
     * - unique: you can’t have two rows both named "ACTIVE"
     * - nullable=false: every status row must have a name
     *
     * Your Users entity references this table using:
     *   @ManyToOne @JoinColumn(name="status")
     * So the column "status" in users table stores the FK to UserStatus.id
     */
    @Column(unique = true, nullable = false)
    private String name; // e.g., ACTIVE, INACTIVE, DELETED

    // JPA requires a no-args constructor
    public UserStatus() {}

    // Convenience constructor to quickly create a status row
    public UserStatus(String name) {
        this.name = name;
    }

    // Getters & Setters (standard Java bean methods)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
