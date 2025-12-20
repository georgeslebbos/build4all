package com.build4all.payment.gateway.impl;

import com.build4all.payment.gateway.PaymentGateway;
import com.build4all.payment.gateway.dto.CreatePaymentCommand;
import com.build4all.payment.gateway.dto.CreatePaymentResult;
import com.build4all.payment.gateway.dto.GatewayConfig;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
// @Service => Spring will auto-discover this class as a bean.
// Because it implements PaymentGateway, Spring will also include it in:
// PaymentGatewayRegistry(List<PaymentGateway> list)
public class VisaGateway implements PaymentGateway {

    @Override
    public String code() {
        // This must match:
        // - PaymentMethod.name in DB (payment_methods)
        // - the value sent by client: paymentMethod="CASH"
        return "VISA";
    }

    @Override
    public String displayName() {
        // Human-friendly name for UI
        return "VISA";
    }

    @Override
    public Map<String, Object> configSchema() {
        // This describes the OWNER configuration screen (auto-generated form).
        // WooCommerce-style: each gateway exposes its own settings fields.
        //
        // Cash usually needs only "instructions" (e.g., pay on delivery / pay at shop).
        Map<String, Object> schema = new HashMap<>();
        schema.put("title", "VISA Settings");

        // "fields" is an array of field definitions used by your dynamic form engine
        schema.put("fields", new Object[]{
                Map.of(
                        "key", "instructions",
                        "label", "Instructions",
                        "type", "textarea",
                        "required", false
                )
        });

        return schema;
    }

    @Override
    public Map<String, Object> publicCheckoutConfig(GatewayConfig config) {
        // This returns only SAFE info to the public checkout UI (mobile app).
        // For cash payment, no client-side SDK keys are needed.
        //
        // Optionally, you *could* expose instructions to show to the user:
        // return Map.of("instructions", config.getString("instructions"));
        //
        // But right now you return nothing.
        return Map.of();
    }

    @Override
    public CreatePaymentResult createPayment(CreatePaymentCommand cmd, GatewayConfig config) {
        // "Cash" means OFFLINE payment:
        // - No external provider API call
        // - No clientSecret
        // - No redirect URL
        //
        // We return an "offline reference" that your system can store in PaymentTransaction.
        // This becomes providerPaymentId in PaymentTransaction, so you can track it consistently.

        return CreatePaymentResult.offline(
                "CASH_ORDER_" + cmd.getOrderId(), // providerPaymentId (internal reference)
                "OFFLINE_PENDING"                  // status in YOUR system
        );
    }
}
