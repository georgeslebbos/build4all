package com.build4all.order.dto;

public class CashOrderRequest {

    private Long itemId;
    private Long businessUserId;
    private int quantity;
    private boolean wasPaid;
    private Long currencyId;  // optional

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }

    public Long getBusinessUserId() { return businessUserId; }
    public void setBusinessUserId(Long businessUserId) { this.businessUserId = businessUserId; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public boolean isWasPaid() { return wasPaid; }
    public void setWasPaid(boolean wasPaid) { this.wasPaid = wasPaid; }

    public Long getCurrencyId() {
        return currencyId;
    }

    public void setCurrencyId(Long currencyId) {
        this.currencyId = currencyId;
    }
}
