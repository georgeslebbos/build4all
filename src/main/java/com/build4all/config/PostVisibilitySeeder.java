package com.build4all.config;

import com.build4all.entities.PostVisibility;
import com.build4all.repositories.PostVisibilityRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class PostVisibilitySeeder {

    @Bean
    public CommandLineRunner seedPostVisibilities(PostVisibilityRepository postVisibilityRepository) {
        return args -> {
            System.out.println("✅ PostVisibility Seeder running...");

            List<String> visibilities = List.of("PUBLIC", "FRIENDS_ONLY");

            for (String name : visibilities) {
                boolean exists = postVisibilityRepository.findByName(name).isPresent();
                if (!exists) {
                    postVisibilityRepository.save(new PostVisibility(null, name));
                    System.out.println("➕ Inserted PostVisibility: " + name);
                }
            }
        };
    }
}
