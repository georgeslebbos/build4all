// ✅ DTO class (do not use it as JPA Entity)
package com.build4all.business.dto;

import java.time.LocalDate;

public class BusinessAnalytics {
    private double totalRevenue;
    private String topItem;
    private double orderGrowth;
    private String peakHours;
    private double customerRetention;
    private LocalDate analyticsDate;

    public BusinessAnalytics(double totalRevenue, String topItem, double orderGrowth,
                             String peakHours, double customerRetention, LocalDate analyticsDate) {
        this.totalRevenue = totalRevenue;
        this.topItem = topItem;
        this.orderGrowth = orderGrowth;
        this.peakHours = peakHours;
        this.customerRetention = customerRetention;
        this.analyticsDate = analyticsDate;
    }

    // Getters and setters...
    public double getTotalRevenue() { return totalRevenue; }
    public String getTopItem() { return topItem; }
    public double getorderGrowth() { return orderGrowth; }
    public String getPeakHours() { return peakHours; }
    public double getCustomerRetention() { return customerRetention; }
    public LocalDate getAnalyticsDate() { return analyticsDate; }
}