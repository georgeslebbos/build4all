package com.build4all.payment.web;

import com.build4all.payment.dto.StartPaymentRequest;
import com.build4all.payment.service.PaymentOrchestratorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
// This controller is the PUBLIC "start payment" entry point.
// The frontend calls this to start a payment attempt for an order.
// It does NOT finalize/confirm payment (Stripe confirmation happens in the mobile SDK,
// then a webhook marks it PAID on your server).
public class PaymentController {

    private final PaymentOrchestratorService orchestrator;

    public PaymentController(PaymentOrchestratorService orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestBody StartPaymentRequest req) {

        // -------- Basic input validation --------
        // These fields are required to know: which store, which order, which gateway.
        if (req.getOwnerProjectId() == null || req.getOrderId() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "ownerProjectId and orderId required"));
        }

        if (req.getPaymentMethod() == null || req.getPaymentMethod().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "paymentMethod required"));
        }

        if (req.getCurrency() == null || req.getCurrency().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "currency required"));
        }

        if (req.getAmount() == null || req.getAmount().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "amount required"));
        }

        // Convert amount String -> BigDecimal.
        // This can throw NumberFormatException if amount is not a valid number.
        // (Optional improvement: catch exception and return 400 with a friendly message.)
        BigDecimal amount = new BigDecimal(req.getAmount());

        // -------- Delegate to orchestration engine --------
        // Orchestrator will:
        // 1) pick the correct gateway plugin via registry (STRIPE/CASH/PAYPAL...)
        // 2) load project config (PaymentMethodConfig.configJson)
        // 3) create PaymentTransaction row (audit trail)
        // 4) call gateway.createPayment(...)
        // 5) update transaction and return client-facing fields (clientSecret/redirectUrl)
        var res = orchestrator.startPayment(
                req.getOwnerProjectId(),
                req.getOrderId(),
                req.getPaymentMethod(),
                amount,
                req.getCurrency(),
                req.getDestinationAccountId()
        );

        // Response contains things the client needs to continue checkout:
        // - Stripe: clientSecret + providerPaymentId (pi_...)
        // - Cash: providerPaymentId (CASH_ORDER_...) + OFFLINE_PENDING
        // - PayPal: later will return redirectUrl (approval link)
        return ResponseEntity.ok(res);
    }
}
