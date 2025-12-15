package com.build4all.payment.gateway;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
// @Service makes this class a Spring Bean, so you can inject it anywhere:
// e.g. PaymentOrchestratorService, OwnerPaymentConfigController, etc.
public class PaymentGatewayRegistry {

    // Map of gateway code -> gateway implementation instance
    // Example:
    //  "STRIPE" -> StripeGateway
    //  "CASH"   -> CashGateway
    private final Map<String, PaymentGateway> gateways;

    public PaymentGatewayRegistry(List<PaymentGateway> list) {
        // IMPORTANT: Spring automatically injects ALL beans that implement PaymentGateway
        // into this constructor parameter "list".
        //
        // That means:
        // - If you have @Service class StripeGateway implements PaymentGateway,
        //   it will be inside this list.
        // - If you later add @Service class HyperPayGateway implements PaymentGateway,
        //   it will also be added automatically.
        //
        // This is the "plugin" behavior (WooCommerce-style).

        this.gateways = list.stream()
                .collect(Collectors.toMap(
                        // Normalize the key to uppercase to avoid case bugs:
                        // "stripe" / "Stripe" / "STRIPE" all become "STRIPE"
                        g -> g.code().toUpperCase(Locale.ROOT),

                        // The value is the gateway instance itself
                        g -> g

                        // NOTE: if TWO gateways return the same code(),
                        // Collectors.toMap will throw an exception at startup.
                        // That's good because it prevents ambiguous providers.
                ));
    }

    public PaymentGateway require(String code) {
        // "require" means:
        // - validate the input is not empty
        // - validate the gateway exists
        // - if not, throw an error immediately
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("paymentMethod is required");
        }

        // Normalize the incoming code the same way as map keys
        PaymentGateway g = gateways.get(code.trim().toUpperCase(Locale.ROOT));

        if (g == null) {
            // This usually means one of these:
            // 1) You forgot to create a gateway class (StripeGateway) as a Spring bean (@Service)
            // 2) code() doesn't match the DB PaymentMethod.name
            // 3) You typed wrong value in request ("STRPIE" typo)
            throw new IllegalArgumentException("Unsupported gateway: " + code);
        }

        return g;
    }

    public Optional<PaymentGateway> find(String code) {
        // "find" is a safe version:
        // - it never throws for missing gateway
        // - returns Optional.empty() if not found
        if (code == null) return Optional.empty();
        return Optional.ofNullable(gateways.get(code.trim().toUpperCase(Locale.ROOT)));
    }

    public Collection<PaymentGateway> all() {
        // Returns all available gateway implementations loaded in memory.
        // Useful for admin screens, debugging, or listing supported gateway codes.
        return gateways.values();
    }
}
