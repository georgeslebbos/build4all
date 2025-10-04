package com.build4all.config;

import com.build4all.entities.BookingStatus;
import com.build4all.repositories.BookingStateRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class BookingStatusSeeder {

    @Bean
    public CommandLineRunner seedBookingStates(BookingStateRepository repo) {
        return args -> {
            System.out.println("✅ BookingState Seeder running...");

            List<String> states = List.of("PENDING", "APPROVED", "REJECTED", "Cancel_Requested", "Canceled", "Completed", "REFUNDED");

            for (String name : states) {
                if (repo.findByNameIgnoreCase(name).isEmpty()) {
                    repo.save(new BookingStatus(name));
                    System.out.println("➕ Inserted BookingState: " + name);
                }
            }
        };
    }
}
