package com.build4all.payment.service;

import com.build4all.payment.domain.PaymentMethodConfig;
import com.build4all.payment.domain.PaymentTransaction;
import com.build4all.payment.dto.StartPaymentResponse;
import com.build4all.payment.gateway.PaymentGateway;
import com.build4all.payment.gateway.PaymentGatewayRegistry;
import com.build4all.payment.gateway.dto.CreatePaymentCommand;
import com.build4all.payment.gateway.dto.CreatePaymentResult;
import com.build4all.payment.gateway.dto.GatewayConfig;
import com.build4all.payment.repository.PaymentTransactionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

@Service
// This service orchestrates payment creation in a provider-agnostic way.
// It behaves like WooCommerce's internal payment engine:
// - It does NOT care if payment is Stripe, Cash, PayPal...
// - It simply selects the gateway plugin and delegates to it.
public class PaymentOrchestratorService {

    private final PaymentGatewayRegistry registry;
    private final PaymentConfigService configService;
    private final PaymentTransactionRepository txRepo;

    // Used only to parse configJson safely when we need publishableKey
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PaymentOrchestratorService(PaymentGatewayRegistry registry,
                                      PaymentConfigService configService,
                                      PaymentTransactionRepository txRepo) {
        this.registry = registry;
        this.configService = configService;
        this.txRepo = txRepo;
    }

    @Transactional
    // Transaction boundary:
    // - create PaymentTransaction row
    // - call gateway (Stripe create PaymentIntent)
    // - update transaction with providerPaymentId/status
    //
    // If something fails, DB changes roll back (except external Stripe call, which cannot roll back).
    public StartPaymentResponse startPayment(Long ownerProjectId,
                                             Long orderId,
                                             String paymentMethod,
                                             BigDecimal amount,
                                             String currencyCode,
                                             String destinationAccountId) {

        // 1) Resolve the correct plugin implementation.
        // Example: "STRIPE" -> StripeGateway, "CASH" -> CashGateway
        PaymentGateway gateway = registry.require(paymentMethod);

        // 2) Load and enforce per-project gateway config (like WooCommerce store settings).
        // If Stripe is not configured for this project, fail.
        PaymentMethodConfig cfgEntity = configService.requireEnabled(ownerProjectId, paymentMethod);

        // 3) Parse the config JSON into a runtime object (GatewayConfig),
        // so the gateway can read values like "secretKey".
        GatewayConfig cfg = configService.parse(cfgEntity.getConfigJson());

        // 4) Create an internal transaction record BEFORE calling the provider.
        // This gives you an audit record even if Stripe call fails later.
        PaymentTransaction tx = new PaymentTransaction();
        tx.setOwnerProjectId(ownerProjectId);
        tx.setOrderId(orderId);
        tx.setProviderCode(gateway.code()); // normalized official plugin code
        tx.setAmount(amount);
        tx.setCurrency(currencyCode.toLowerCase(Locale.ROOT)); // normalize
        tx.setStatus("CREATED"); // your internal initial status
        tx = txRepo.save(tx);

        // 5) Build a command object that contains payment input data.
        // This is provider-agnostic: Stripe/Cash/PayPal all receive the same structure.
        CreatePaymentCommand cmd = new CreatePaymentCommand();
        cmd.setOwnerProjectId(ownerProjectId);
        cmd.setOrderId(orderId);
        cmd.setAmount(amount);
        cmd.setCurrency(currencyCode.toLowerCase(Locale.ROOT));
        cmd.setDestinationAccountId(destinationAccountId); // used only by gateways that support it (Stripe Connect)

        // 6) Delegate to the gateway plugin.
        // For Stripe:
        // - it creates a PaymentIntent
        // - returns providerPaymentId (pi_...) and clientSecret
        //
        // For Cash:
        // - returns a local reference (CASH_ORDER_...)
        // - status OFFLINE_PENDING
        CreatePaymentResult r = gateway.createPayment(cmd, cfg);

        // 7) Store provider reference + status in your internal transaction ledger.
        // This is critical for:
        // - webhook reconciliation (find by providerPaymentId)
        // - UI status tracking
        tx.setProviderPaymentId(r.getProviderPaymentId());
        tx.setStatus(r.getStatus() == null ? "CREATED" : r.getStatus().toUpperCase(Locale.ROOT));
        txRepo.save(tx);

        // 8) Build response for the client checkout.
        // Stripe: clientSecret is used by mobile Stripe SDK to confirm payment.
        // PayPal: redirectUrl would be used to open approval link.
        // Cash: both might be null, but status OFFLINE_PENDING.
        StartPaymentResponse out = new StartPaymentResponse();
        out.setTransactionId(tx.getId());
        out.setProviderCode(tx.getProviderCode());
        out.setProviderPaymentId(tx.getProviderPaymentId());
        out.setClientSecret(r.getClientSecret());
        out.setRedirectUrl(r.getRedirectUrl());
        out.setStatus(tx.getStatus());

        // ✅ NEW (Stripe-only): return publishableKey (pk_...) from the SAME configJson stored in DB.
        // Important: NEVER return secretKey (sk_...) to client.
        if ("STRIPE".equalsIgnoreCase(tx.getProviderCode())) {
            String pk = extractPublishableKeyFromConfigJson(cfgEntity.getConfigJson());
            if (pk != null && !pk.isBlank()) {
                pk = pk.trim();

                // Safety guard: if someone accidentally stored secret key in publishableKey field
                if (pk.startsWith("sk_")) {
                    throw new IllegalStateException(
                            "Invalid Stripe publishableKey in configJson (secret key detected). " +
                                    "Please store pk_... under publishableKey."
                    );
                }

                out.setPublishableKey(pk);
            }
        }

        return out;
    }

    /**
     * Extract Stripe publishableKey from the same configJson used by Stripe gateway.
     *
     * Why this helper exists:
     * - Your Stripe gateway needs secretKey to create PaymentIntent (server side).
     * - Your mobile client needs publishableKey to initialize Stripe SDK (client side).
     * - Both keys are stored in the same JSON config in DB.
     *
     * We try common key names to be resilient across versions:
     * - publishableKey
     * - stripePublishableKey
     * - stripe_publishable_key
     * - publishable_key
     */
    private String extractPublishableKeyFromConfigJson(String configJson) {
        if (configJson == null || configJson.isBlank()) return null;

        try {
            Map<String, Object> m = objectMapper.readValue(
                    configJson,
                    new TypeReference<Map<String, Object>>() {}
            );

            Object v =
                    m.get("publishableKey") != null ? m.get("publishableKey") :
                            m.get("stripePublishableKey") != null ? m.get("stripePublishableKey") :
                                    m.get("stripe_publishable_key") != null ? m.get("stripe_publishable_key") :
                                            m.get("publishable_key");

            if (v == null) return null;

            String s = v.toString().trim();
            return s.isEmpty() ? null : s;
        } catch (Exception ignored) {
            // We don't want checkout to fail only because pk couldn't be read.
            // Client will later fail with "paymentConfiguration not initialized"
            // which signals configJson is missing publishableKey.
            return null;
        }
    }
}
