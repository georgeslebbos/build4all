package com.build4all.order.dto;

public class OrderRequestDto {
    private Long itemId;
    private Long businessUserId;
    private int participants;
    private boolean wasPaid;

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }

    public Long getBusinessUserId() { return businessUserId; }
    public void setBusinessUserId(Long businessUserId) { this.businessUserId = businessUserId; }

    public int getParticipants() { return participants; }
    public void setParticipants(int participants) { this.participants = participants; }

    public boolean isWasPaid() { return wasPaid; }
    public void setWasPaid(boolean wasPaid) { this.wasPaid = wasPaid; }
}
