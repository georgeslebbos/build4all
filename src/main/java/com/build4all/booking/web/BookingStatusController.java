package com.build4all.booking.web;

import com.build4all.booking.domain.BookingStatus;
import com.build4all.booking.service.BookingStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/booking-status")
public class BookingStatusController {

    private final BookingStatusService service;

    public BookingStatusController(BookingStatusService service) {
        this.service = service;
    }

    @Operation(summary = "Get all booking statuses")
    @ApiResponse(responseCode = "200", description = "List of booking statuses")
    @GetMapping
    public List<BookingStatus> getAll() {
        return service.findAll();
    }

    @Operation(summary = "Create a booking status")
    @ApiResponse(responseCode = "201", description = "Booking status created")
    @PostMapping
    public BookingStatus create(@RequestBody BookingStatus status) {
        return service.save(status);
    }

    @Operation(summary = "Update a booking status")
    @ApiResponse(responseCode = "200", description = "Booking status updated")
    @PutMapping("/{id}")
    public ResponseEntity<BookingStatus> update(@PathVariable Long id, @RequestBody BookingStatus status) {
        return service.findById(id)
                .map(existing -> {
                    existing.setName(status.getName());
                    return ResponseEntity.ok(service.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Delete a booking status")
    @ApiResponse(responseCode = "204", description = "Booking status deleted")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
