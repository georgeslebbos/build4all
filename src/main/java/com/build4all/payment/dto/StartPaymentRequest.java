package com.build4all.payment.dto;

/**
 * StartPaymentRequest is the payload the CLIENT sends to your backend
 * to start a payment attempt for an order.
 *
 * Endpoint:
 * POST /api/payments/start
 *
 * Important:
 * - This request does NOT confirm/complete payment.
 * - It only starts the payment process and returns data like:
 *   - Stripe: clientSecret
 *   - PayPal: redirectUrl (future)
 *   - Cash: offline reference
 */
public class StartPaymentRequest {

    /**
     * Which project/store is being used.
     * This is essential because payment configs are stored per ownerProjectId.
     */
    private Long ownerProjectId;

    /**
     * Your internal order id (Build4All order header id).
     * Used to link the payment transaction to an order.
     */
    private Long orderId;

    /**
     * The payment method code (gateway code).
     * Must match:
     * - PaymentMethod.name (platform catalog)
     * - PaymentGateway.code() implementation
     *
     * Examples: "STRIPE", "CASH", "PAYPAL"
     */
    private String paymentMethod;

    /**
     * Currency code from client.
     * Example: "USD"
     * In orchestrator you normalize to lowercase ("usd") before calling gateway.
     */
    private String currency;

    /**
     * Optional field mainly for marketplace payments (Stripe Connect destination).
     * If provided, StripeGateway will create a destination payment transfer to this account.
     * If null, StripeGateway will create a normal PaymentIntent (platform account).
     */
    private String destinationAccountId;

    /**
     * Amount as String to avoid float/double rounding issues in JSON.
     *
     * Example values:
     * - "49.99"
     * - "100"
     *
     * PaymentController converts it to BigDecimal:
     *   BigDecimal amount = new BigDecimal(req.getAmount());
     *
     * NOTE: If amount is not numeric, BigDecimal will throw an exception
     * (you may want to catch that and return 400).
     */
    private String amount;

    // ---- Getters / Setters ----

    public Long getOwnerProjectId() { return ownerProjectId; }
    public void setOwnerProjectId(Long ownerProjectId) { this.ownerProjectId = ownerProjectId; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getDestinationAccountId() { return destinationAccountId; }
    public void setDestinationAccountId(String destinationAccountId) { this.destinationAccountId = destinationAccountId; }

    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }
}
