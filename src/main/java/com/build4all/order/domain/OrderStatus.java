package com.build4all.order.domain;

import jakarta.persistence.*;

/**
 * OrderStatus (order_status table)
 *
 * Stores the allowed statuses for an Order header.
 * Examples: PENDING, COMPLETED, CANCELED, CANCEL_REQUESTED, REJECTED, REFUNDED...
 *
 * Why a separate table (instead of enum only):
 * - You can manage statuses in DB (seed/migrations) without recompiling.
 * - You can reference it as a FK from Order (status_id) for consistency.
 * - Easy to query by status name (you already do findByNameIgnoreCase).
 *
 * Notes:
 * - "name" is unique: each status code exists once.
 * - Keep "name" uppercase to stay consistent with your service logic.
 */
@Entity
@Table(name = "order_status")
public class OrderStatus {

    /** Primary key of the status row */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Status code (unique).
     * Use short stable codes because they are used in business rules and queries.
     *
     * Examples:
     * - PENDING
     * - COMPLETED
     * - CANCELED
     * - CANCEL_REQUESTED
     * - REJECTED
     * - REFUNDED
     */
    @Column(unique = true, nullable = false, length = 32)
    private String name;

    /** JPA requires a no-args constructor */
    public OrderStatus() {}

    /** Convenience constructor to quickly create a status instance */
    public OrderStatus(String name) {
        this.name = name;
    }

    /** Convenience constructor (sometimes used for DTO mapping / tests) */
    public OrderStatus(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    // --- Getters & Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
