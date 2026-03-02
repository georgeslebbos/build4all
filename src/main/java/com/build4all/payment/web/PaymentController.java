package com.build4all.payment.web;

import com.build4all.payment.dto.StartPaymentRequest;
import com.build4all.payment.service.PaymentOrchestratorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentOrchestratorService orchestrator;

    public PaymentController(PaymentOrchestratorService orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestBody StartPaymentRequest req) {

        if (req.getOwnerProjectId() == null || req.getOrderId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "ownerProjectId and orderId required"));
        }
        if (req.getPaymentMethod() == null || req.getPaymentMethod().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "paymentMethod required"));
        }
        if (req.getCurrency() == null || req.getCurrency().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "currency required"));
        }
        if (req.getAmount() == null || req.getAmount().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "amount required"));
        }

        final BigDecimal amount;
        try {
            amount = new BigDecimal(req.getAmount().trim());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "amount must be numeric"));
        }

        var res = orchestrator.startPayment(
                req.getOwnerProjectId(),
                req.getOrderId(),
                req.getPaymentMethod(),
                amount,
                req.getCurrency(),
                req.getDestinationAccountId()
        );

        return ResponseEntity.ok(res);
    }
}