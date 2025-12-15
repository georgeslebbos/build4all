package com.build4all.payment.gateway.impl;

import com.build4all.payment.gateway.PaymentGateway;
import com.build4all.payment.gateway.dto.*;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
// @Service => Spring auto-registers this gateway as a bean.
// Because it implements PaymentGateway, it will be auto-included in PaymentGatewayRegistry.
public class PaypalGateway implements PaymentGateway {

    @Override
    public String code() {
        // Must match:
        // - payment_methods.name in DB
        // - payment_method sent by client ("PAYPAL")
        // - this gateway key inside PaymentGatewayRegistry
        return "PAYPAL";
    }

    @Override
    public String displayName() {
        // Friendly name shown in UI
        return "PayPal";
    }

    @Override
    public Map<String, Object> configSchema() {
        // This schema is used by the OWNER settings UI to render a dynamic form.
        // Owners can configure PayPal without asking you to code UI fields.
        Map<String, Object> schema = new HashMap<>();
        schema.put("title", "PayPal Settings");

        // "fields" describes what the owner must input to enable PayPal.
        // - clientId/clientSecret are PayPal credentials
        // - mode is SANDBOX vs LIVE
        schema.put("fields", new Object[]{
                Map.of("key","clientId", "label","Client ID", "type","text", "required", true),
                Map.of("key","clientSecret", "label","Client Secret", "type","password", "required", true),
                Map.of(
                        "key", "mode",
                        "label", "Mode",
                        "type", "select",
                        "options", new String[]{"SANDBOX","LIVE"},
                        "default", "SANDBOX"
                )
        });

        return schema;
    }

    @Override
    public Map<String, Object> publicCheckoutConfig(GatewayConfig config) {
        // Public checkout should only receive SAFE info.
        // For PayPal, the mobile/web client usually needs clientId to initialize the PayPal button/SDK.
        // NEVER expose clientSecret here.
        String clientId = config.getString("clientId");
        return clientId == null ? Map.of() : Map.of("clientId", clientId);
    }

    @Override
    public CreatePaymentResult createPayment(CreatePaymentCommand cmd, GatewayConfig config) {
        // PayPal integration is not a single “clientSecret” like Stripe.
        //
        // Typical PayPal flow:
        // 1) Server creates a PayPal Order (using clientId/clientSecret).
        // 2) PayPal returns an "approval link" (redirect URL).
        // 3) Client opens the approval link (user approves).
        // 4) Server captures the order to finalize payment.
        // 5) Webhook or capture response confirms PAID.
        //
        // So for PayPal you will likely return:
        // - providerPaymentId = PayPal order id
        // - redirectUrl = approval link
        //
        // Then the client redirects the user to PayPal for approval.
        throw new UnsupportedOperationException(
                "PayPal gateway not implemented yet (requires PayPal SDK + order/approval flow)"
        );
    }
}
