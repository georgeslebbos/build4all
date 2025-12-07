package com.build4all.payment.web;

import com.build4all.payment.domain.PaymentMethod;
import com.build4all.payment.repository.PaymentMethodRepository;
import com.build4all.security.JwtUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/superadmin/payment-methods")
public class PaymentMethodController {

    @Autowired
    private PaymentMethodRepository paymentMethodRepository;

    @Autowired
    private JwtUtil jwtUtil;

    /* ------------------------ helpers (same pattern as ProductController) ------------------------ */

    private String strip(String auth) {
        return auth == null ? "" : auth.replace("Bearer ", "").trim();
    }

    private boolean hasRole(String token, String... roles) {
        String role = jwtUtil.extractRole(token);
        if (role == null) return false;
        for (String r : roles) {
            if (r.equalsIgnoreCase(role)) return true;
        }
        return false;
    }

    // ✅ Fetch all payment methods (for Super Admin dashboard)
    @GetMapping
    public ResponseEntity<?> getAllMethods(@RequestHeader("Authorization") String auth) {
        String token = strip(auth);

        // USER and OWNER can see slider banners
        if (!hasRole(token, "USER", "OWNER")) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "USER or OWNER role required."));
        }
        return ResponseEntity.ok(paymentMethodRepository.findAll());
    }

    // ✅ Get single payment method by id (for Super Admin)
    @GetMapping("/{id}")
    public ResponseEntity<?> getMethodById(@RequestHeader("Authorization") String auth,
                                           @PathVariable Long id) {
        String token = strip(auth);

        // USER and OWNER can see slider banners
        if (!hasRole(token, "USER", "OWNER")) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "USER or OWNER role required."));
        }

        Optional<PaymentMethod> optional = paymentMethodRepository.findById(id);
        if (optional.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(Map.of("error", "Payment method not found"));
        }

        return ResponseEntity.ok(optional.get());
    }

    // ✅ Create payment method (for Super Admin)
    @PostMapping
    public ResponseEntity<?> createMethod(@RequestHeader("Authorization") String auth,
                                          @RequestBody PaymentMethod body) {
        String token = strip(auth);

        // USER and OWNER can see slider banners
        if (!hasRole(token, "OWNER")) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "USER or OWNER role required."));
        }

        if (body.getName() == null || body.getName().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "name is required"));
        }

        String normalized = body.getName().trim().toUpperCase();

        // prevent duplicates by name
        if (paymentMethodRepository.findByNameIgnoreCase(normalized).isPresent()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Payment method already exists: " + normalized));
        }

        PaymentMethod pm = new PaymentMethod();
        pm.setName(normalized);
        pm.setEnabled(body.isEnabled());

        PaymentMethod saved = paymentMethodRepository.save(pm);
        return ResponseEntity.status(201).body(saved);
    }

    // ✅ Update payment method (for Super Admin)
    @PutMapping("/{id}")
    public ResponseEntity<?> updateMethod(@RequestHeader("Authorization") String auth,
                                          @PathVariable Long id,
                                          @RequestBody PaymentMethod body) {
        String token = strip(auth);

        // USER and OWNER can see slider banners
        if (!hasRole(token, "USER", "OWNER")) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "USER or OWNER role required."));
        }

        Optional<PaymentMethod> optional = paymentMethodRepository.findById(id);
        if (optional.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(Map.of("error", "Payment method not found"));
        }

        PaymentMethod existing = optional.get();

        if (body.getName() != null && !body.getName().isBlank()) {
            existing.setName(body.getName().trim().toUpperCase());
        }
        // enabled flag – if body.isEnabled() default false, we still accept it (API level decision)
        existing.setEnabled(body.isEnabled());

        PaymentMethod updated = paymentMethodRepository.save(existing);
        return ResponseEntity.ok(updated);
    }

    // ✅ Delete payment method (for Super Admin)
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMethod(@RequestHeader("Authorization") String auth,
                                          @PathVariable Long id) {
        String token = strip(auth);

        // USER and OWNER can see slider banners
        if (!hasRole(token, "USER", "OWNER")) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "USER or OWNER role required."));
        }

        if (!paymentMethodRepository.existsById(id)) {
            return ResponseEntity.status(404)
                    .body(Map.of("error", "Payment method not found"));
        }

        paymentMethodRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ✅ Toggle payment method status (enable/disable)
    @PutMapping("/{id}/toggle")
    public ResponseEntity<?> toggleMethod(@PathVariable Long id,
                                          @RequestHeader("Authorization") String auth) {
        String token = strip(auth);

        // USER and OWNER can see slider banners
        if (!hasRole(token, "USER", "OWNER")) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "USER or OWNER role required."));
        }

        Optional<PaymentMethod> optional = paymentMethodRepository.findById(id);
        if (optional.isEmpty()) {
            return ResponseEntity.status(404).body("Payment method not found");
        }

        PaymentMethod method = optional.get();
        method.setEnabled(!method.isEnabled());
        paymentMethodRepository.save(method);

        // small payload for the dashboard
        return ResponseEntity.ok(Map.of(
                "message", "Payment method " + (method.isEnabled() ? "enabled" : "disabled"),
                "method", method
        ));
    }

    // ✅ Fetch enabled methods (for user-facing frontend)
    @GetMapping("/enabled")
    public ResponseEntity<?> getEnabledMethods() {
        return ResponseEntity.ok(paymentMethodRepository.findByEnabledTrue());
    }
}
