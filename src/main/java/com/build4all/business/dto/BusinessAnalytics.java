// ✅ DTO class (do not use it as JPA Entity)
package com.build4all.business.dto;

import java.time.LocalDate;

/**
 * BusinessAnalytics (DTO)
 * ------------------------------------------------------------
 * This is a pure DTO used to RETURN analytics numbers to the client
 * (dashboard, reports, charts...).
 *
 * ✅ It is NOT a database table and should NOT be annotated with @Entity.
 *
 * Typical usage:
 * - Returned by a Service method (calculated from Orders, OrderItems, etc.)
 * - Returned by a Repository projection query (JPQL/Native) using "new BusinessAnalytics(...)"
 *
 * Example (JPQL projection):
 *   SELECT new com.build4all.business.dto.BusinessAnalytics(
 *       SUM(o.totalAmount),
 *       i.name,
 *       :growth,
 *       :peak,
 *       :retention,
 *       CURRENT_DATE
 *   )
 *   FROM Order o ...
 */
public class BusinessAnalytics {

    /**
     * Total revenue for a given period/date.
     * Example meaning: sum of paid orders totals.
     *
     * Equivalent SQL idea:
     *   SELECT SUM(o.total_amount) FROM orders o WHERE ...
     */
    private double totalRevenue;

    /**
     * Name/title of the top-selling item (by quantity or revenue).
     *
     * Equivalent SQL idea:
     *   SELECT i.name FROM order_items oi JOIN items i ...
     *   GROUP BY i.name ORDER BY SUM(oi.quantity) DESC LIMIT 1
     */
    private String topItem;

    /**
     * Growth rate of orders/revenue vs previous period.
     * Example: 0.15 = +15% growth.
     *
     * This is usually computed in service logic or by SQL with 2 periods.
     */
    private double orderGrowth;

    /**
     * Peak hours (string representation) like:
     * - "18:00-21:00"
     * - "Friday 20:00"
     * This is presentation-friendly, not a strict time type.
     */
    private String peakHours;

    /**
     * Customer retention rate for the period.
     * Example: 0.40 = 40% returned customers.
     *
     * Usually computed using distinct customer counts across periods:
     * returning / total.
     */
    private double customerRetention;

    /**
     * The date the analytics snapshot represents.
     * This can be:
     * - "today"
     * - end of week/month
     * - a requested date from client filters
     */
    private LocalDate analyticsDate;

    /**
     * Constructor used for fast creation (and also works well with JPQL projections).
     */
    public BusinessAnalytics(double totalRevenue,
                             String topItem,
                             double orderGrowth,
                             String peakHours,
                             double customerRetention,
                             LocalDate analyticsDate) {
        this.totalRevenue = totalRevenue;
        this.topItem = topItem;
        this.orderGrowth = orderGrowth;
        this.peakHours = peakHours;
        this.customerRetention = customerRetention;
        this.analyticsDate = analyticsDate;
    }

    // Getters (and optional setters if you need JSON deserialization)

    public double getTotalRevenue() { return totalRevenue; }

    public String getTopItem() { return topItem; }

    /**
     * ⚠️ Naming note:
     * Java convention is getOrderGrowth() (capital O).
     * Your method name is getorderGrowth() which still works,
     * but may confuse frameworks/tools (and other developers).
     */
    public double getorderGrowth() { return orderGrowth; }

    public String getPeakHours() { return peakHours; }

    public double getCustomerRetention() { return customerRetention; }

    public LocalDate getAnalyticsDate() { return analyticsDate; }
}
