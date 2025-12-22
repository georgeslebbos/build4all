package com.build4all.payment.gateway.dto;

/**
 * CreatePaymentResult is the OUTPUT returned by any PaymentGateway plugin
 * when the backend "starts" a payment.
 *
 * It contains provider-specific data that the client may need to continue:
 * - Stripe: clientSecret to confirm payment in mobile SDK
 * - PayPal: redirectUrl to approve payment
 * - Cash: offline reference only (no clientSecret)
 */
public class CreatePaymentResult {

    /**
     * Provider-side identifier (or offline reference).
     *
     * Stripe: PaymentIntent id, e.g. "pi_3P...."
     * PayPal: Order id (later), e.g. "5O190127TN364715T"
     * Cash: internal reference like "CASH_ORDER_9001"
     *
     * This value is critical because you store it into:
     * PaymentTransaction.providerPaymentId
     * and later webhooks can find the transaction by it.
     */
    private String providerPaymentId;

    /**
     * Used mostly by Stripe.
     * The mobile app uses this with Stripe SDK to confirm the payment.
     *
     * IMPORTANT: clientSecret is safe to send to client,
     * but the Stripe "secretKey" is NOT.
     */
    private String clientSecret;

    /**
     * Used by providers that require redirect/approval flow.
     * Example: PayPal approval link, redirect-based providers.
     *
     * Stripe does not need redirectUrl because it uses clientSecret + SDK.
     */
    private String redirectUrl;

    /**
     * Provider status (or your own internal status returned by gateway).
     *
     * Stripe examples:
     * - "requires_payment_method"
     * - "requires_action"
     * - "succeeded"
     *
     * Cash example:
     * - "OFFLINE_PENDING"
     *
     * Your orchestrator later normalizes it to uppercase in PaymentTransaction.
     */
    private String status;

    /**
     * Factory helper for Stripe results.
     * StripeGateway uses this to return (pi id + clientSecret + status).
     */
    public static CreatePaymentResult stripe(String pi, String secret, String status) {
        CreatePaymentResult r = new CreatePaymentResult();
        r.providerPaymentId = pi;
        r.clientSecret = secret;
        r.status = status;
        return r;
    }

    /**
     * Factory helper for offline providers (Cash).
     * No clientSecret, no redirectUrl.
     */
    public static CreatePaymentResult offline(String ref, String status) {
        CreatePaymentResult r = new CreatePaymentResult();
        r.providerPaymentId = ref;
        r.status = status;
        return r;
    }

    /** ✅ ADD THIS */
    public static CreatePaymentResult paypal(String orderId, String approvalUrl, String status) {
        CreatePaymentResult r = new CreatePaymentResult();
        r.providerPaymentId = orderId;
        r.redirectUrl = approvalUrl;
        r.status = status;
        return r;
    }

    // ---- Getters ----
    public String getProviderPaymentId() { return providerPaymentId; }
    public String getClientSecret() { return clientSecret; }
    public String getRedirectUrl() { return redirectUrl; }
    public String getStatus() { return status; }
}
