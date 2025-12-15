package com.build4all.payment.web;

import com.build4all.payment.domain.PaymentMethod;
import com.build4all.payment.repository.PaymentMethodRepository;
import com.build4all.security.JwtUtil;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/superadmin/payment-methods")
// This controller is for PLATFORM administration (global level).
//
// WooCommerce analogy:
// - These are the "installed payment plugins" available on the platform.
// - A SUPER_ADMIN can add/remove/enable gateways globally.
// - Owners still need to enable/configure them per project using OwnerPaymentConfigController.
//
// Example:
// If platformEnabled STRIPE=true, then owners can configure Stripe for their stores.
// If platformEnabled PAYPAL=false, then PayPal will not be available to owners.
public class PaymentMethodPlatformController {

    private final PaymentMethodRepository repo;
    private final JwtUtil jwtUtil;

    public PaymentMethodPlatformController(PaymentMethodRepository repo, JwtUtil jwtUtil) {
        this.repo = repo;
        this.jwtUtil = jwtUtil;
    }

    // Remove "Bearer " prefix from Authorization header
    private String strip(String auth) {
        return auth == null ? "" : auth.replace("Bearer ", "").trim();
    }

    // Simple role check: only SUPER_ADMIN can use this controller
    private boolean hasRole(String token, String... roles) {
        String role = jwtUtil.extractRole(token);
        if (role == null) return false;
        for (String r : roles) if (r.equalsIgnoreCase(role)) return true;
        return false;
    }

    /**
     * GET /api/superadmin/payment-methods
     * Lists all platform payment methods (enabled and disabled).
     *
     * Use case:
     * - Super admin dashboard shows all gateways in the platform catalog.
     */
    @GetMapping
    public ResponseEntity<?> list(@RequestHeader("Authorization") String auth) {
        if (!hasRole(strip(auth), "SUPER_ADMIN")) {
            return ResponseEntity.status(403).body(Map.of("message","SUPER_ADMIN required"));
        }
        return ResponseEntity.ok(repo.findAll());
    }

    /**
     * POST /api/superadmin/payment-methods
     * Creates a new platform payment method.
     *
     * Example:
     * { "name": "HYPERPAY", "enabled": true }
     *
     * Note:
     * - This only adds it to the platform catalog table.
     * - Owners still need to enable/configure it for each project.
     * - If there is no matching PaymentGateway plugin implementation (code()),
     *   the owner list endpoint will fail when calling registry.require(name).
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestHeader("Authorization") String auth,
                                    @RequestBody PaymentMethod body) {

        if (!hasRole(strip(auth), "SUPER_ADMIN")) {
            return ResponseEntity.status(403).body(Map.of("message","SUPER_ADMIN required"));
        }

        if (body.getName() == null || body.getName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error","name is required"));
        }

        // Normalize code to uppercase for consistency
        String name = body.getName().trim().toUpperCase();

        // Prevent duplicates
        if (repo.findByNameIgnoreCase(name).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error","Payment method exists: " + name));
        }

        PaymentMethod pm = new PaymentMethod(name, body.isEnabled());
        return ResponseEntity.status(201).body(repo.save(pm));
    }

    /**
     * PUT /api/superadmin/payment-methods/{id}
     * Updates a platform payment method.
     *
     * Typical use case:
     * - Enable or disable a gateway globally.
     *   If disabled globally => owners should not see it in their settings list.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@RequestHeader("Authorization") String auth,
                                    @PathVariable Long id,
                                    @RequestBody PaymentMethod body) {

        if (!hasRole(strip(auth), "SUPER_ADMIN")) {
            return ResponseEntity.status(403).body(Map.of("message","SUPER_ADMIN required"));
        }

        PaymentMethod pm = repo.findById(id).orElse(null);
        if (pm == null) {
            return ResponseEntity.status(404).body(Map.of("error","not found"));
        }

        // Update name (code) if provided
        if (body.getName() != null && !body.getName().isBlank()) {
            pm.setName(body.getName().trim().toUpperCase());
        }

        // Update enabled flag
        pm.setEnabled(body.isEnabled());

        return ResponseEntity.ok(repo.save(pm));
    }

    /**
     * DELETE /api/superadmin/payment-methods/{id}
     * Deletes a gateway from platform catalog.
     *
     * WARNING:
     * If owners already configured this method in payment_method_configs,
     * deleting it may break those configs or orphan records (depending on FK constraints).
     * In production, it's often safer to "disable" rather than delete.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@RequestHeader("Authorization") String auth,
                                    @PathVariable Long id) {

        if (!hasRole(strip(auth), "SUPER_ADMIN")) {
            return ResponseEntity.status(403).body(Map.of("message","SUPER_ADMIN required"));
        }

        if (!repo.existsById(id)) {
            return ResponseEntity.status(404).body(Map.of("error","not found"));
        }

        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
