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

@Service
public class BusinessAnalyticsService {

    @Autowired
    private OrderItemRepository orderItemRepositoryRepo;

    @Autowired
    private ItemRepository itemRepo;

    @Autowired
    private UsersRepository customerRepo;

    public BusinessAnalytics getAnalyticsForBusiness(Long businessId) {
        BigDecimal revenue = orderItemRepositoryRepo.sumRevenueByBusinessId(businessId);
        double totalRevenue = revenue != null ? revenue.doubleValue() : 0.0;

        String topItem = itemRepo.findTopItemNameByBusinessId(businessId);
        if (topItem == null) {
            topItem = "No orders yet";
        }

        double orderGrowth = calculateorderGrowth(businessId);
        String peakHours = findPeakHours(businessId);
        double retention = calculateCustomerRetention(businessId);

        return new BusinessAnalytics(
                totalRevenue,
                topItem,
                orderGrowth,
                peakHours,
                retention,
                LocalDate.now()
        );
    }

    private double calculateorderGrowth(Long businessId) {
        LocalDate now = LocalDate.now();
        int currentMonth = now.getMonthValue();
        int previousMonth = currentMonth == 1 ? 12 : currentMonth - 1;
        int year = now.getYear();
        int previousYear = currentMonth == 1 ? year - 1 : year;

        long currentorders = orderItemRepositoryRepo.countOrdersByMonthAndYear(businessId, currentMonth, year);
        long previousorders = orderItemRepositoryRepo.countOrdersByMonthAndYear(businessId, previousMonth, previousYear);

        if (previousorders == 0) return currentorders > 0 ? 100.0 : 0.0;

        return ((double)(currentorders - previousorders) / previousorders) * 100.0;
    }

    private String findPeakHours(Long businessId) {
        List<Object[]> result = orderItemRepositoryRepo.findPeakOrderHours(businessId);
        if (result == null || result.isEmpty()) return "No data";

        Number hourValue = (Number) result.get(0)[0];
        int peakHour = hourValue.intValue();

        return String.format("%d:00 - %d:00", peakHour, peakHour + 1);
    }

    private double calculateCustomerRetention(Long businessId) {
        Long total = orderItemRepositoryRepo.countDistinctCustomers(businessId);
        if (total == 0) return 0.0;

        Long returning = orderItemRepositoryRepo.countReturningCustomers(businessId);
        return (returning / (double) total) * 100.0;
    }
}
