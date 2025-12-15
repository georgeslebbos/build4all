package com.build4all.payment.service;

import com.build4all.payment.domain.PaymentMethodConfig;
import com.build4all.payment.domain.PaymentTransaction;
import com.build4all.payment.dto.StartPaymentResponse;
import com.build4all.payment.gateway.PaymentGateway;
import com.build4all.payment.gateway.PaymentGatewayRegistry;
import com.build4all.payment.gateway.dto.*;
import com.build4all.payment.repository.PaymentTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
// This service orchestrates payment creation in a provider-agnostic way.
// It behaves like WooCommerce's internal payment engine:
// - It does NOT care if payment is Stripe, Cash, PayPal...
// - It simply selects the gateway plugin and delegates to it.
public class PaymentOrchestratorService {

    private final PaymentGatewayRegistry registry;
    private final PaymentConfigService configService;
    private final PaymentTransactionRepository txRepo;

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
        tx.setCurrency(currencyCode.toLowerCase()); // normalize
        tx.setStatus("CREATED"); // your internal initial status
        tx = txRepo.save(tx);

        // 5) Build a command object that contains payment input data.
        // This is provider-agnostic: Stripe/Cash/PayPal all receive the same structure.
        CreatePaymentCommand cmd = new CreatePaymentCommand();
        cmd.setOwnerProjectId(ownerProjectId);
        cmd.setOrderId(orderId);
        cmd.setAmount(amount);
        cmd.setCurrency(currencyCode.toLowerCase());
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
        tx.setStatus(r.getStatus() == null ? "CREATED" : r.getStatus().toUpperCase());
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
        return out;
    }
}
