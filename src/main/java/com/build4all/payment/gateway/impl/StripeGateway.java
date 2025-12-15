package com.build4all.payment.gateway.impl;

import com.build4all.payment.gateway.PaymentGateway;
import com.build4all.payment.gateway.dto.*;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
// @Service => Spring registers this as a bean.
// Because it implements PaymentGateway, it will be included automatically in PaymentGatewayRegistry.
public class StripeGateway implements PaymentGateway {

    @Override public String code() { return "STRIPE"; }
    // Must match:
    // - payment_methods.name = "STRIPE"
    // - checkout request paymentMethod = "STRIPE"

    @Override public String displayName() { return "Stripe"; }
    // Friendly label used in UI

    @Override
    public Map<String, Object> configSchema() {
        // This schema is used by the OWNER dashboard to render a dynamic settings form.
        // The owner enters Stripe keys without needing code changes.
        //
        // These fields are stored as JSON inside PaymentMethodConfig.configJson (per project).
        Map<String, Object> schema = new HashMap<>();
        schema.put("title", "Stripe Settings");
        schema.put("fields", new Object[]{
                // secretKey: server-side only (must never be sent to mobile clients)
                Map.of("key","secretKey","label","Secret Key","type","password","required",true),

                // publishableKey: safe for mobile app; used to initialize Stripe SDK
                Map.of("key","publishableKey","label","Publishable Key","type","text","required",true),

                // webhookSecret: server-side only; used later in webhook signature verification
                Map.of("key","webhookSecret","label","Webhook Secret","type","password","required",true),

                // platformFeePct: optional, used when you do Stripe Connect destination payments
                Map.of("key","platformFeePct","label","Platform Fee %","type","number","min",0,"max",30,"default",10)
        });
        return schema;
    }

    @Override
    public Map<String, Object> publicCheckoutConfig(GatewayConfig config) {
        // Returns only SAFE config for the mobile app checkout UI.
        // Stripe requires publishableKey on the client to initialize the Stripe SDK.
        //
        // IMPORTANT: do not expose secretKey or webhookSecret here.
        String pk = config.getString("publishableKey");
        return pk == null ? Map.of() : Map.of("publishableKey", pk);
    }

    @Override
    public CreatePaymentResult createPayment(CreatePaymentCommand cmd, GatewayConfig config) {

        // 1) Read Stripe secret key from per-project configJson
        // Each ownerProject can have different Stripe keys (WooCommerce-style per store settings).
        String secretKey = config.getString("secretKey");
        if (secretKey == null || secretKey.isBlank())
            throw new IllegalStateException("Stripe secretKey not configured for this project");

        // 2) Build Stripe request options with the secret key
        // This means: all Stripe calls here are authenticated as THIS store's Stripe account.
        RequestOptions opts = RequestOptions.builder().setApiKey(secretKey).build();

        // 3) Convert your amount into cents (Stripe uses smallest currency unit)
        // Example: 49.99 USD -> 4999
        //
        // NOTE: amount.multiply(100).longValue() assumes 2 decimals currencies.
        // For currencies like JPY (0 decimals) you'd need special handling later.
        BigDecimal amount = cmd.getAmount() == null ? BigDecimal.ZERO : cmd.getAmount();
        long cents = amount.multiply(BigDecimal.valueOf(100)).longValue();

        // Normalize currency to lowercase for Stripe ("usd", "eur", ...)
        String currency = (cmd.getCurrency() == null ? "usd" : cmd.getCurrency()).toLowerCase();

        // 4) Platform fee percentage (for Stripe Connect)
        // If you're not using Connect destination transfers, fee is not applied.
        Double feePct = config.getDouble("platformFeePct", 10.0);
        long fee = Math.round(cents * (feePct / 100.0));

        // 5) Build the PaymentIntent create request
        // Automatic payment methods lets Stripe decide what methods to show.
        PaymentIntentCreateParams.Builder b = PaymentIntentCreateParams.builder()
                .setAmount(cents)
                .setCurrency(currency)
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build()
                );

        // 6) Add metadata
        // This is VERY useful because webhook events can include these values
        // so you can map the Stripe PaymentIntent back to your Order / OwnerProject.
        Map<String, String> md = new HashMap<>();
        md.put("orderId", String.valueOf(cmd.getOrderId()));
        md.put("ownerProjectId", String.valueOf(cmd.getOwnerProjectId()));
        if (cmd.getMetadata() != null) md.putAll(cmd.getMetadata());
        b.putAllMetadata(md);

        // 7) Stripe Connect destination payments (optional)
        // If you're running a marketplace:
        // - customer pays to platform
        // - then Stripe transfers funds to destinationAccountId (business)
        // - applicationFeeAmount is what your platform keeps
        if (cmd.getDestinationAccountId() != null && !cmd.getDestinationAccountId().isBlank()) {
            b.setApplicationFeeAmount(fee);
            b.setTransferData(
                    PaymentIntentCreateParams.TransferData.builder()
                            .setDestination(cmd.getDestinationAccountId())
                            .build()
            );
        }

        try {
            // 8) Create the PaymentIntent on Stripe
            PaymentIntent intent = PaymentIntent.create(b.build(), opts);

            // 9) Return a result that the client can use.
            // clientSecret is needed by the mobile app to confirm the payment with Stripe SDK.
            // providerPaymentId = PaymentIntent id "pi_..."
            // status = Stripe's PaymentIntent status (e.g. requires_payment_method, succeeded, ...)
            return CreatePaymentResult.stripe(
                    intent.getId(),
                    intent.getClientSecret(),
                    intent.getStatus()
            );
        } catch (Exception e) {
            // We wrap Stripe exceptions into runtime error for your service/controller.
            throw new IllegalStateException("Stripe createPayment failed: " + e.getMessage());
        }
    }
}
