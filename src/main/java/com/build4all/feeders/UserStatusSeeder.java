package com.build4all.feeders;

import com.build4all.user.domain.UserStatus;
import com.build4all.user.repository.UserStatusRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class UserStatusSeeder {

    @Bean
    public CommandLineRunner seedUserStatuses(UserStatusRepository repo) {
        return args -> {
            System.out.println("✅ UserStatus seeder running...");

            List<String> statuses = List.of("ACTIVE", "INACTIVE", "BANNED", "LOCKED","PENDING");

            for (String s : statuses) {
                if (repo.findByNameIgnoreCase(s).isEmpty()) {
                    repo.save(new UserStatus(s));
                    System.out.println("   • inserted UserStatus: " + s);
                }
            }
        };
    }
}
