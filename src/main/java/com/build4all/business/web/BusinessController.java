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

    @Autowired private BusinessService businessService;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private BusinessStatusRepository statusRepo;

    /** Example: http://192.168.1.6:8080  or  https://your-domain.com */
    @Value("${app.base-domain}")
    private String baseDomain;

    /* -------------------- Auth helpers -------------------- */

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

    private Long ownerProjectLinkIdOrNull(Businesses b) {
        return (b != null && b.getOwnerProjectLink() != null) ? b.getOwnerProjectLink().getId() : null;
    }

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

        String jwt = authHeader.replace("Bearer ", "").trim();
        if (!(jwtUtil.isBusinessToken(jwt) || jwtUtil.isAdminToken(jwt) || jwtUtil.isOwnerToken(jwt) || jwtUtil.isUserToken(jwt))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Business/Admin/Owner/User token required"));
        }
        if (!isAuthorized(authHeader, id)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Access denied"));
        }
        Businesses b = businessService.findById(id);
        return (b != null) ? ResponseEntity.ok(b) : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Business not found"));
    }

    @Operation(summary = "Get all businesses (SUPER_ADMIN)")
    @GetMapping
    public ResponseEntity<?> getAll(@RequestHeader("Authorization") String authHeader) {
        String role = jwtUtil.extractRole(authHeader.replace("Bearer ", ""));
        if (!"SUPER_ADMIN".equals(role)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Only SUPER_ADMIN can access this");
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

        if (!isAuthorized(authHeader, id)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        body.setId(id);
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
            Businesses updated = (tenantHeader == null)
                ? businessService.updateBusinessWithImages(id, name, email, password, description, phoneNumber, websiteUrl, logo, banner)
                : businessService.updateBusinessWithImages(tenantHeader, id, name, email, password, description, phoneNumber, websiteUrl, logo, banner);
            return ResponseEntity.ok(updated);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("File upload error");
        } catch (IllegalArgumentException e) {
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
        Boolean makePublic = body.get("public");
        if (makePublic == null) return ResponseEntity.badRequest().body("Field 'public' is required (true|false)");

        Businesses b = businessService.findById(id);
        if (b == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Business not found");

        b.setIsPublicProfile(makePublic);
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

        String jwt = authHeader.replace("Bearer ", "").trim();
        if (!jwtUtil.isBusinessToken(jwt)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Business token required");
        if (!isAuthorized(authHeader, id)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");

        String pwd = body.get("password");
        if (pwd == null || pwd.isBlank()) return ResponseEntity.badRequest().body("Password is required");

        boolean ok = businessService.deleteBusinessByIdWithPassword(id, pwd);
        return ok ? ResponseEntity.ok(Map.of("message", "Business deleted successfully."))
                  : ResponseEntity.status(HttpStatus.FORBIDDEN).body("Incorrect password or business not found");
    }

    @DeleteMapping("/delete-logo/{id}")
    public ResponseEntity<?> deleteLogo(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
        String jwt = authHeader.replace("Bearer ", "").trim();
        if (!jwtUtil.isBusinessToken(jwt)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Business token required");
        boolean ok = businessService.deleteBusinessLogo(id);
        return ok ? ResponseEntity.ok("Logo deleted successfully.") : ResponseEntity.status(HttpStatus.NOT_FOUND).body("No logo found to delete.");
    }

    @DeleteMapping("/delete-banner/{id}")
    public ResponseEntity<?> deleteBanner(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
        String jwt = authHeader.replace("Bearer ", "").trim();
        if (!jwtUtil.isBusinessToken(jwt)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Business token required");
        boolean ok = businessService.deleteBusinessBanner(id);
        return ok ? ResponseEntity.ok("Banner deleted successfully.") : ResponseEntity.status(HttpStatus.NOT_FOUND).body("No banner found to delete.");
    }

    /* -------------------- Public list -------------------- */

    @Operation(summary = "Get public & ACTIVE businesses (supports tenant header)")
    @GetMapping("/public")
    public ResponseEntity<List<Businesses>> getPublicActive(
            @RequestHeader("Authorization") String authHeader,
            @RequestHeader(value = "X-Owner-Project-Link-Id", required = false) Long tenantHeader) {

        String jwt = authHeader.replace("Bearer ", "").trim();
        if (!(jwtUtil.isUserToken(jwt) || jwtUtil.isAdminToken(jwt))) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

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

        if (!isAuthorized(authHeader, id)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        String email = body.get("email");
        if (email == null || email.isBlank()) return ResponseEntity.badRequest().body("Email is required");

        Businesses b = businessService.findById(id);
        if (b == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Business not found");

        try {
            businessService.sendManagerInvite(email, b);
            return ResponseEntity.ok(Map.of("message", "Invitation sent successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to send invite: " + e.getMessage()));
        }
    }

    /* -------------------- Stripe -------------------- */

    private String cb(String path) {
        // ensures mobile devices can reach the callback (NO localhost)
        String root = (baseDomain != null) ? baseDomain.replaceAll("/+$", "") : "";
        return root + path;
    }

    @PostMapping({"/{id}/stripe/connect", "/stripe/connect"})
    public ResponseEntity<?> connectStripe(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable(name = "id", required = false) Long pathId,
            @RequestBody(required = false) Map<String, Object> body) {

        try {
            String jwt = authHeader.replace("Bearer ", "").trim();

            // determine target business id
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

            String accountId = b.getStripeAccountId();
            if (accountId == null || accountId.isBlank()) {
                Account account = Account.create(AccountCreateParams.builder().setType(AccountCreateParams.Type.STANDARD).build());
                accountId = account.getId();
                b.setStripeAccountId(accountId);
                tenantAwareSave(null, b);
            }

            AccountLink link = AccountLink.create(AccountLinkCreateParams.builder()
                    .setAccount(accountId)
                    .setRefreshUrl(cb("/stripe/refresh"))
                    .setReturnUrl(cb("/stripe/success"))   // <- use your real domain
                    .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                    .build());

            return ResponseEntity.ok(Map.of("url", link.getUrl(), "accountId", accountId));

        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Stripe error: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal error: " + e.getMessage()));
        }
    }

    @Operation(summary = "Check Stripe connection")
    @GetMapping("/{id}/stripe-status")
    public ResponseEntity<?> stripeStatus(@PathVariable Long id, @RequestHeader("Authorization") String authHeader) {
        if (!isAuthorized(authHeader, id)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));

        Businesses b = businessService.findById(id);
        if (b == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Business not found"));

        String acct = b.getStripeAccountId();
        if (acct == null || acct.isBlank()) {
            return ResponseEntity.ok(Map.of(
                    "stripeConnected", false,
                    "detailsSubmitted", false,
                    "chargesEnabled", false,
                    "stripeAccountId", null
            ));
        }

        try {
            Account account = Account.retrieve(acct);
            boolean charges = account.getChargesEnabled();
            boolean details = account.getDetailsSubmitted();
            return ResponseEntity.ok(Map.of(
                    "stripeConnected", charges && details,
                    "detailsSubmitted", details,
                    "chargesEnabled", charges,
                    "stripeAccountId", acct
            ));
        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Stripe error: " + e.getMessage()));
        }
    }

    @Operation(summary = "Resume Stripe onboarding")
    @PostMapping("/stripe/resume")
    public ResponseEntity<?> resumeStripe(@RequestHeader("Authorization") String authHeader) {
        try {
            String jwt = authHeader.replace("Bearer ", "");
            String email = jwtUtil.extractUsername(jwt);
            Businesses b = businessService.findByEmailOrThrow(email);

            if (b == null || b.getStripeAccountId() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "No Stripe account found for this business"));
            }

            AccountLink link = AccountLink.create(AccountLinkCreateParams.builder()
                    .setAccount(b.getStripeAccountId())
                    .setRefreshUrl(cb("/stripe/refresh"))
                    .setReturnUrl(cb("/stripe/success"))
                    .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                    .build());

            return ResponseEntity.ok(Map.of("url", link.getUrl()));
        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Stripe error: " + e.getMessage()));
        }
    }
}
