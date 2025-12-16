package com.build4all.order.web;

import com.build4all.order.domain.OrderStatus;
import com.build4all.order.service.OrderStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * OrderStatusController
 *
 * Simple CRUD controller for managing OrderStatus reference data.
 *
 * Why this exists:
 * - Order statuses are usually “master data” (PENDING, COMPLETED, CANCELED, ...)
 * - The OrderService relies on these rows to exist in DB (ex: requireStatus("PENDING"))
 * - This controller allows admin/superadmin tools (or seeding scripts) to create/update/remove statuses.
 *
 * Endpoints:
 * - GET    /api/order-status        -> list all statuses
 * - POST   /api/order-status        -> create a new status
 * - PUT    /api/order-status/{id}   -> update an existing status name
 * - DELETE /api/order-status/{id}   -> delete a status
 *
 * Notes / Best practice suggestions:
 * - In production, you typically restrict these endpoints (SUPER_ADMIN only)
 * - You may also prevent deletion of “system” statuses if orders already reference them
 *   (otherwise you risk FK constraint errors or orphaned data).
 */
@RestController
@RequestMapping("/api/order-status")
public class OrderStatusController {

    /** Service layer handling persistence and business rules for statuses */
    private final OrderStatusService service;

    public OrderStatusController(OrderStatusService service) {
        this.service = service;
    }

    /**
     * GET /api/order-status
     * Returns all OrderStatus rows.
     *
     * Used by:
     * - Admin dashboards
     * - Dropdowns / filters
     * - Debugging / verifying seed data
     */
    @Operation(summary = "Get all order statuses")
    @ApiResponse(responseCode = "200", description = "List of order statuses")
    @GetMapping
    public List<OrderStatus> getAll() {
        return service.findAll();
    }

    /**
     * POST /api/order-status
     * Creates a new OrderStatus row.
     *
     * Example payload:
     * { "name": "PENDING" }
     *
     * Important:
     * - Your code frequently does findByNameIgnoreCase("PENDING"),
     *   so keep names consistent and unique.
     */
    @Operation(summary = "Create an order status")
    @ApiResponse(responseCode = "201", description = "Order status created")
    @PostMapping
    public OrderStatus create(@RequestBody OrderStatus status) {
        return service.save(status);
    }

    /**
     * PUT /api/order-status/{id}
     * Updates an existing OrderStatus name.
     *
     * If id not found -> 404.
     *
     * Note:
     * - Only updates "name" (minimal safe update).
     * - If you later add fields (description, color, active...), extend here.
     */
    @Operation(summary = "Update an order status")
    @ApiResponse(responseCode = "200", description = "Order status updated")
    @PutMapping("/{id}")
    public ResponseEntity<OrderStatus> update(@PathVariable Long id, @RequestBody OrderStatus status) {
        return service.findById(id)
                .map(existing -> {
                    // Update only the fields we allow to change
                    existing.setName(status.getName());
                    return ResponseEntity.ok(service.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * DELETE /api/order-status/{id}
     * Deletes an OrderStatus row.
     *
     * Warning:
     * - If orders reference this status, deletion can fail due to FK constraints.
     * - Many systems prefer "soft delete" (active=false) instead of hard delete.
     */
    @Operation(summary = "Delete an order status")
    @ApiResponse(responseCode = "204", description = "Order status deleted")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
