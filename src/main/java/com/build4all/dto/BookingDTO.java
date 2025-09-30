package com.build4all.dto;

public class BookingDTO {
    private Long id;
    private String activityName;
    private String userEmail;
    private int participants;
    private double totalPrice;
    private String currencySymbol; // ✅ Required for displaying symbol

    // ✅ Constructor including all fields
    public BookingDTO(Long id, String activityName, String userEmail, int participants, double totalPrice, String currencySymbol) {
        this.id = id;
        this.activityName = activityName;
        this.userEmail = userEmail;
        this.participants = participants;
        this.totalPrice = totalPrice;
        this.currencySymbol = currencySymbol;
    }

    // ✅ Getters
    public Long getId() { return id; }
    public String getActivityName() { return activityName; }
    public String getUserEmail() { return userEmail; }
    public int getParticipants() { return participants; }
    public double getTotalPrice() { return totalPrice; }
    public String getCurrencySymbol() { return currencySymbol; }

    // (Optional) toString for debugging
    @Override
    public String toString() {
        return "BookingDTO{" +
                "id=" + id +
                ", activityName='" + activityName + '\'' +
                ", userEmail='" + userEmail + '\'' +
                ", participants=" + participants +
                ", totalPrice=" + totalPrice +
                ", currencySymbol='" + currencySymbol + '\'' +
                '}';
    }
}
