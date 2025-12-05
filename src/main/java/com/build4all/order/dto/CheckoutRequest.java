package com.build4all.order.dto;

import java.util.List;

public class CheckoutRequest {

    private List<CartLine> lines;
    private Long shippingMethodId;
    private ShippingAddressDTO shippingAddress;

    private String paymentMethod;
    private String stripePaymentId;

    // 🔹 Add this field to fix request.getCurrencyId()
    private Long currencyId;

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
}
