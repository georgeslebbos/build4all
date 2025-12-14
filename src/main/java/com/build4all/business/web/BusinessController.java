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
@Tag(name = "Business Management")
public class BusinessController {

    @Autowired private BusinessService businessService; // Business domain service (CRUD + registration + manager invites + etc.)
    @Autowired private JwtUtil jwtUtil;                 // JWT helper (extract role, token type, businessId, etc.)
    @Autowired private BusinessStatusRepository statusRepo; // Lookup business statuses (ACTIVE/INACTIVE/DELETED...)

    /** Example: http://192.168.1.6:8080  or  https://your-domain.com */
    @Value("${app.base-domain}")
    private String baseDomain; // Used to build Stripe callback URLs that are reachable from mobile/web (avoid localhost)

    /* -------------------- Auth helpers -------------------- */

    /**
     * Authorization rules for business resources:
     * - SUPER_ADMIN / OWNER: can access any business
     * - BUSINESS token: can only access its own business (id must match token businessId)
     */
    private boolean isAuthorized(String authHeader, Long bizId) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return false;
        String jwt = authHeader.substring(7).trim();

        if (jwtUtil.isSuperAdmin(jwt) || jwtUtil.isOwnerToken(jwt)) return true; // global access
        if (jwtUtil.isBusinessToken(jwt)) {
            Long tokenBizId = jwtUtil.extractBusinessId(jwt);
            return tokenBizId != null && tokenBizId.equals(bizId);
        }
        return false;
    }

    /**
     * Extract tenant id from entity when the caller didn't pass the tenant header.
     * If business is not tenant-linked, returns null and controller falls back to legacy save().
     */
    private Long ownerProjectLinkIdOrNull(Businesses b) {
        return (b != null && b.getOwnerProjectLink() != null) ? b.getOwnerProjectLink().getId() : null;
    }

    /**
     * Centralized "save" behavior:
     * - If tenantHeader is present -> tenant-aware save(ownerProjectLinkId, business)
     * - Else, if entity has ownerProjectLink -> tenant-aware save using that id
     * - Else -> legacy global save(business)
     */
    private Businesses tenantAwareSave(Long tenantHeader, Businesses b) {
        Long tenant = (tenantHeader != null) ? tenantHeader : ownerProjectLinkIdOrNull(b);
        return (tenant != null) ? businessService.save(tenant, b) : businessService.save(b);
    }

    /* -------------------- Read -------------------- */

    @Operation(summary = "Get a business by ID")
    @GetMapping("/{id}")
    public ResponseEntity<?> getBusinessById(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {

        // Token-type gate: you decided these token types can call this endpoint
        String jwt = authHeader.replace("Bearer ", "").trim();
        if (!(jwtUtil.isBusinessToken(jwt) || jwtUtil.isAdminToken(jwt) || jwtUtil.isOwnerToken(jwt) || jwtUtil.isUserToken(jwt))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Business/Admin/Owner/User token required"));
        }

        // Ownership/identity gate (business token must match business id; owners/super admins pass)
        if (!isAuthorized(authHeader, id)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Access denied"));
        }

        Businesses b = businessService.findById(id);
        return (b != null)
                ? ResponseEntity.ok(b)
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Business not found"));
    }

    @Operation(summary = "Get all businesses (SUPER_ADMIN)")
    @GetMapping
    public ResponseEntity<?> getAll(@RequestHeader("Authorization") String authHeader) {
        // NOTE: this uses "role" claim; make sure extractRole() always returns consistent values
        String role = jwtUtil.extractRole(authHeader.replace("Bearer ", ""));
        if (!"SUPER_ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Only SUPER_ADMIN can access this");
        }
        return ResponseEntity.ok(businessService.findAll());
    }

    /* -------------------- Update (full) -------------------- */

    @Operation(summary = "Update a business (supports tenant header)")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateBusiness(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            @RequestHeader(value = "X-Owner-Project-Link-Id", required = false) Long tenantHeader,
            @RequestBody Businesses body) {

        // Auth: only owner/superadmin or the business itself
        if (!isAuthorized(authHeader, id)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");

        // Ensure path id is the source of truth
        body.setId(id);

        // Save with tenant scoping when possible
        return ResponseEntity.ok(tenantAwareSave(tenantHeader, body));
    }

    @Operation(summary = "Update business with images (supports tenant header)")
    @PutMapping("/update-with-images/{id}")
    public ResponseEntity<?> updateBusinessWithImages(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            @RequestHeader(value = "X-Owner-Project-Link-Id", required = false) Long tenantHeader,
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
            // If tenantHeader exists, use tenant-aware overload, else use legacy overload
            Businesses updated = (tenantHeader == null)
                    ? businessService.updateBusinessWithImages(id, name, email, password, description, phoneNumber, websiteUrl, logo, banner)
                    : businessService.updateBusinessWithImages(tenantHeader, id, name, email, password, description, phoneNumber, websiteUrl, logo, banner);

            return ResponseEntity.ok(updated);

        } catch (IOException e) {
            // File IO problems (uploads directory, permissions, etc.)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("File upload error");
        } catch (IllegalArgumentException e) {
            // Validation failures from service layer (email exists, password too short, etc.)
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /* -------------------- Visibility & Status -------------------- */

    @Operation(summary = "Toggle public profile visibility")
    @PatchMapping("/{id}/visibility")
    public ResponseEntity<?> updateVisibility(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            @RequestHeader(value = "X-Owner-Project-Link-Id", required = false) Long tenantHeader,
            @RequestBody Map<String, Boolean> body) {

        if (!isAuthorized(authHeader, id)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");

        // Expect JSON payload: { "public": true|false }
        Boolean makePublic = body.get("public");
        if (makePublic == null) return ResponseEntity.badRequest().body("Field 'public' is required (true|false)");

        Businesses b = businessService.findById(id);
        if (b == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Business not found");

        b.setIsPublicProfile(makePublic);

        // Save tenant-aware (scoped uniqueness checks)
        return ResponseEntity.ok(tenantAwareSave(tenantHeader, b));
    }

    @Operation(summary = "Update business status (ACTIVE/INACTIVE)")
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateBusinessStatus(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            @RequestHeader(value = "X-Owner-Project-Link-Id", required = false) Long tenantHeader,
            @RequestBody Map<String, String> body) {

        if (!isAuthorized(authHeader, id)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");

        String newStatus = body.get("status");
        String password  = body.get("password");
        if (newStatus == null) return ResponseEntity.badRequest().body("Missing status");

        // Security rule: require password confirmation when deactivating
        if ("INACTIVE".equalsIgnoreCase(newStatus)) {
            if (password == null || password.isBlank()) {
                return ResponseEntity.badRequest().body("Password is required to deactivate account.");
            }
            if (!businessService.verifyPassword(id, password)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Incorrect password. Status not changed.");
            }
        }

        Businesses b = businessService.findById(id);
        if (b == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Business not found");

        // Lookup status entity (throws if invalid)
        BusinessStatus status = statusRepo.findByNameIgnoreCase(newStatus.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Invalid status value"));
        b.setStatus(status);

        return ResponseEntity.ok(tenantAwareSave(tenantHeader, b));
    }

    /* -------------------- Delete & assets -------------------- */

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteWithPassword(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {

        // Only BUSINESS token can self-delete (as per your current policy)
        String jwt = authHeader.replace("Bearer ", "").trim();
        if (!jwtUtil.isBusinessToken(jwt)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Business token required");
        }
        if (!isAuthorized(authHeader, id)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");

        String pwd = body.get("password");
        if (pwd == null || pwd.isBlank()) return ResponseEntity.badRequest().body("Password is required");

        boolean ok = businessService.deleteBusinessByIdWithPassword(id, pwd);
        return ok
                ? ResponseEntity.ok(Map.of("message", "Business deleted successfully."))
                : ResponseEntity.status(HttpStatus.FORBIDDEN).body("Incorrect password or business not found");
    }

    @DeleteMapping("/delete-logo/{id}")
    public ResponseEntity<?> deleteLogo(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
        // Access restriction: BUSINESS token required (your policy)
        String jwt = authHeader.replace("Bearer ", "").trim();
        if (!jwtUtil.isBusinessToken(jwt)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Business token required");

        boolean ok = businessService.deleteBusinessLogo(id);
        return ok
                ? ResponseEntity.ok("Logo deleted successfully.")
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body("No logo found to delete.");
    }

    @DeleteMapping("/delete-banner/{id}")
    public ResponseEntity<?> deleteBanner(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
        // Access restriction: BUSINESS token required (your policy)
        String jwt = authHeader.replace("Bearer ", "").trim();
        if (!jwtUtil.isBusinessToken(jwt)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Business token required");

        boolean ok = businessService.deleteBusinessBanner(id);
        return ok
                ? ResponseEntity.ok("Banner deleted successfully.")
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body("No banner found to delete.");
    }

    /* -------------------- Public list -------------------- */

    @Operation(summary = "Get public & ACTIVE businesses (supports tenant header)")
    @GetMapping("/public")
    public ResponseEntity<List<Businesses>> getPublicActive(
            @RequestHeader("Authorization") String authHeader,
            @RequestHeader(value = "X-Owner-Project-Link-Id", required = false) Long tenantHeader) {

        String jwt = authHeader.replace("Bearer ", "").trim();

        // Only User/Admin tokens can browse public businesses (as per your policy)
        if (!(jwtUtil.isUserToken(jwt) || jwtUtil.isAdminToken(jwt))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Tenant-scoped list if tenantHeader exists; otherwise global list
        List<Businesses> data = (tenantHeader != null)
                ? businessService.getAllPublicActiveBusinesses(tenantHeader)
                : businessService.getAllPublicActiveBusinesses();

        return ResponseEntity.ok(data);
    }

    /* -------------------- Manager invite (unchanged semantics) -------------------- */

    @PostMapping("/{id}/send-manager-invite")
    public ResponseEntity<?> sendManagerInvite(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {

        // Owner/SuperAdmin or the business itself can invite a manager
        if (!isAuthorized(authHeader, id)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");

        String email = body.get("email");
        if (email == null || email.isBlank()) return ResponseEntity.badRequest().body("Email is required");

        Businesses b = businessService.findById(id);
        if (b == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Business not found");

        try {
            // Creates PendingManager + sends email with registration link
            businessService.sendManagerInvite(email, b);
            return ResponseEntity.ok(Map.of("message", "Invitation sent successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to send invite: " + e.getMessage()));
        }
    }

    /* -------------------- Stripe -------------------- */

    /**
     * Build callback URL using baseDomain (must be reachable by Stripe + user device).
     * Important: avoid localhost in production/mobile.
     */
    private String cb(String path) {
        // ensures mobile devices can reach the callback (NO localhost)
        String root = (baseDomain != null) ? baseDomain.replaceAll("/+$", "") : "";
        return root + path;
    }

    /**
     * Create Stripe Connect account (if missing) and generate an onboarding link.
     *
     * Supported routes:
     * - POST /api/businesses/{id}/stripe/connect        (owner/admin passes {id})
     * - POST /api/businesses/stripe/connect            (owner/admin passes businessId in body)
     * - BUSINESS token can call without id/body; it uses token businessId.
     */
    @PostMapping({"/{id}/stripe/connect", "/stripe/connect"})
    public ResponseEntity<?> connectStripe(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable(name = "id", required = false) Long pathId,
            @RequestBody(required = false) Map<String, Object> body) {

        try {
            String jwt = authHeader.replace("Bearer ", "").trim();

            // Determine target business id:
            // - BUSINESS token: target = businessId from token (self)
            // - OWNER/ADMIN: target from pathId or body.businessId
            Long targetId = null;
            if (jwtUtil.isBusinessToken(jwt)) {
                targetId = jwtUtil.extractBusinessId(jwt);      // business self
            } else if (jwtUtil.isOwnerToken(jwt) || jwtUtil.isAdminToken(jwt)) {
                if (pathId != null) targetId = pathId;          // from path
                if (targetId == null && body != null && body.get("businessId") != null) {
                    targetId = Long.valueOf(body.get("businessId").toString());
                }
            }

            if (targetId == null) return ResponseEntity.badRequest().body(Map.of("error", "businessId is required"));
            if (!isAuthorized(authHeader, targetId)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));

            Businesses b = businessService.findById(targetId);
            if (b == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Business not found"));

            // Create stripe account if not yet connected
            String accountId = b.getStripeAccountId();
            if (accountId == null || accountId.isBlank()) {
                Account account = Account.create(AccountCreateParams.builder()
                        .setType(AccountCreateParams.Type.STANDARD) // Stripe-hosted onboarding (simpler)
                        .build());

                accountId = account.getId();
                b.setStripeAccountId(accountId);

                // Persist stripeAccountId (tenant-aware when possible)
                tenantAwareSave(null, b);
            }

            // Create onboarding link
            AccountLink link = AccountLink.create(AccountLinkCreateParams.builder()
                    .setAccount(accountId)
                    .setRefreshUrl(cb("/stripe/refresh")) // Stripe will redirect here if user refreshes/abandons
                    .setReturnUrl(cb("/stripe/success"))  // Stripe redirects here after completion
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

    @Operation(summary = "Check Stripe connection")
    @GetMapping("/{id}/stripe-status")
    public ResponseEntity<?> stripeStatus(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
        // Business owner (or admin/superadmin) can check stripe status
        if (!isAuthorized(authHeader, id)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized"));
        }

        Businesses b = businessService.findById(id);
        if (b == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Business not found"));
        }

        // If there is no stripe account id -> not connected
        String acct = b.getStripeAccountId();
        if (acct == null || acct.isBlank()) {
            // Use a mutable map that tolerates null values
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("stripeConnected", false);
            body.put("detailsSubmitted", false);
            body.put("chargesEnabled", false);
            body.put("stripeAccountId", null); // allowed in LinkedHashMap
            return ResponseEntity.ok(body);
        }

        try {
            // Retrieve account to check onboarding state
            Account account = Account.retrieve(acct);
            boolean charges = account.getChargesEnabled();
            boolean details = account.getDetailsSubmitted();

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("stripeConnected", charges && details); // simplified "ready" rule
            body.put("detailsSubmitted", details);
            body.put("chargesEnabled", charges);
            body.put("stripeAccountId", acct);
            return ResponseEntity.ok(body);

        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Stripe error: " + e.getMessage()));
        }
    }

    @Operation(summary = "Resume Stripe onboarding")
    @PostMapping("/stripe/resume")
    public ResponseEntity<?> resumeStripe(@RequestHeader("Authorization") String authHeader) {
        try {
            // NOTE: You used replace("Bearer ", "") (without trim in original).
            // Keeping same behavior but trimming makes it safer.
            String jwt = authHeader.replace("Bearer ", "").trim();

            // This depends on how business login stores subject:
            // If subject is email, this works. If it's phone, this endpoint may fail.
            String email = jwtUtil.extractUsername(jwt);

            // Legacy finder (global) used here: consider tenant-aware version if needed
            Businesses b = businessService.findByEmailOrThrow(email);

            if (b.getStripeAccountId() == null || b.getStripeAccountId().isBlank()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "No Stripe account found for this business"));
            }

            // Create a new onboarding link for the same account
            AccountLink link = AccountLink.create(AccountLinkCreateParams.builder()
                    .setAccount(b.getStripeAccountId())
                    .setRefreshUrl(cb("/stripe/refresh"))
                    .setReturnUrl(cb("/stripe/success"))
                    .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                    .build());

            return ResponseEntity.ok(Map.of("url", link.getUrl()));

        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Stripe error: " + e.getMessage()));
        }
    }
}
