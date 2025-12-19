package com.build4all.payment.dto;

/**
 * StartPaymentResponse is returned by:
 * POST /api/payments/start
 *
 * It contains everything the CLIENT needs to continue the payment flow.
 *
 * - Stripe: uses clientSecret to confirm payment in the mobile/web Stripe SDK
 * - PayPal (future): uses redirectUrl to open approval page
 * - Cash: shows offline instructions and keeps status OFFLINE_PENDING
 */
public class StartPaymentResponse {

    /**
     * Your internal DB id of PaymentTransaction.
     * Useful for debugging and UI tracking (optional to show to user).
     */
    private Long transactionId;

    /**
     * Gateway code used (STRIPE, CASH, PAYPAL...)
     * Comes from PaymentTransaction.providerCode (i.e. PaymentGateway.code()).
     */
    private String providerCode;

    /**
     * Provider payment reference (stored in PaymentTransaction.providerPaymentId).
     *
     * Stripe: PaymentIntent id "pi_..."
     * PayPal: order id (future)
     * Cash: "CASH_ORDER_{orderId}"
     *
     * This id is extremely important:
     * - Stripe webhooks will mention the PaymentIntent id
     * - Your webhook handler finds PaymentTransaction using this field
     */
    private String providerPaymentId;

    /**
     * Stripe-only (mostly):
     * Used by the client Stripe SDK to confirm payment.
     *
     * Stripe mobile flow:
     * 1) call /api/payments/start -> get clientSecret
     * 2) Stripe SDK confirms payment using clientSecret
     * 3) webhook updates server transaction/order status
     */
    private String clientSecret;

    /**
     * Redirect-based providers:
     * PayPal approval link or other gateways that need user redirect.
     * Stripe normally doesn't need redirectUrl because SDK handles it.
     */
    private String redirectUrl;

    /**
     * Current status of the payment attempt.
     * In your orchestrator you store it uppercase.
     *
     * Examples:
     * - CREATED
     * - REQUIRES_ACTION
     * - REQUIRES_PAYMENT_METHOD
     * - SUCCEEDED (rarely returned directly)
     * - OFFLINE_PENDING (cash)
     */
    private String status;

    /**
     * Stripe-only (PUBLIC, safe to return to client):
     * Publishable key "pk_..." loaded from the SAME DB config JSON as secret key.
     *
     * Why needed:
     * - Stripe SDK requires publishableKey to be set before presenting PaymentSheet.
     * - In multi-tenant Build4All, each ownerProject may have its own Stripe account/config.
     *
     * Security note:
     * - NEVER return secretKey "sk_..." to client.
     * - publishableKey "pk_..." is safe to return.
     */
    private String publishableKey;

    // ---- Getters / Setters ----

    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

    public String getProviderCode() { return providerCode; }
    public void setProviderCode(String providerCode) { this.providerCode = providerCode; }

    public String getProviderPaymentId() { return providerPaymentId; }
    public void setProviderPaymentId(String providerPaymentId) { this.providerPaymentId = providerPaymentId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    public String getRedirectUrl() { return redirectUrl; }
    public void setRedirectUrl(String redirectUrl) { this.redirectUrl = redirectUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPublishableKey() { return publishableKey; }
    public void setPublishableKey(String publishableKey) { this.publishableKey = publishableKey; }
}
