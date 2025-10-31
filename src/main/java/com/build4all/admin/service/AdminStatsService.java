package com.build4all.admin.service;

import com.build4all.user.repository.UsersRepository;
import com.build4all.catalog.repository.ItemRepository;
import com.build4all.booking.repository.ItemBookingsRepository;

import com.build4all.review.repository.ReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

@Service
public class AdminStatsService {

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private ItemRepository itemsRepository;
    @Autowired
    private ItemBookingsRepository bookingsRepository;


    @Autowired(required = false)
    private ReviewRepository reviewRepository;

    public Map<String, Long> getStats(String period) {
        LocalDateTime fromDate = switch (period.toLowerCase()) {
            case "week" -> LocalDateTime.now().minusWeeks(1);
            case "month" -> LocalDateTime.now().minusMonths(1);
            default -> LocalDateTime.now().toLocalDate().atStartOfDay();
        };

        Map<String, Long> stats = new HashMap<>();
        stats.put("users", usersRepository.countByCreatedAtAfter(fromDate));
        stats.put("items", itemsRepository.countByCreatedAtAfter(fromDate)); 
        stats.put("bookings", bookingsRepository.countByBookingDatetimeAfter(fromDate));

        if (reviewRepository != null) {
            stats.put("feedback", reviewRepository.countByCreatedAtAfter(fromDate));
        }

        return stats;
    }

    public Map<String, Long> getMonthlyRegistrations() {
        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(5).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);

        List<Object[]> result = usersRepository.countMonthlyRegistrations(sixMonthsAgo);

        Map<String, Long> registrations = new LinkedHashMap<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = YearMonth.now().minusMonths(i);
            registrations.put(ym.toString(), 0L);
        }

        for (Object[] row : result) {
            String monthStr = (String) row[0];
            Long count = ((Number) row[1]).longValue();
            registrations.put(monthStr, count);
        }

        return registrations;
    }

    public List<Map<String, Object>> getPopularItems() { // ✅ بدل getPopularActivities
        List<Object[]> result = itemsRepository.findPopularItems(); // ✅ بدل findPopularActivities

        List<Map<String, Object>> popularItems = new ArrayList<>();
        for (Object[] row : result) {
            Map<String, Object> map = new HashMap<>();
            map.put("name", row[0]);
            map.put("bookings", row[1]);
            map.put("views", row[2]);
            popularItems.add(map);
        }

        return popularItems;
    }
}
