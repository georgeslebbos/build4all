package com.build4all.payment.web;

import com.build4all.payment.service.StripeService;
import com.stripe.model.PaymentIntent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class StripeController {

    @Autowired
    private StripeService stripeService;

    @PostMapping("/create-intent")
    public ResponseEntity<?> createIntent(@RequestBody Map<String, Object> request) {
        try {
            // Validate required fields
            if (!request.containsKey("price") || !request.containsKey("currency") || !request.containsKey("stripeAccountId")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing one or more required fields: price, currency, stripeAccountId"));
            }

            // Extract values safely
            Object priceObj = request.get("price");
            Object currencyObj = request.get("currency");
            Object stripeAccountIdObj = request.get("stripeAccountId");

            if (priceObj == null || currencyObj == null || stripeAccountIdObj == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Null value in required fields"));
            }

            double price = Double.parseDouble(priceObj.toString());
            String currency = currencyObj.toString();
            String stripeAccountId = stripeAccountIdObj.toString();

            PaymentIntent intent = stripeService.createPaymentIntentWithCommission(price, currency, stripeAccountId);

            return ResponseEntity.ok(Map.of(
                "clientSecret", intent.getClientSecret(),
                "paymentIntentId", intent.getId()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

}
