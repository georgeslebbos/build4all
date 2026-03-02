package com.build4all.payment.web;

import com.build4all.payment.service.StripeService;
import com.stripe.model.PaymentIntent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments/stripe")
public class StripeController {

    private final StripeService stripeService;

    public StripeController(StripeService stripeService) {
        this.stripeService = stripeService;
    }

    @PostMapping("/create-intent")
    public ResponseEntity<?> createIntent(@RequestBody Map<String, Object> request) {
        try {
            Object priceObj = request.get("price");
            Object currencyObj = request.get("currency");
            Object stripeAccountIdObj = request.get("stripeAccountId");

            if (priceObj == null || currencyObj == null || stripeAccountIdObj == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Missing required fields: price, currency, stripeAccountId"
                ));
            }

            double price;
            try {
                price = Double.parseDouble(String.valueOf(priceObj));
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("error", "price must be numeric"));
            }

            String currency = String.valueOf(currencyObj).trim();
            String stripeAccountId = String.valueOf(stripeAccountIdObj).trim();

            if (currency.isBlank() || stripeAccountId.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "currency and stripeAccountId must be non-empty"));
            }

            PaymentIntent intent = stripeService.createPaymentIntentWithCommission(price, currency, stripeAccountId);

            return ResponseEntity.ok(Map.of(
                    "clientSecret", intent.getClientSecret(),
                    "paymentIntentId", intent.getId()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }
}