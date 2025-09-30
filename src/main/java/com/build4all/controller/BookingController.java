package com.build4all.controller;

import com.build4all.entities.ItemBooking;
import com.build4all.security.JwtUtil;
import com.build4all.services.ItemBookingService;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final ItemBookingService bookingService;
    private final JwtUtil jwt;

    public BookingController(ItemBookingService bookingService, JwtUtil jwt) {
        this.bookingService = bookingService;
        this.jwt = jwt;
    }

    private String strip(String auth) {
        if (auth == null) return "";
        return auth.replace("Bearer ", "").trim();
    }

    // My bookings
    @GetMapping("/mybookings")
    @Operation(summary = "List all my bookings")
    public ResponseEntity<?> myBookings(@RequestHeader("Authorization") String auth) {
        Long userId = jwt.extractId(strip(auth));
        List<ItemBooking> list = bookingService.getMyBookings(userId);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/mybookings/pending")
    public ResponseEntity<?> myPending(@RequestHeader("Authorization") String auth) {
        Long userId = jwt.extractId(strip(auth));
        return ResponseEntity.ok(bookingService.getMyBookingsByStatus(userId, "Pending"));
    }

    @GetMapping("/mybookings/completed")
    public ResponseEntity<?> myCompleted(@RequestHeader("Authorization") String auth) {
        Long userId = jwt.extractId(strip(auth));
        return ResponseEntity.ok(bookingService.getMyBookingsByStatus(userId, "Completed"));
    }

    @GetMapping("/mybookings/canceled")
    public ResponseEntity<?> myCanceled(@RequestHeader("Authorization") String auth) {
        Long userId = jwt.extractId(strip(auth));
        return ResponseEntity.ok(bookingService.getMyBookingsByStatus(userId, "Canceled"));
    }

    // Actions
    @PutMapping("/cancel/{bookingId}")
    public ResponseEntity<?> cancel(@RequestHeader("Authorization") String auth, @PathVariable Long bookingId) {
        Long actorId = jwt.extractId(strip(auth));
        bookingService.cancelBooking(bookingId, actorId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/pending/{bookingId}")
    public ResponseEntity<?> resetToPending(@RequestHeader("Authorization") String auth, @PathVariable Long bookingId) {
        Long actorId = jwt.extractId(strip(auth));
        bookingService.resetToPending(bookingId, actorId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/delete/{bookingId}")
    public ResponseEntity<?> delete(@RequestHeader("Authorization") String auth, @PathVariable Long bookingId) {
        Long actorId = jwt.extractId(strip(auth));
        bookingService.deleteBooking(bookingId, actorId);
        return ResponseEntity.noContent().build();
    }

    // Optional flows
    @PutMapping("/refund/{bookingId}")
    public ResponseEntity<?> refund(@RequestHeader("Authorization") String auth, @PathVariable Long bookingId) {
        Long actorId = jwt.extractId(strip(auth));
        bookingService.refundIfEligible(bookingId, actorId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/cancel/request/{bookingId}")
    public ResponseEntity<?> requestCancel(@RequestHeader("Authorization") String auth, @PathVariable Long bookingId) {
        Long userId = jwt.extractId(strip(auth));
        bookingService.requestCancel(bookingId, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/cancel/approve/{bookingId}")
    public ResponseEntity<?> approveCancel(@RequestHeader("Authorization") String auth, @PathVariable Long bookingId) {
        Long businessId = jwt.extractBusinessId(auth);
        bookingService.approveCancel(bookingId, businessId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/cancel/reject/{bookingId}")
    public ResponseEntity<?> rejectCancel(@RequestHeader("Authorization") String auth, @PathVariable Long bookingId) {
        Long businessId = jwt.extractBusinessId(auth);
        bookingService.rejectCancel(bookingId, businessId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/cancel/mark-refunded/{bookingId}")
    public ResponseEntity<?> markRefunded(@RequestHeader("Authorization") String auth, @PathVariable Long bookingId) {
        Long businessId = jwt.extractBusinessId(auth);
        bookingService.markRefunded(bookingId, businessId);
        return ResponseEntity.ok().build();
    }

    // Business insights
    @GetMapping("/mybusinessbookings")
    public ResponseEntity<?> myBusinessBookings(@RequestHeader("Authorization") String auth) {
        Long businessId = jwt.extractBusinessId(auth);
        return ResponseEntity.ok(bookingService.getBookingsByBusiness(businessId));
    }

    @PutMapping("/mark-paid/{bookingId}")
    public ResponseEntity<?> markPaid(@RequestHeader("Authorization") String auth, @PathVariable Long bookingId) {
        Long businessId = jwt.extractBusinessId(auth);
        bookingService.markPaid(bookingId, businessId);
        return ResponseEntity.ok().build();
    }

    // Errors
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(java.util.Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> serverError(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(java.util.Map.of("error", ex.getMessage()));
    }
}
