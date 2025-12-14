package com.build4all.feedType;

import com.build4all.feedType.repository.FeedTypeRepository;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

    @Entity
    @Table(name = "feed_types")
    public static class FeedType {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;


        @Column(nullable = false, unique = true)
        private String name;  // Values like "Post", "Event", etc.

        public FeedType() {

        }

        public FeedType(String name) {
            this.name = name;
        }
    }
}
