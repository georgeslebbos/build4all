package com.build4all.config;

import com.build4all.entities.FeedType;
import com.build4all.repositories.FeedTypeRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class FeedTypeSeeder {

    @Bean
    public CommandLineRunner seedFeedTypes(FeedTypeRepository feedTypeRepository) {
        return args -> {
            System.out.println("✅ FeedType Seeder running...");

            List<String> types = List.of("Post", "Event", "Activity", "Review");

            for (String name : types) {
                boolean exists = feedTypeRepository.findByName(name).isPresent();
                if (!exists) {
                    feedTypeRepository.save(new FeedType(name)); // ✅ use the single-arg constructor
                    System.out.println("➕ Inserted FeedType: " + name);
                }
            }
        };
    }
}
