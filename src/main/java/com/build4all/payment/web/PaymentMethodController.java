package com.build4all.payment.web;

import com.build4all.payment.domain.PaymentMethod;
import com.build4all.payment.repository.PaymentMethodRepository;
import com.build4all.security.JwtUtil;

import org.springframework.beans.factory.annotation.Autowired;
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

    private boolean isSuperAdmin(String token) {
        try {
            token = token.substring(7).trim();
            String role = jwtUtil.extractRole(token);
            return "SUPER_ADMIN".equalsIgnoreCase(role);
        } catch (Exception e) {
            return false;
        }
    }

    // ✅ Fetch all payment methods (for Super Admin dashboard)
    @GetMapping
    public ResponseEntity<?> getAllMethods(@RequestHeader("Authorization") String token) {
        if (!isSuperAdmin(token)) return ResponseEntity.status(401).body("Unauthorized");
        return ResponseEntity.ok(paymentMethodRepository.findAll());
    }

    // ✅ Fetch enabled methods (for user-facing frontend)
    @GetMapping("/enabled")
    public ResponseEntity<?> getEnabledMethods() {
        return ResponseEntity.ok(paymentMethodRepository.findByEnabledTrue());
    }

    // ✅ Toggle payment method status (enable/disable)
    @PutMapping("/{id}/toggle")
    public ResponseEntity<?> toggleMethod(@PathVariable Long id,
                                          @RequestHeader("Authorization") String token) {
        if (!isSuperAdmin(token)) return ResponseEntity.status(401).body("Unauthorized");

        Optional<PaymentMethod> optional = paymentMethodRepository.findById(id);
        if (optional.isEmpty()) {
            return ResponseEntity.status(404).body("Payment method not found");
        }

        PaymentMethod method = optional.get();
        method.setEnabled(!method.isEnabled());
        paymentMethodRepository.save(method);

        return ResponseEntity.ok(Map.of(
                "message", "Payment method " + (method.isEnabled() ? "enabled" : "disabled"),
                "method", method
        ));
    }
}
