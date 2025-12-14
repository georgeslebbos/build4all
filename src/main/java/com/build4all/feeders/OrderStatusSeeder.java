package com.build4all.feeders;

import com.build4all.order.domain.OrderStatus;
import com.build4all.order.repository.OrderStatusRepository; // keep if your repo is named *State*
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OrderStatusSeeder {

    @Bean
    public CommandLineRunner seedorderStatuses(OrderStatusRepository repo) {
        return args -> {
            System.out.println("✅ orderStatus seeder running...");

            List<String> names = List.of(
                    "PENDING","PROCESSING","ON_HOLD", "APPROVED", "REJECTED",
                    "CANCEL_REQUESTED", "CANCELED", "COMPLETED", "REFUNDED","FAILED"
            );

            for (String n : names) {
                if (repo.findByNameIgnoreCase(n).isEmpty()) {
                    repo.save(new OrderStatus(n));
                    System.out.println("   • inserted orderStatus: " + n);
                }
            }
        };
    }
}
