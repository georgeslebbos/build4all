package com.build4all.order.dto;

import java.math.BigDecimal;

public class CheckoutLineSummary {

    private Long itemId;
    private String itemName;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineSubtotal;

    public CheckoutLineSummary() {
    }

    public CheckoutLineSummary(Long itemId,
                               String itemName,
                               int quantity,
                               BigDecimal unitPrice,
                               BigDecimal lineSubtotal) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineSubtotal = lineSubtotal;
    }

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

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getLineSubtotal() {
        return lineSubtotal;
    }

    public void setLineSubtotal(BigDecimal lineSubtotal) {
        this.lineSubtotal = lineSubtotal;
    }
}
