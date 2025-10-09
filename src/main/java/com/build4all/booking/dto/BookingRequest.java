package com.build4all.booking.dto;

public class BookingRequest {

    private int participants;
    private String paymentMethod;

    public BookingRequest() {
    }

    public int getParticipants() {
        return participants;
    }

    public void setParticipants(int participants) {
        this.participants = participants;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

	
}
