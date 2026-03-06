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

    private Long orderId;
    private LocalDateTime orderDate;

    /* =========================================================
       PRICING BREAKDOWN
       ========================================================= */

    private BigDecimal itemsSubtotal;
    private BigDecimal shippingTotal;
    private BigDecimal itemTaxTotal;
    private BigDecimal shippingTaxTotal;
    private BigDecimal grandTotal;

    /* =========================================================
       CURRENCY DISPLAY (for UI convenience)
       ========================================================= */

    private String currencyCode;
    private String currencySymbol;

    private String orderCode;
    private Long orderSeq;

    /* =========================================================
       LINE SUMMARIES (for UI)
       ========================================================= */

    private List<CheckoutLineSummary> lines;

    /* =========================================================
       COUPON INFO
       ========================================================= */

    private String couponCode;
    private BigDecimal couponDiscount;

    /* =========================================================
       CLEAN UI MESSAGE
       ========================================================= */

    private String message;

    /* =========================================================
       PAYMENT BOOTSTRAP INFO (NEW)
       ========================================================= */

    private Long paymentTransactionId;
    private String paymentProviderCode;
    private String providerPaymentId;
    private String clientSecret;
    private String publishableKey;
    private String redirectUrl;
    private String paymentStatus;

    // ===== Constructors =====

    public CheckoutSummaryResponse() { }

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

    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }

    public Long getOrderSeq() { return orderSeq; }
    public void setOrderSeq(Long orderSeq) { this.orderSeq = orderSeq; }

    public BigDecimal getGrandTotal() { return grandTotal; }
    public void setGrandTotal(BigDecimal grandTotal) { this.grandTotal = grandTotal; }

    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }

    public String getCurrencySymbol() { return currencySymbol; }
    public void setCurrencySymbol(String currencySymbol) { this.currencySymbol = currencySymbol; }

    public List<CheckoutLineSummary> getLines() { return lines; }
    public void setLines(List<CheckoutLineSummary> lines) { this.lines = lines; }

    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }

    public BigDecimal getCouponDiscount() { return couponDiscount; }
    public void setCouponDiscount(BigDecimal couponDiscount) { this.couponDiscount = couponDiscount; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Long getPaymentTransactionId() { return paymentTransactionId; }
    public void setPaymentTransactionId(Long paymentTransactionId) { this.paymentTransactionId = paymentTransactionId; }

    public String getPaymentProviderCode() { return paymentProviderCode; }
    public void setPaymentProviderCode(String paymentProviderCode) { this.paymentProviderCode = paymentProviderCode; }

    public String getProviderPaymentId() { return providerPaymentId; }
    public void setProviderPaymentId(String providerPaymentId) { this.providerPaymentId = providerPaymentId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    public String getPublishableKey() { return publishableKey; }
    public void setPublishableKey(String publishableKey) { this.publishableKey = publishableKey; }

    public String getRedirectUrl() { return redirectUrl; }
    public void setRedirectUrl(String redirectUrl) { this.redirectUrl = redirectUrl; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
}