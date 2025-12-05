package com.build4all.shipping.dto;

import java.math.BigDecimal;

public class ShippingQuote {

    private Long methodId;
    private String methodName;
    private BigDecimal price;
    private String currencySymbol;

    // ---- No-args constructor (needed for Jackson etc.) ----
    public ShippingQuote() {
    }

    // ---- All-args constructor (used in ShippingServiceImpl) ----
    public ShippingQuote(Long methodId,
                         String methodName,
                         BigDecimal price,
                         String currencySymbol) {
        this.methodId = methodId;
        this.methodName = methodName;
        this.price = price;
        this.currencySymbol = currencySymbol;
    }

    // ---- Getters & Setters ----

    public Long getMethodId() {
        return methodId;
    }

    public void setMethodId(Long methodId) {
        this.methodId = methodId;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getCurrencySymbol() {
        return currencySymbol;
    }

    public void setCurrencySymbol(String currencySymbol) {
        this.currencySymbol = currencySymbol;
    }

    // ---- Backward-compat alias for old code (cost <-> price) ----

    public BigDecimal getCost() {
        return price;
    }

    public void setCost(BigDecimal cost) {
        this.price = cost;
    }
}
