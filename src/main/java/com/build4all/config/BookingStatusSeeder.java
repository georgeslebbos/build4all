package com.build4all.config;

import com.build4all.order.domain.OrderStatus;
import com.build4all.order.repository.OrderStatusRepository; // keep if your repo is named *State*
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class BookingStatusSeeder {

    @Bean
    public CommandLineRunner seedBookingStatuses(OrderStatusRepository repo) {
        return args -> {
            System.out.println("✅ BookingStatus seeder running...");

            List<String> names = List.of(
                    "PENDING", "APPROVED", "REJECTED",
                    "CANCEL_REQUESTED", "CANCELED", "COMPLETED", "REFUNDED"
            );

            for (String n : names) {
                if (repo.findByNameIgnoreCase(n).isEmpty()) {
                    repo.save(new OrderStatus(n));
                    System.out.println("   • inserted BookingStatus: " + n);
                }
            }
        };
    }
}
