package com.build4all.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * CheckoutSummaryResponse
 *
 * DTO returned by:
 *   POST /api/orders/checkout
 *
 * Purpose:
 * - Returns the pricing breakdown (subtotal, shipping, taxes, discount, grand total)
 * - Returns the created order identifiers (orderId, orderDate)
 * - Returns payment bootstrap info (clientSecret / redirectUrl / providerPaymentId)
 *
 * In the NEW flow:
 *   1) Backend prices the cart
 *   2) Backend creates Order + OrderItems (status=PENDING)
 *   3) Backend starts the payment via PaymentOrchestratorService
 *   4) Backend returns this response to the client to complete payment UI
 *
 * Client usage (Flutter):
 * - Show totals to the user (itemsSubtotal, taxes, shipping, discount, grandTotal)
 * - If paymentProviderCode == "STRIPE":
 *     use clientSecret with Stripe SDK to confirm payment
 * - If paymentProviderCode == "PAYPAL" (later):
 *     open redirectUrl in webview/browser
 * - If paymentProviderCode == "CASH":
 *     show offline instructions/reference (providerPaymentId)
 */
public class CheckoutSummaryResponse {

    /* =========================================================
       ORDER HEADER INFO
       ========================================================= */

    /**
     * Database id of the created Order header.
     * - Set in OrderServiceImpl after saving the Order.
     * - Useful for showing "Order #123" in UI and for later status checks.
     */
    private Long orderId;

    /**
     * Creation datetime of the Order header.
     * - Typically set to LocalDateTime.now() when order is created.
     */
    private LocalDateTime orderDate;

    /* =========================================================
       PRICING BREAKDOWN
       ========================================================= */

    /**
     * Sum of (unitPrice * quantity) across all cart lines.
     * Does NOT include shipping or taxes.
     */
    private BigDecimal itemsSubtotal;

    /**
     * Shipping total cost (based on selected shipping method + address).
     * If no shipping method/address, may be 0.
     */
    private BigDecimal shippingTotal;

    /**
     * Total tax computed on items (VAT / sales tax / etc.).
     * Calculated by TaxService based on ownerProject rules and address.
     */
    private BigDecimal itemTaxTotal;

    /**
     * Total tax computed on shipping cost (some regions tax shipping).
     */
    private BigDecimal shippingTaxTotal;

    /**
     * Final amount to pay:
     *   itemsSubtotal + shippingTotal + itemTaxTotal + shippingTaxTotal - couponDiscount
     */
    private BigDecimal grandTotal;

    /* =========================================================
       CURRENCY DISPLAY (for UI convenience)
       ========================================================= */

    /**
     * Currency code (e.g., "USD", "SAR", "LBP").
     * Derived from Currency entity loaded by currencyId.
     */
    private String currencyCode;

    /**
     * Currency symbol (e.g., "$", "﷼").
     */
    private String currencySymbol;

    /* =========================================================
       LINE SUMMARIES (for UI)
       ========================================================= */

    /**
     * Summary list of all lines included in this checkout.
     * Each line includes itemId, quantity, unitPrice, lineSubtotal (and optionally name).
     */
    private List<CheckoutLineSummary> lines;

    /* =========================================================
       COUPON INFO
       (do NOT touch existing constructor as you noted)
       ========================================================= */

    /**
     * Coupon code provided by the user (if any).
     * Note: you may also want to return the validated coupon code (normalized).
     */
    private String couponCode;

    /**
     * Absolute discount amount applied to itemsSubtotal.
     * (Your implementation currently discounts itemsSubtotal only.)
     */
    private BigDecimal couponDiscount;

    /* =========================================================
       PAYMENT BOOTSTRAP INFO (NEW)
       ========================================================= */

    /**
     * Internal PaymentTransaction id (your DB).
     * Useful for:
     * - debugging payment attempts
     * - webhook reconciliation
     * - querying payment status later
     */
    private Long paymentTransactionId;

    /**
     * Provider code used by your payment module.
     * Examples: "STRIPE", "CASH", "PAYPAL"
     *
     * (In your code you return providerCode from PaymentOrchestratorService)
     */
    private String paymentProviderCode;

    /**
     * Provider-side payment id/reference.
     * Examples:
     * - Stripe PaymentIntent id: "pi_..."
     * - Cash reference: "CASH_ORDER_..." (or any offline reference you generate)
     */
    private String providerPaymentId;

    /**
     * Stripe-only: client secret used by Stripe SDK on client side to confirm payment.
     * Example: "pi_123_secret_..."
     *
     * IMPORTANT:
     * - Never log this in plaintext in production.
     * - Only return it to the authenticated user who owns the order.
     */
    private String clientSecret;

    /**
     * Redirect-based providers (PayPal-like) can return a URL to complete payment.
     * Client opens it in a browser/webview and then returns to the app.
     */
    private String redirectUrl;

    /**
     * Payment status returned by your payment module.
     * Typical values depend on your design, e.g.:
     * - "CREATED" (payment created but not completed)
     * - "OFFLINE_PENDING" (cash / bank transfer waiting)
     * - "REQUIRES_ACTION" (3DS step-up)
     * - "SUCCEEDED" (if you ever allow instant confirmation)
     */
    private String paymentStatus;

    // ===== Constructors =====

    /** No-arg constructor (needed by Jackson) */
    public CheckoutSummaryResponse() { }

    /**
     * All-args constructor used earlier in OrderServiceImpl.
     * Keep as-is (you explicitly requested).
     * Note: this constructor does not include coupon/payment fields;
     * those are expected to be set through setters afterwards.
     */
    public CheckoutSummaryResponse(Long orderId,
                                   LocalDateTime orderDate,
                                   BigDecimal itemsSubtotal,
                                   BigDecimal shippingTotal,
                                   BigDecimal itemTaxTotal,
                                   BigDecimal shippingTaxTotal,
                                   BigDecimal grandTotal,
                                   String currencyCode,
                                   String currencySymbol,
                                   List<CheckoutLineSummary> lines) {
        this.orderId = orderId;
        this.orderDate = orderDate;
        this.itemsSubtotal = itemsSubtotal;
        this.shippingTotal = shippingTotal;
        this.itemTaxTotal = itemTaxTotal;
        this.shippingTaxTotal = shippingTaxTotal;
        this.grandTotal = grandTotal;
        this.currencyCode = currencyCode;
        this.currencySymbol = currencySymbol;
        this.lines = lines;
    }

    // ===== Getters & Setters =====

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public LocalDateTime getOrderDate() { return orderDate; }
    public void setOrderDate(LocalDateTime orderDate) { this.orderDate = orderDate; }

    public BigDecimal getItemsSubtotal() { return itemsSubtotal; }
    public void setItemsSubtotal(BigDecimal itemsSubtotal) { this.itemsSubtotal = itemsSubtotal; }

    public BigDecimal getShippingTotal() { return shippingTotal; }
    public void setShippingTotal(BigDecimal shippingTotal) { this.shippingTotal = shippingTotal; }

    public BigDecimal getItemTaxTotal() { return itemTaxTotal; }
    public void setItemTaxTotal(BigDecimal itemTaxTotal) { this.itemTaxTotal = itemTaxTotal; }

    public BigDecimal getShippingTaxTotal() { return shippingTaxTotal; }
    public void setShippingTaxTotal(BigDecimal shippingTaxTotal) { this.shippingTaxTotal = shippingTaxTotal; }

    public BigDecimal getGrandTotal() { return grandTotal; }
    public void setGrandTotal(BigDecimal grandTotal) { this.grandTotal = grandTotal; }

    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }

    public String getCurrencySymbol() { return currencySymbol; }
    public void setCurrencySymbol(String currencySymbol) { this.currencySymbol = currencySymbol; }

    public List<CheckoutLineSummary> getLines() { return lines; }
    public void setLines(List<CheckoutLineSummary> lines) { this.lines = lines; }

    // --- Coupon fields ---

    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }

    public BigDecimal getCouponDiscount() { return couponDiscount; }
    public void setCouponDiscount(BigDecimal couponDiscount) { this.couponDiscount = couponDiscount; }

    // --- Payment fields ---

    public Long getPaymentTransactionId() { return paymentTransactionId; }
    public void setPaymentTransactionId(Long paymentTransactionId) { this.paymentTransactionId = paymentTransactionId; }

    public String getPaymentProviderCode() { return paymentProviderCode; }
    public void setPaymentProviderCode(String paymentProviderCode) { this.paymentProviderCode = paymentProviderCode; }

    public String getProviderPaymentId() { return providerPaymentId; }
    public void setProviderPaymentId(String providerPaymentId) { this.providerPaymentId = providerPaymentId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    public String getRedirectUrl() { return redirectUrl; }
    public void setRedirectUrl(String redirectUrl) { this.redirectUrl = redirectUrl; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
}
