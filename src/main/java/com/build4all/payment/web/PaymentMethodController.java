package com.build4all.payment.web;

import com.build4all.payment.repository.PaymentMethodRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payment-methods")
public class PaymentMethodController {

    private final PaymentMethodRepository repo;

    public PaymentMethodController(PaymentMethodRepository repo) {
        this.repo = repo;
    }

    /**
     * ✅ Public endpoint:
     * Returns platform-enabled methods only.
     * Owners will see a richer version in OwnerPaymentConfigController (/owner/.../payment/methods)
     */
    @GetMapping("/enabled")
    public ResponseEntity<?> enabled() {
        return ResponseEntity.ok(repo.findByEnabledTrue());
    }
}