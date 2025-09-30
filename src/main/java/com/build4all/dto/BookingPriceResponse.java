package com.build4all.dto;

import java.math.BigDecimal;

public class BookingPriceResponse {
    private Long bookingId;
    private String activityName;
    private int numberOfParticipants;
    private BigDecimal totalPrice;
    private String currencySymbol;

    // Constructor
    public BookingPriceResponse(Long bookingId, String activityName, int numberOfParticipants,
                                 BigDecimal totalPrice, String currencySymbol) {
        this.bookingId = bookingId;
        this.activityName = activityName;
        this.numberOfParticipants = numberOfParticipants;
        this.totalPrice = totalPrice;
        this.currencySymbol = currencySymbol;
    }

    // Getters and Setters
    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public String getActivityName() {
        return activityName;
    }

    public void setActivityName(String activityName) {
        this.activityName = activityName;
    }

    public int getNumberOfParticipants() {
        return numberOfParticipants;
    }

    public void setNumberOfParticipants(int numberOfParticipants) {
        this.numberOfParticipants = numberOfParticipants;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public String getCurrencySymbol() {
        return currencySymbol;
    }

    public void setCurrencySymbol(String currencySymbol) {
        this.currencySymbol = currencySymbol;
    }
}
