package com.build4all.payment.gateway;

import com.build4all.payment.gateway.dto.*;

import java.util.Map;

/**
 * PaymentGateway is the "plugin contract" (WooCommerce-style).
 *
 * Every payment provider (Stripe, Cash, PayPal, HyperPay, Tap...)
 * must implement this interface so the rest of your system can:
 * - list it in the owner dashboard
 * - collect config from the owner
 * - start a payment during checkout
 * without changing checkout/order code.
 */
public interface PaymentGateway {

    /**
     * The unique gateway CODE.
     *
     * Must match:
     * - PaymentMethod.name in DB (payment_methods table)
     * - The string sent in checkout request: paymentMethod="STRIPE"
     *
     * Examples: "STRIPE", "CASH", "PAYPAL"
     */
    String code();

    /**
     * Friendly label for UI.
     * Example: "Stripe", "Cash on Delivery"
     */
    String displayName();

    /**
     * Used by OWNER settings UI.
     *
     * Returns a JSON schema that describes the configuration form fields
     * for this gateway, so the UI can auto-generate the form.
     *
     * Example for Stripe:
     * - secretKey (password)
     * - publishableKey (text)
     * - webhookSecret (password)
     * - platformFeePct (number)
     *
     * This is what makes the system "dynamic" like WooCommerce:
     * the owner can configure gateways without code changes.
     */
    Map<String, Object> configSchema();

    /**
     * Used by PUBLIC checkout UI (mobile app).
     *
     * Returns only SAFE configuration fields that the client is allowed to see.
     * Example for Stripe: publishableKey
     *
     * IMPORTANT:
     * - NEVER expose secretKey/webhookSecret here.
     * - Only return values needed to initialize client SDKs.
     */
    Map<String, Object> publicCheckoutConfig(GatewayConfig config);

    /**
     * Starts/creates a payment with the provider.
     *
     * Input:
     * - CreatePaymentCommand: orderId, amount, currency, ownerProjectId, etc.
     * - GatewayConfig: parsed configJson from PaymentMethodConfig (per project)
     *
     * Output:
     * - CreatePaymentResult containing:
     *   - providerPaymentId (Stripe PaymentIntent id "pi_...")
     *   - clientSecret (needed by Stripe SDK)
     *   - status (CREATED / REQUIRES_ACTION / ...)
     *
     * Note:
     * - This method usually does NOT "finalize" the payment.
     * - For Stripe, it creates a PaymentIntent and returns clientSecret.
     * - Payment success is typically confirmed via WEBHOOK later.
     */
    CreatePaymentResult createPayment(CreatePaymentCommand cmd, GatewayConfig config);
}
