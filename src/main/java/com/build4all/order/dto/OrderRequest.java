package com.build4all.order.dto;

public class OrderRequest {

    private int participants;
    private String paymentMethod;

    public OrderRequest() {}

    public int getParticipants() { return participants; }
    public void setParticipants(int participants) { this.participants = participants; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
}
