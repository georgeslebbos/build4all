package com.build4all.catalog.dto;

import java.math.BigDecimal;

public class ItemPriceResponse {

    private Long itemId;
    private String itemName;
    private BigDecimal price;
    private String currencySymbol;

    // Constructor
    public ItemPriceResponse(Long itemId, String itemName, BigDecimal price, String currencySymbol) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.price = price;
        this.currencySymbol = currencySymbol;
    }

    // Getters and Setters
    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
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
}
