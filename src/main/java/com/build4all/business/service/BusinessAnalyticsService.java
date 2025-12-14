package com.build4all.business.service;

import com.build4all.business.dto.BusinessAnalytics;
import com.build4all.order.repository.OrderItemRepository;
import com.build4all.catalog.repository.ItemRepository;
import com.build4all.user.repository.UsersRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service // Spring Service: contains business logic (not controller, not repository)
public class BusinessAnalyticsService {

    @Autowired
    private OrderItemRepository orderItemRepositoryRepo;
    // Repository that should expose aggregate queries on order items
    // Examples you call later:
    // - sumRevenueByBusinessId(businessId)
    // - countOrdersByMonthAndYear(businessId, month, year)
    // - findPeakOrderHours(businessId)
    // - countDistinctCustomers(businessId)
    // - countReturningCustomers(businessId)

    @Autowired
    private ItemRepository itemRepo;
    // Repository used to retrieve the "top item" of a business.
    // Example you call later:
    // - findTopItemNameByBusinessId(businessId)

    @Autowired
    private UsersRepository customerRepo;
    // Currently UNUSED in this service.
    // If you don't need it, remove it to keep the service clean.
    // If you plan to use it for customer-level analytics, keep it.

    /**
     * Returns a computed analytics snapshot for a given business.
     *
     * What this method does:
     * 1) Revenue: SUM of order_item totals for this business
     * 2) Top item: item with highest sales/quantity (based on your repository query)
     * 3) Order growth: month-over-month % change (current month vs previous month)
     * 4) Peak hours: hour window with most orders
     * 5) Retention: returning customers / distinct customers %
     *
     * NOTE: This method relies on repository-level SQL/JPQL aggregate queries.
     */
    public BusinessAnalytics getAnalyticsForBusiness(Long businessId) {

        // ---------------------- 1) Revenue ----------------------
        // Expected repository SQL idea (depends on schema):
        //   SELECT COALESCE(SUM(oi.total_price), 0)
        //   FROM order_items oi
        //   JOIN items i ON i.item_id = oi.item_id
        //   WHERE i.business_id = :businessId;
        //
        // Or if order_items has business_id directly:
        //   SELECT COALESCE(SUM(total_price), 0)
        //   FROM order_items
        //   WHERE business_id = :businessId;
        BigDecimal revenue = orderItemRepositoryRepo.sumRevenueByBusinessId(businessId);

        // Handle null (no orders yet -> SUM returns null in many DBs)
        double totalRevenue = revenue != null ? revenue.doubleValue() : 0.0;

        // ---------------------- 2) Top item ----------------------
        // Expected repository SQL idea:
        //   SELECT i.name
        //   FROM items i
        //   JOIN order_items oi ON oi.item_id = i.item_id
        //   WHERE i.business_id = :businessId
        //   GROUP BY i.name
        //   ORDER BY SUM(oi.quantity) DESC
        //   LIMIT 1;
        //
        // (Could also use SUM(oi.total_price) instead of quantity)
        String topItem = itemRepo.findTopItemNameByBusinessId(businessId);

        // If no item found (no sales), fallback message
        if (topItem == null) {
            topItem = "No orders yet";
        }

        // ---------------------- 3) Order growth ----------------------
        // Month-over-month growth (percentage)
        double orderGrowth = calculateorderGrowth(businessId);

        // ---------------------- 4) Peak hours ----------------------
        // Most frequent ordering hour in the day
        String peakHours = findPeakHours(businessId);

        // ---------------------- 5) Retention ----------------------
        // returning / total distinct customers
        double retention = calculateCustomerRetention(businessId);

        // Return DTO snapshot for today's date
        return new BusinessAnalytics(
                totalRevenue,
                topItem,
                orderGrowth,
                peakHours,
                retention,
                LocalDate.now()
        );
    }

    /**
     * Calculates month-over-month order growth as a percentage.
     *
     * Logic:
     * - currentOrders = number of orders in current month/year
     * - previousOrders = number of orders in previous month/year
     * - if previousOrders == 0:
     *      - if currentOrders > 0 -> 100%
     *      - else -> 0%
     *
     * Expected repository SQL idea:
     *   SELECT COUNT(DISTINCT o.order_id)
     *   FROM orders o
     *   JOIN order_items oi ON oi.order_id = o.order_id
     *   JOIN items i ON i.item_id = oi.item_id
     *   WHERE i.business_id = :businessId
     *     AND EXTRACT(MONTH FROM o.created_at) = :month
     *     AND EXTRACT(YEAR  FROM o.created_at) = :year;
     *
     * NOTE: If your schema stores "business_id" directly on orders or order_items,
     * the query becomes simpler.
     */
    private double calculateorderGrowth(Long businessId) {
        LocalDate now = LocalDate.now();

        int currentMonth = now.getMonthValue();
        int previousMonth = currentMonth == 1 ? 12 : currentMonth - 1;

        int year = now.getYear();
        int previousYear = currentMonth == 1 ? year - 1 : year;

        long currentorders = orderItemRepositoryRepo.countOrdersByMonthAndYear(businessId, currentMonth, year);
        long previousorders = orderItemRepositoryRepo.countOrdersByMonthAndYear(businessId, previousMonth, previousYear);

        // Avoid division by zero
        if (previousorders == 0) return currentorders > 0 ? 100.0 : 0.0;

        // Growth % = ((current - previous) / previous) * 100
        return ((double) (currentorders - previousorders) / previousorders) * 100.0;
    }

    /**
     * Finds the hour window with the highest number of orders.
     *
     * Your repository returns List<Object[]> where:
     * - row[0] = hour (Number)
     * - row[1] = count (optional, depends on your query)
     *
     * Expected repository SQL idea (Postgres example):
     *   SELECT EXTRACT(HOUR FROM o.created_at) AS hour, COUNT(*) AS cnt
     *   FROM orders o
     *   JOIN order_items oi ON oi.order_id = o.order_id
     *   JOIN items i ON i.item_id = oi.item_id
     *   WHERE i.business_id = :businessId
     *   GROUP BY EXTRACT(HOUR FROM o.created_at)
     *   ORDER BY cnt DESC
     *   LIMIT 1;
     *
     * Return format: "H:00 - H+1:00"
     */
    private String findPeakHours(Long businessId) {
        List<Object[]> result = orderItemRepositoryRepo.findPeakOrderHours(businessId);

        // If repository returned nothing -> no analytics
        if (result == null || result.isEmpty()) return "No data";

        // Extract hour column from first row
        Number hourValue = (Number) result.get(0)[0];
        int peakHour = hourValue.intValue();

        // Example: 14 -> "14:00 - 15:00"
        return String.format("%d:00 - %d:00", peakHour, peakHour + 1);
    }

    /**
     * Calculates customer retention percentage for a business.
     *
     * Definitions (based on your repository methods):
     * - total = DISTINCT customers who ordered from this business
     * - returning = customers who ordered more than once (or ordered again after first order)
     *
     * Retention % = (returning / total) * 100
     *
     * Expected repository SQL idea for total distinct customers:
     *   SELECT COUNT(DISTINCT o.user_id)
     *   FROM orders o
     *   JOIN order_items oi ON oi.order_id = o.order_id
     *   JOIN items i ON i.item_id = oi.item_id
     *   WHERE i.business_id = :businessId;
     *
     * Expected repository SQL idea for returning customers (example: >= 2 orders):
     *   SELECT COUNT(*)
     *   FROM (
     *     SELECT o.user_id
     *     FROM orders o
     *     JOIN order_items oi ON oi.order_id = o.order_id
     *     JOIN items i ON i.item_id = oi.item_id
     *     WHERE i.business_id = :businessId
     *     GROUP BY o.user_id
     *     HAVING COUNT(DISTINCT o.order_id) >= 2
     *   ) t;
     */
    private double calculateCustomerRetention(Long businessId) {
        Long total = orderItemRepositoryRepo.countDistinctCustomers(businessId);

        // Defensive: if null or 0 -> no retention can be computed
        if (total == null || total == 0) return 0.0;

        Long returning = orderItemRepositoryRepo.countReturningCustomers(businessId);
        if (returning == null) returning = 0L;

        return (returning / (double) total) * 100.0;
    }
}
