package com.build4all.review.web;

import com.build4all.review.dto.ReviewDTO;
import com.build4all.review.domain.Review;
import com.build4all.review.service.ReviewService;
import com.build4all.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@Tag(name = "Reviews API", description = "Endpoints for managing customer reviews")
public class ReviewController {

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private JwtUtil jwtUtil;

    private boolean isAuthorized(String token) {
        if (token == null || !token.startsWith("Bearer ")) return false;
        String jwt = token.substring(7);
        String role = jwtUtil.extractRole(jwt);
        return "BUSINESS".equals(role) || "SUPER_ADMIN".equals(role) || "MANAGER".equals(role);
    }

    @Operation(summary = "Get all reviews")
    @GetMapping
    public ResponseEntity<?> getAllReviews(@RequestHeader("Authorization") String token) {
        if (!isAuthorized(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Access denied.");
        }

        List<Review> reviews = reviewService.getAllReviews();
        return ResponseEntity.ok(reviews != null ? reviews : Collections.emptyList());
    }

    @Operation(summary = "Get reviews by item ID")
    @GetMapping("/item/{itemId}")
    public ResponseEntity<?> getReviewsByItem(
            @RequestHeader("Authorization") String token,
            @PathVariable Long itemId) {

        String jwt = token.substring(7);
        String role = jwtUtil.extractRole(jwt);

        if (!isAuthorized(token) && !"USER".equals(role)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Access denied.");
        }

        try {
            List<Review> reviews = reviewService.getReviewsByItem(itemId);
            return ResponseEntity.ok(reviews != null ? reviews : Collections.emptyList());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Something went wrong.");
        }
    }

    @Operation(summary = "Add a review")
    @PostMapping("/addreviews")
    public ResponseEntity<?> addReview(
            @RequestHeader("Authorization") String token,
            @RequestBody ReviewDTO dto) {

        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Missing or invalid token.");
        }

        String jwt = token.substring(7);
        String role = jwtUtil.extractRole(jwt);

        if (!"USER".equals(role)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Only users can submit reviews.");
        }

        try {
            Review review = reviewService.createReviewFromDTO(dto, token);
            return new ResponseEntity<>(review, HttpStatus.CREATED);
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.toLowerCase().contains("at least")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(msg);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all reviews for a business")
    @GetMapping("/business/{businessId}")
    public ResponseEntity<?> getReviewsByBusiness(
            @RequestHeader("Authorization") String token,
            @PathVariable Long businessId) {

        if (!isAuthorized(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Access denied.");
        }

        List<Review> reviews = reviewService.getReviewsByBusiness(businessId);
        return ResponseEntity.ok(reviews != null ? reviews : Collections.emptyList());
    }

    @GetMapping("/check-completed/{itemId}")
    public ResponseEntity<?> hasCompletedItem(
            @RequestHeader("Authorization") String token,
            @PathVariable Long itemId) {
        try {
            boolean hasCompleted = reviewService.hasUserCompletedItem(itemId, token);
            return ResponseEntity.ok(hasCompleted);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

    @GetMapping("/completed-activities")
    public ResponseEntity<?> getCompletedActivitiesForUser(@RequestHeader("Authorization") String token) {
        try {
            List<Long> itemIds = reviewService.getCompletedItemIdsForUser(token);
            return ResponseEntity.ok(itemIds != null ? itemIds : Collections.emptyList());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

    @GetMapping("/should-show-modal/{itemId}")
    public ResponseEntity<?> shouldShowReviewModal(
            @RequestHeader("Authorization") String token,
            @PathVariable Long itemId) {
        try {
            boolean result = reviewService.shouldShowReviewModal(itemId, token);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

    @GetMapping("/suggest")
    public ResponseEntity<?> suggestReviewItem(@RequestHeader("Authorization") String token) {
        try {
            Long itemId = reviewService.getFirstCompletedUnreviewedItem(token);
            return ResponseEntity.ok(itemId); // ✅ 200 OK with null if nothing found
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/business/{businessId}/check-rating")
    public ResponseEntity<?> checkRatingAndNotifyAdmins(
            @PathVariable Long businessId,
            @RequestHeader("Authorization") String token) {

        if (!token.startsWith("Bearer ") || !jwtUtil.extractRole(token.substring(7)).equals("SUPER_ADMIN")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }

        double avgRating = reviewService.checkAndNotifyIfLowRating(businessId);

        return ResponseEntity.ok(
                avgRating == -1
                        ? "No reviews yet."
                        : "Average rating: " + avgRating
        );
    }
}
