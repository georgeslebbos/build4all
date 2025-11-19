package com.build4all.order.web;

import com.build4all.order.domain.OrderStatus;
import com.build4all.order.service.OrderStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/order-status")
public class OrderStatusController {

    private final OrderStatusService service;

    public OrderStatusController(OrderStatusService service) {
        this.service = service;
    }

    @Operation(summary = "Get all order statuses")
    @ApiResponse(responseCode = "200", description = "List of order statuses")
    @GetMapping
    public List<OrderStatus> getAll() {
        return service.findAll();
    }

    @Operation(summary = "Create an order status")
    @ApiResponse(responseCode = "201", description = "Order status created")
    @PostMapping
    public OrderStatus create(@RequestBody OrderStatus status) {
        return service.save(status);
    }

    @Operation(summary = "Update an order status")
    @ApiResponse(responseCode = "200", description = "Order status updated")
    @PutMapping("/{id}")
    public ResponseEntity<OrderStatus> update(@PathVariable Long id, @RequestBody OrderStatus status) {
        return service.findById(id)
                .map(existing -> {
                    existing.setName(status.getName());
                    return ResponseEntity.ok(service.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Delete an order status")
    @ApiResponse(responseCode = "204", description = "Order status deleted")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
