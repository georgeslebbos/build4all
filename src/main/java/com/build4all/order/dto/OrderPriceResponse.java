package com.build4all.order.dto;

import java.math.BigDecimal;

public class OrderPriceResponse {
    private Long orderId;
    private String itemName;
    private int numberOfParticipants;
    private BigDecimal totalPrice;
    private String currencySymbol;

    public OrderPriceResponse(Long orderId, String itemName, int numberOfParticipants,
                              BigDecimal totalPrice, String currencySymbol) {
        this.orderId = orderId;
        this.itemName = itemName;
        this.numberOfParticipants = numberOfParticipants;
        this.totalPrice = totalPrice;
        this.currencySymbol = currencySymbol;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public int getNumberOfParticipants() { return numberOfParticipants; }
    public void setNumberOfParticipants(int numberOfParticipants) { this.numberOfParticipants = numberOfParticipants; }

    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }

    public String getCurrencySymbol() { return currencySymbol; }
    public void setCurrencySymbol(String currencySymbol) { this.currencySymbol = currencySymbol; }
}
