package com.build4all.business.web;

import com.build4all.business.domain.BusinessStatus;
import com.build4all.business.domain.Businesses;
import com.build4all.business.repository.BusinessStatusRepository;
import com.build4all.business.service.BusinessService;
import com.build4all.security.JwtUtil;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/businesses")
@Tag(name = "Business Management", description = "Operations for creating, reading, updating, and deleting businesses")
public class BusinessController {

    @Autowired private BusinessService businessService;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private BusinessStatusRepository businessStatusRepository;

    @Value("${app.base-domain}")
    private String baseDomain;

    private boolean isAuthorized(String authHeader, Long businessId) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return false;
        String jwt = authHeader.substring(7);
        String role = jwtUtil.extractRole(jwt);
        if ("SUPER_ADMIN".equals(role)) return true;

        String email = jwtUtil.extractUsername(jwt);
        Businesses me;
        try {
            me = businessService.findByEmailOrThrow(email); // legacy global lookup for compatibility
        } catch (Exception e) {
            return false;
        }
        return me != null && me.getId().equals(businessId);
    }

    @Operation(summary = "Get a business by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successful"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Not Found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<?> getBusinessById(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {

        String token = authHeader.replace("Bearer ", "").trim();
        if (!(jwtUtil.isBusinessToken(token) || jwtUtil.isAdminToken(token) || jwtUtil.isUserToken(token))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied: Business, Admin or User token required");
        }

        if (!isAuthorized(authHeader, id)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Access denied");
        Businesses business = businessService.findById(id);
        return business != null ? ResponseEntity.ok(business) : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @Operation(summary = "Get all businesses")
    @GetMapping
    public ResponseEntity<?> getAllBusinesses(@RequestHeader("Authorization") String authHeader) {
        String role = jwtUtil.extractRole(authHeader.replace("Bearer ", ""));
        if (!"SUPER_ADMIN".equals(role)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Only SUPER_ADMIN can access this");
        return ResponseEntity.ok(businessService.findAll());
    }

    @Operation(summary = "Update a business (supports tenant header)")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateBusiness(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            @RequestHeader(value = "X-Owner-Project-Link-Id", required = false) Long ownerProjectLinkId,
            @RequestBody Businesses business) {

        if (!isAuthorized(authHeader, id)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        business.setId(id);

        Businesses saved = (ownerProjectLinkId != null)
                ? businessService.save(ownerProjectLinkId, business)
                : businessService.save(business); // legacy
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBusinessWithPassword(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> request) {

        String token = authHeader.replace("Bearer ", "").trim();
        if (!jwtUtil.isBusinessToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied: Business token required");
        }
        if (!isAuthorized(authHeader, id)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");

        String password = request.get("password");
        if (password == null || password.isEmpty()) return ResponseEntity.badRequest().body("Password is required");

        boolean deleted = businessService.deleteBusinessByIdWithPassword(id, password);
        return deleted
                ? ResponseEntity.ok(Map.of("message", "Business deleted successfully."))
                : ResponseEntity.status(HttpStatus.FORBIDDEN).body("Incorrect password or business not found");
    }

    @Operation(summary = "Update business with images (supports tenant header)")
    @PutMapping("/update-with-images/{id}")
    public ResponseEntity<?> updateBusinessWithImages(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            @RequestHeader(value = "X-Owner-Project-Link-Id", required = false) Long ownerProjectLinkId,
            @RequestParam String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String password,
            @RequestParam String description,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) String websiteUrl,
            @RequestParam(required = false) MultipartFile logo,
            @RequestParam(required = false) MultipartFile banner) {

        if (!isAuthorized(authHeader, id)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        try {
            if (ownerProjectLinkId == null) {
                // legacy path (kept exactly as before)
                Businesses updated = businessService.updateBusinessWithImages(
                        id, name, email, password, description, phoneNumber, websiteUrl, logo, banner);
                return ResponseEntity.ok(updated);
            } else {
                // tenant-aware path
                Businesses updated = businessService.updateBusinessWithImages(
                        ownerProjectLinkId, id, name, email, password, description, phoneNumber, websiteUrl, logo, banner);
                return ResponseEntity.ok(updated);
            }
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("File upload error");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @Operation(summary = "Update logo and banner (legacy save kept)")
    @PutMapping("/update-logo-banner/{id}")
    public ResponseEntity<?> updateBusinessLogoAndBanner(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) MultipartFile logo,
            @RequestParam(required = false) MultipartFile banner) {

        if (!isAuthorized(authHeader, id)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        Businesses existing = businessService.findById(id);
        if (existing == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Business not found");
        if (logo != null && !logo.isEmpty()) existing.setBusinessLogoUrl(logo.getOriginalFilename());
        if (banner != null && !banner.isEmpty()) existing.setBusinessBannerUrl(banner.getOriginalFilename());
        return ResponseEntity.ok(businessService.save(existing)); // legacy save to avoid breaking old behavior
    }

    @Operation(summary = "Request password reset")
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> sendBusinessResetCode(@RequestBody Map<String, String> request) {
        boolean success = businessService.resetPassword(request.get("email"));
        return success
                ? ResponseEntity.ok(Map.of("message", "Reset code sent"))
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Business not found"));
    }

    @Operation(summary = "Verify reset code")
    @PostMapping("/verify-reset-code")
    public ResponseEntity<Map<String, String>> verifyBusinessResetCode(@RequestBody Map<String, String> request) {
        boolean verified = businessService.verifyResetCode(request.get("email"), request.get("code"));
        return verified
                ? ResponseEntity.ok(Map.of("message", "Code verified"))
                : ResponseEntity.badRequest().body(Map.of("message", "Invalid code"));
    }

    @Operation(summary = "Update business password")
    @PostMapping("/update-password")
    public ResponseEntity<Map<String, String>> updateBusinessPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String newPassword = request.get("newPassword");
        if (email == null || newPassword == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Missing email or password"));
        }
        boolean updated = businessService.updatePasswordDirectly(email, newPassword);
        return updated
                ? ResponseEntity.ok(Map.of("message", "Password updated"))
                : ResponseEntity.badRequest().body(Map.of("message", "Business not found"));
    }

    @DeleteMapping("/delete-logo/{id}")
    public ResponseEntity<?> deleteLogo(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "").trim();
        if (!jwtUtil.isBusinessToken(token))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied: Business token required");

        boolean deleted = businessService.deleteBusinessLogo(id);
        return deleted ? ResponseEntity.ok("Logo deleted successfully.")
                       : ResponseEntity.status(HttpStatus.NOT_FOUND).body("No logo found to delete.");
    }

    @DeleteMapping("/delete-banner/{id}")
    public ResponseEntity<?> deleteBanner(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "").trim();
        if (!jwtUtil.isBusinessToken(token))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied: Business token required");

        boolean deleted = businessService.deleteBusinessBanner(id);
        return deleted ? ResponseEntity.ok("Banner deleted successfully.")
                       : ResponseEntity.status(HttpStatus.NOT_FOUND).body("No banner found to delete.");
    }

    @Operation(summary = "Get public active businesses (supports tenant header)")
    @GetMapping("/public")
    public ResponseEntity<List<Businesses>> getPublicActiveBusinesses(
            @RequestHeader("Authorization") String authHeader,
            @RequestHeader(value = "X-Owner-Project-Link-Id", required = false) Long ownerProjectLinkId) {

        String token = authHeader.replace("Bearer ", "").trim();
        if (!(jwtUtil.isUserToken(token) || jwtUtil.isAdminToken(token))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<Businesses> businesses = (ownerProjectLinkId != null)
                ? businessService.getAllPublicActiveBusinesses(ownerProjectLinkId)
                : businessService.getAllPublicActiveBusinesses(); // legacy global
        return ResponseEntity.ok(businesses);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateBusinessStatus(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> request) {

        if (!isAuthorized(authHeader, id)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }

        String newStatus = request.get("status");
        String password = request.get("password");
        if (newStatus == null) {
            return ResponseEntity.badRequest().body("Missing status");
        }

        if ("INACTIVE".equalsIgnoreCase(newStatus)) {
            if (password == null || password.isBlank()) {
                return ResponseEntity.badRequest().body("Password is required to deactivate account.");
            }
            boolean ok = businessService.verifyPassword(id, password);
            if (!ok) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Incorrect password. Status not changed.");
        }

        try {
            Businesses business = businessService.findById(id);
            BusinessStatus statusEntity = businessStatusRepository.findByNameIgnoreCase(newStatus.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Invalid status value"));

            business.setStatus(statusEntity);
            // Use tenant-aware save when possible
            Long ownerProjectLinkId = business.getOwnerProjectLink() != null ? business.getOwnerProjectLink().getId() : null;
            Businesses saved = (ownerProjectLinkId != null)
                    ? businessService.save(ownerProjectLinkId, business)
                    : businessService.save(business); // fallback legacy
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid status value");
        }
    }

    @Operation(summary = "Send Manager Invitation Email")
    @PostMapping("/{id}/send-manager-invite")
    public ResponseEntity<?> sendManagerInvite(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> request) {

        if (!isAuthorized(authHeader, id)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");

        String email = request.get("email");
        if (email == null || email.isBlank()) return ResponseEntity.badRequest().body("Email is required");

        Businesses business = businessService.findById(id);
        if (business == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Business not found");

        try {
            businessService.sendManagerInvite(email, business);
            return ResponseEntity.ok(Map.of("message", "Invitation sent successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to send invite: " + e.getMessage()));
        }
    }

    /* -------- Stripe (kept legacy behavior) -------- */

    @PostMapping("/stripe/connect")
    public ResponseEntity<?> connectStripeAccount(@RequestHeader("Authorization") String token) {
        try {
            String jwt = token.replace("Bearer ", "");
            String email = jwtUtil.extractUsername(jwt);
            Businesses business = businessService.findByEmailOrThrow(email);
            if (business == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Business not found"));
            }

            String accountId = business.getStripeAccountId();
            if (accountId == null) {
                Account account = Account.create(AccountCreateParams.builder()
                        .setType(AccountCreateParams.Type.STANDARD).build());
                accountId = account.getId();
                business.setStripeAccountId(accountId);

                Long ownerProjectLinkId = business.getOwnerProjectLink() != null ? business.getOwnerProjectLink().getId() : null;
                if (ownerProjectLinkId != null) {
                    businessService.save(ownerProjectLinkId, business);
                } else {
                    businessService.save(business); // legacy fallback
                }
            }

            AccountLink link = AccountLink.create(AccountLinkCreateParams.builder()
                    .setAccount(accountId)
                    .setRefreshUrl("https://localhost:8080/stripe/refresh")
                    .setReturnUrl("https://localhost:8080/stripe/success")
                    .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                    .build());

            return ResponseEntity.ok(Map.of("url", link.getUrl(), "accountId", accountId));

        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Stripe error: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal error: " + e.getMessage()));
        }
    }

    @Operation(summary = "Check if business has connected Stripe account")
    @GetMapping("/{id}/stripe-status")
    public ResponseEntity<?> checkStripeConnection(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
        if (!isAuthorized(authHeader, id)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        Businesses business = businessService.findById(id);
        if (business == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Business not found"));
        }

        String stripeAccountId = business.getStripeAccountId();
        if (stripeAccountId == null || stripeAccountId.isBlank()) {
            Map<String, Object> response = new HashMap<>();
            response.put("stripeConnected", false);
            response.put("detailsSubmitted", false);
            response.put("chargesEnabled", false);
            response.put("stripeAccountId", null);
            return ResponseEntity.ok(response);
        }

        try {
            Account account = Account.retrieve(stripeAccountId);
            boolean chargesEnabled = account.getChargesEnabled();
            boolean detailsSubmitted = account.getDetailsSubmitted();

            return ResponseEntity.ok(Map.of(
                "stripeConnected", chargesEnabled && detailsSubmitted,
                "detailsSubmitted", detailsSubmitted,
                "chargesEnabled", chargesEnabled,
                "stripeAccountId", stripeAccountId
            ));
        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Stripe error: " + e.getMessage()));
        }
    }

    @Operation(summary = "Resume Stripe onboarding for incomplete account")
    @PostMapping("/stripe/resume")
    public ResponseEntity<?> resumeStripeOnboarding(@RequestHeader("Authorization") String token) {
        try {
            String jwt = token.replace("Bearer ", "");
            String email = jwtUtil.extractUsername(jwt);
            Businesses business = businessService.findByEmailOrThrow(email);

            if (business == null || business.getStripeAccountId() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "No Stripe account found for this business"));
            }

            AccountLink link = AccountLink.create(AccountLinkCreateParams.builder()
                    .setAccount(business.getStripeAccountId())
                    .setRefreshUrl("https://localhost:8080/stripe/refresh")
                    .setReturnUrl("https://localhost:8080/stripe/success")
                    .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                    .build());

            return ResponseEntity.ok(Map.of("url", link.getUrl()));

        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Stripe error: " + e.getMessage()));
        }
    }
}
