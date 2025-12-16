package com.build4all.order.service;

import com.build4all.order.domain.OrderStatus;
import com.build4all.order.repository.OrderStatusRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * OrderStatusService
 *
 * A small CRUD service around the OrderStatus lookup table.
 *
 * Why it exists:
 * - Order statuses (PENDING, COMPLETED, CANCELED, ...) are stored in a DB table (order_status)
 *   rather than being hard-coded as an enum.
 * - Other services (like OrderServiceImpl) often need to fetch a status entity by name/id.
 * - This service is typically used by:
 *   1) Admin/SuperAdmin screens (manage allowed statuses)
 *   2) Seed/init jobs (insert default statuses)
 *   3) API endpoints that list statuses for UI dropdowns
 *
 * Notes:
 * - This service currently exposes basic repository passthrough methods.
 * - In your OrderServiceImpl you already use OrderStatusRepository.findByNameIgnoreCase(...)
 *   via requireStatus(...). This class is complementary (mainly for CRUD/UI usage).
 */
@Service
public class OrderStatusService {

    /**
     * Repository for the order_status table.
     * Provides standard CRUD operations (findAll, findById, save, deleteById).
     */
    private final OrderStatusRepository repo;

    /**
     * Constructor injection (recommended in Spring).
     * Spring will auto-inject the OrderStatusRepository implementation.
     */
    public OrderStatusService(OrderStatusRepository repo) {
        this.repo = repo;
    }

    /**
     * Returns all statuses in the order_status table.
     *
     * Typical usage:
     * - UI dropdown: list possible statuses
     * - Admin page: show status list
     */
    public List<OrderStatus> findAll() {
        return repo.findAll();
    }

    /**
     * Finds a status by its primary key.
     *
     * @param id status id (PK)
     * @return Optional.empty() if not found
     */
    public Optional<OrderStatus> findById(Long id) {
        return repo.findById(id);
    }

    /**
     * Creates or updates a status row.
     *
     * Behavior:
     * - If status.id is null -> INSERT
     * - If status.id exists -> UPDATE
     *
     * Important:
     * - In your entity, "name" is unique. Saving a duplicate name will throw a DB constraint error.
     *
     * @param status the OrderStatus entity to save
     * @return saved entity (with generated id if inserted)
     */
    public OrderStatus save(OrderStatus status) {
        return repo.save(status);
    }

    /**
     * Deletes a status row by its id.
     *
     * Caution:
     * - If there are existing Orders referencing this status_id,
     *   the DB may reject deletion (FK constraint) unless you configured cascade/delete rules.
     * - In most systems, statuses are "static" and deletion is rare; often you "disable" instead.
     *
     * @param id status id
     */
    public void delete(Long id) {
        repo.deleteById(id);
    }
}
