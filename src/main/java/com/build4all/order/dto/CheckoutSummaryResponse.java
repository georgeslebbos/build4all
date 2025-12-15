package com.build4all.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class CheckoutSummaryResponse {

    private Long orderId;
    private LocalDateTime orderDate;

    private BigDecimal itemsSubtotal;
    private BigDecimal shippingTotal;
    private BigDecimal itemTaxTotal;
    private BigDecimal shippingTaxTotal;
    private BigDecimal grandTotal;

    private String currencyCode;
    private String currencySymbol;

    private List<CheckoutLineSummary> lines;

    // 🔹 NEW: coupon fields (do NOT touch existing constructor)
    private String couponCode;
    private BigDecimal couponDiscount;

    private Long paymentTransactionId;
    private String paymentProviderCode;   // STRIPE / CASH / PAYPAL
    private String providerPaymentId;     // pi_... or CASH_ORDER_...
    private String clientSecret;          // Stripe only
    private String redirectUrl;           // PayPal-like providers later
    private String paymentStatus;         // CREATED / OFFLINE_PENDING / ...

    // ===== Constructors =====

    // No-arg constructor (needed by Jackson / JPA / frameworks)
    public CheckoutSummaryResponse() {
    }

    // All-args constructor used in OrderServiceImpl (kept as-is)
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

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public LocalDateTime getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDateTime orderDate) {
        this.orderDate = orderDate;
    }

    public BigDecimal getItemsSubtotal() {
        return itemsSubtotal;
    }

    public void setItemsSubtotal(BigDecimal itemsSubtotal) {
        this.itemsSubtotal = itemsSubtotal;
    }

    public BigDecimal getShippingTotal() {
        return shippingTotal;
    }

    public void setShippingTotal(BigDecimal shippingTotal) {
        this.shippingTotal = shippingTotal;
    }

    public BigDecimal getItemTaxTotal() {
        return itemTaxTotal;
    }

    public void setItemTaxTotal(BigDecimal itemTaxTotal) {
        this.itemTaxTotal = itemTaxTotal;
    }

    public BigDecimal getShippingTaxTotal() {
        return shippingTaxTotal;
    }

    public void setShippingTaxTotal(BigDecimal shippingTaxTotal) {
        this.shippingTaxTotal = shippingTaxTotal;
    }

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    public void setGrandTotal(BigDecimal grandTotal) {
        this.grandTotal = grandTotal;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public String getCurrencySymbol() {
        return currencySymbol;
    }

    public void setCurrencySymbol(String currencySymbol) {
        this.currencySymbol = currencySymbol;
    }

    public List<CheckoutLineSummary> getLines() {
        return lines;
    }

    public void setLines(List<CheckoutLineSummary> lines) {
        this.lines = lines;
    }

    // 🔹 NEW: couponCode / couponDiscount

    public String getCouponCode() {
        return couponCode;
    }

    public void setCouponCode(String couponCode) {
        this.couponCode = couponCode;
    }

    public BigDecimal getCouponDiscount() {
        return couponDiscount;
    }

    public void setCouponDiscount(BigDecimal couponDiscount) {
        this.couponDiscount = couponDiscount;
    }

    public Long getPaymentTransactionId() {
        return paymentTransactionId;
    }

    public String getPaymentProviderCode() {
        return paymentProviderCode;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentTransactionId(Long paymentTransactionId) {
        this.paymentTransactionId = paymentTransactionId;
    }

    public void setPaymentProviderCode(String paymentProviderCode) {
        this.paymentProviderCode = paymentProviderCode;
    }

    public void setProviderPaymentId(String providerPaymentId) {
        this.providerPaymentId = providerPaymentId;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }
}
