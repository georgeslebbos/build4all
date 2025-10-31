package com.build4all.config;

import com.build4all.business.domain.BusinessStatus;
import com.build4all.business.repository.BusinessStatusRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class BusinessStatusSeeder {

    @Bean
    public CommandLineRunner seedBusinessStatuses(BusinessStatusRepository repo) {
        return args -> {
            System.out.println("✅ BusinessStatus seeder running...");

            List<String> names = List.of("ACTIVE", "INACTIVE", "SUSPENDED", "PENDING_APPROVAL");

            for (String n : names) {
                if (repo.findByNameIgnoreCase(n).isEmpty()) {
                    repo.save(new BusinessStatus(n));
                    System.out.println("   • inserted BusinessStatus: " + n);
                }
            }
        };
    }
}
