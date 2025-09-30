// ✅ DTO class (do not use it as JPA Entity)
package com.build4all.dto;

import java.time.LocalDate;

public class BusinessAnalytics {
    private double totalRevenue;
    private String topActivity;
    private double bookingGrowth;
    private String peakHours;
    private double customerRetention;
    private LocalDate analyticsDate;

    public BusinessAnalytics(double totalRevenue, String topActivity, double bookingGrowth,
                             String peakHours, double customerRetention, LocalDate analyticsDate) {
        this.totalRevenue = totalRevenue;
        this.topActivity = topActivity;
        this.bookingGrowth = bookingGrowth;
        this.peakHours = peakHours;
        this.customerRetention = customerRetention;
        this.analyticsDate = analyticsDate;
    }

    // Getters and setters...
    public double getTotalRevenue() { return totalRevenue; }
    public String getTopActivity() { return topActivity; }
    public double getBookingGrowth() { return bookingGrowth; }
    public String getPeakHours() { return peakHours; }
    public double getCustomerRetention() { return customerRetention; }
    public LocalDate getAnalyticsDate() { return analyticsDate; }
}