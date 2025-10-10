package com.build4all.booking.dto;

public class BookingDTO {
    private Long id;
    private String itemName;
    private String userEmail;
    private int participants;
    private double totalPrice;
    private String currencySymbol;

    public BookingDTO(Long id, String itemName, String userEmail, int participants, double totalPrice, String currencySymbol) {
        this.id = id;
        this.itemName = itemName;
        this.userEmail = userEmail;
        this.participants = participants;
        this.totalPrice = totalPrice;
        this.currencySymbol = currencySymbol;
    }

    public Long getId() { return id; }
    public String getItemName() { return itemName; }
    public String getUserEmail() { return userEmail; }
    public int getParticipants() { return participants; }
    public double getTotalPrice() { return totalPrice; }
    public String getCurrencySymbol() { return currencySymbol; }

    @Override
    public String toString() {
        return "BookingDTO{" +
                "id=" + id +
                ", itemName='" + itemName + '\'' +
                ", userEmail='" + userEmail + '\'' +
                ", participants=" + participants +
                ", totalPrice=" + totalPrice +
                ", currencySymbol='" + currencySymbol + '\'' +
                '}';
    }
}
