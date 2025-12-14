package com.build4all.admin.service;

import com.build4all.user.repository.UsersRepository;
import com.build4all.catalog.repository.ItemRepository;
import com.build4all.order.repository.OrderItemRepository;

import com.build4all.review.repository.ReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

@Service
/**
 * Service responsible for building dashboard statistics for the admin side.
 * It aggregates counts and simple analytics by calling repository methods.
 *
 * Returned structures (Map/List) are convenient to serialize as JSON for charts and counters.
 */
public class AdminStatsService {

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private ItemRepository itemsRepository;

    @Autowired
    private OrderItemRepository OrderItemRepository;

    @Autowired(required = false)
    // Optional repository: if the review module is not included, Spring will inject null (so we check before using).
    private ReviewRepository reviewRepository;

    /**
     * Returns global counters (users/items/orders/feedback) for a given period.
     *
     * @param period accepted values: "week", "month", anything else => today (start of day)
     * @return Map with keys: "users", "items", "orders", and optionally "feedback"
     */
    public Map<String, Long> getStats(String period) {

        // Determine the starting datetime of the period.
        // - week  => now - 7 days
        // - month => now - 1 month
        // - default => today at 00:00
        LocalDateTime fromDate = switch (period.toLowerCase()) {
            case "week" -> LocalDateTime.now().minusWeeks(1);
            case "month" -> LocalDateTime.now().minusMonths(1);
            default -> LocalDateTime.now().toLocalDate().atStartOfDay();
        };

        // "stats" will be returned as JSON object.
        Map<String, Long> stats = new HashMap<>();

        // Count entities created after fromDate (based on each repository's method definition).
        stats.put("users", usersRepository.countByCreatedAtAfter(fromDate));
        stats.put("items", itemsRepository.countByCreatedAtAfter(fromDate));
        stats.put("orders", OrderItemRepository.countByOrderDatetimeAfter(fromDate));

        // Only add feedback if the review module exists in this deployment.
        if (reviewRepository != null) {
            stats.put("feedback", reviewRepository.countByCreatedAtAfter(fromDate));
        }

        return stats;
    }

    /**
     * Returns user registrations grouped by month for the last 6 months (including current month).
     * Output keys are "YYYY-MM" (from YearMonth.toString()) and values are counts.
     */
    public Map<String, Long> getMonthlyRegistrations() {

        // Start from the first day of the month, 5 months ago => gives a 6-month range including current month.
        LocalDateTime sixMonthsAgo = LocalDateTime.now()
                .minusMonths(5)
                .withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);

        // Expected result rows: [monthString, count]
        // monthString should match YearMonth format: "YYYY-MM"
        List<Object[]> result = usersRepository.countMonthlyRegistrations(sixMonthsAgo);

        // LinkedHashMap preserves insertion order, useful for charting.
        Map<String, Long> registrations = new LinkedHashMap<>();

        // Pre-fill the map with the last 6 months and 0 counts,
        // so missing months still appear in the output.
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = YearMonth.now().minusMonths(i);
            registrations.put(ym.toString(), 0L);
        }

        // Override the months returned from the DB with real values.
        for (Object[] row : result) {
            String monthStr = (String) row[0];              // e.g. "2025-12"
            Long count = ((Number) row[1]).longValue();     // convert DB numeric type safely to Long
            registrations.put(monthStr, count);
        }

        return registrations;
    }

    /**
     * Returns a list of popular items with aggregated values (orders + views).
     * Each list element is a Map with keys: "name", "orders", "views".
     */
    public List<Map<String, Object>> getPopularItems() { // ✅ بدل getPopularActivities

        // Expected result rows from query: [itemName, ordersCount, viewsCount]
        List<Object[]> result = itemsRepository.findPopularItems(); // ✅ بدل findPopularActivities

        List<Map<String, Object>> popularItems = new ArrayList<>();
        for (Object[] row : result) {
            Map<String, Object> map = new HashMap<>();
            map.put("name", row[0]);     // item name
            map.put("orders", row[1]);   // aggregated orders count
            map.put("views", row[2]);    // aggregated views count
            popularItems.add(map);
        }

        return popularItems;
    }
}
