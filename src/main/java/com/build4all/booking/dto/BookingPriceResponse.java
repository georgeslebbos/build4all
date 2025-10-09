package com.build4all.booking.dto;

import java.math.BigDecimal;

public class BookingPriceResponse {
    private Long bookingId;
    private String itemName;
    private int numberOfParticipants;
    private BigDecimal totalPrice;
    private String currencySymbol;

    // Constructor
    public BookingPriceResponse(Long bookingId, String itemName, int numberOfParticipants,
                                 BigDecimal totalPrice, String currencySymbol) {
        this.bookingId = bookingId;
        this.itemName = itemName;
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

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
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
