package com.build4all.order.dto;

import java.util.List;

/**
 * CheckoutRequest
 *
 * DTO (Data Transfer Object) sent from the client (Flutter/Web) to:
 *   POST /api/orders/checkout
 *
 * Purpose:
 * - Represents everything needed to convert the user's cart into an Order + OrderItems
 * - Provides info required for pricing (items, shipping, tax, coupon)
 * - Provides payment preferences (payment method + optional Stripe Connect destination account)
 *
 * Typical flow:
 * 1) Client sends CheckoutRequest with lines + address + currency + paymentMethod (+ optional coupon/shipping method)
 * 2) Backend computes final totals (subtotal + shipping + tax - coupon)
 * 3) Backend creates Order + OrderItems
 * 4) Backend starts payment (Stripe/Cash/...) and returns clientSecret/redirectUrl in CheckoutSummaryResponse
 */
public class CheckoutRequest {

    /**
     * Cart lines to purchase.
     * Each line must include:
     * - itemId
     * - quantity
     *
     * In OrderServiceImpl you also enrich each line with:
     * - unitPrice
     * - lineSubtotal
     * so pricing services can use them.
     */
    private List<CartLine> lines;

    /**
     * Selected shipping method ID (optional).
     * - Used when checkout includes delivery/shipping.
     * - Passed to ShippingService to get a quote (cost).
     *
     * Note:
     * - In CheckoutPricingServiceImpl you copy this into shippingAddress.shippingMethodId
     *   so the ShippingService has the selected method.
     */
    private Long shippingMethodId;

    /**
     * Shipping address for delivery and tax calculation (optional).
     * - countryId / regionId are used to load Country/Region entities in OrderServiceImpl
     * - also used by TaxService & ShippingService to compute totals
     */
    private ShippingAddressDTO shippingAddress;

    /**
     * Payment method code chosen by the client.
     * Examples: "STRIPE", "CASH", "PAYPAL"
     *
     * Backend normalizes to UPPERCASE and:
     * - attaches a PaymentMethod entity to the Order header
     * - routes payment initiation to the correct provider via PaymentOrchestratorService
     */
    private String paymentMethod;

    /**
     * ✅ NEW (Stripe Connect / destination charges)
     *
     * Optional Stripe connected account id (starts with acct_...)
     * When present, PaymentOrchestratorService can:
     * - send funds to that connected account
     * - or create payment intents on behalf of that account (depending on your Stripe strategy)
     *
     * When null:
     * - normal platform payment flow (money goes to platform Stripe account)
     */
    private String destinationAccountId;

    /**
     * Legacy field (old flow): stripePaymentId / PaymentIntent id (pi_...)
     *
     * Old model:
     * - Client pays first -> sends succeeded stripePaymentId -> backend creates the order
     *
     * New model (recommended for checkout):
     * - Backend creates the order first -> starts payment -> returns clientSecret
     * - Therefore this field is typically NOT required anymore for /checkout
     *
     * Keep it only if you still support:
     * - old endpoints
     * - or confirm-payment flows where client sends back payment intent id after payment
     */
    private String stripePaymentId;

    /**
     * Currency id for the order (required).
     * Backend loads Currency entity and sets it on Order + OrderItems.
     */
    private Long currencyId;

    /**
     * Coupon code (optional).
     * Pricing service validates it and computes couponDiscount.
     */
    private String couponCode;

    // ---------------- Getters & Setters ----------------

    public List<CartLine> getLines() {
        return lines;
    }

    public void setLines(List<CartLine> lines) {
        this.lines = lines;
    }

    public Long getShippingMethodId() {
        return shippingMethodId;
    }

    public void setShippingMethodId(Long shippingMethodId) {
        this.shippingMethodId = shippingMethodId;
    }

    public ShippingAddressDTO getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(ShippingAddressDTO shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getDestinationAccountId() {
        return destinationAccountId;
    }

    public void setDestinationAccountId(String destinationAccountId) {
        this.destinationAccountId = destinationAccountId;
    }

    public String getStripePaymentId() {
        return stripePaymentId;
    }

    public void setStripePaymentId(String stripePaymentId) {
        this.stripePaymentId = stripePaymentId;
    }

    public Long getCurrencyId() {
        return currencyId;
    }

    public void setCurrencyId(Long currencyId) {
        this.currencyId = currencyId;
    }

    public String getCouponCode() {
        return couponCode;
    }

    public void setCouponCode(String couponCode) {
        this.couponCode = couponCode;
    }
}
