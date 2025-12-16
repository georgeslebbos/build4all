package com.build4all.order.repository;

import com.build4all.order.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * OrderStatusRepository
 *
 * Repository for the lookup table "order_status".
 *
 * In your design:
 * - orders.status_id is a FK to order_status.id
 * - order_status.name stores a stable code like:
 *   "PENDING", "COMPLETED", "CANCELED", "CANCEL_REQUESTED", "REFUNDED", "REJECTED", ...
 *
 * Why this repository exists:
 * - To fetch the status entity by its code (name)
 * - To validate a status code exists (useful at startup / seeders / admin screens)
 *
 * Why IgnoreCase:
 * - Your code often normalizes to upper-case, but IgnoreCase makes it resilient
 *   if the DB contains mixed case or the caller sends "pending" vs "PENDING".
 */
public interface OrderStatusRepository extends JpaRepository<OrderStatus, Long> {

    /**
     * Returns true if a status row exists with the given code, case-insensitive.
     *
     * Example:
     * - existsByNameIgnoreCase("pending") -> true (if "PENDING" is stored)
     *
     * Typical usage:
     * - Data seeding checks (insert only if missing)
     * - Validation before allowing an admin to reference a status
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Fetch a status entity by its code (name), case-insensitive.
     *
     * Example:
     * - findByNameIgnoreCase("COMPLETED") -> OrderStatus entity
     *
     * Typical usage:
     * - In OrderServiceImpl.requireStatus("PENDING")
     * - Any place you want to assign header.setStatus(statusEntity)
     *
     * Returns Optional because the status might not exist if DB not seeded.
     */
    Optional<OrderStatus> findByNameIgnoreCase(String name);
}
