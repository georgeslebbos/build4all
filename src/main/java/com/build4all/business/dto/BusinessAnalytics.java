// ✅ DTO class (do not use it as JPA Entity)
package com.build4all.business.dto;

import java.time.LocalDate;

public class BusinessAnalytics {
    private double totalRevenue;
    private String topItem;
    private double bookingGrowth;
    private String peakHours;
    private double customerRetention;
    private LocalDate analyticsDate;

    public BusinessAnalytics(double totalRevenue, String topItem, double bookingGrowth,
                             String peakHours, double customerRetention, LocalDate analyticsDate) {
        this.totalRevenue = totalRevenue;
        this.topItem = topItem;
        this.bookingGrowth = bookingGrowth;
        this.peakHours = peakHours;
        this.customerRetention = customerRetention;
        this.analyticsDate = analyticsDate;
    }

    // Getters and setters...
    public double getTotalRevenue() { return totalRevenue; }
    public String getTopItem() { return topItem; }
    public double getBookingGrowth() { return bookingGrowth; }
    public String getPeakHours() { return peakHours; }
    public double getCustomerRetention() { return customerRetention; }
    public LocalDate getAnalyticsDate() { return analyticsDate; }
}