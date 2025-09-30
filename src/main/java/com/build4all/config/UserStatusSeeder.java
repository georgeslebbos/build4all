package com.build4all.config;

import com.build4all.entities.UserStatus;
import com.build4all.repositories.UserStatusRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class UserStatusSeeder {

    @Bean
    public CommandLineRunner seedUserStatuses(UserStatusRepository userStatusRepository) {
        return args -> {
            System.out.println("✅ UserStatus Seeder running...");

            List<String> statuses = List.of("ACTIVE", "INACTIVE", "DELETED", "PENDING", "CREATED_BY_BUSINESS");


            for (String name : statuses) {
                boolean exists = userStatusRepository.findByName(name).isPresent();
                if (!exists) {
                    userStatusRepository.save(new UserStatus(name));
                    System.out.println("➕ Inserted UserStatus: " + name);
                }
            }
        };
    }
}
