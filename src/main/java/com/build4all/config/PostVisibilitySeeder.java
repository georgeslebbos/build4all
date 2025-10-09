package com.build4all.config;

import com.build4all.social.domain.PostVisibility;
import com.build4all.social.repository.PostVisibilityRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class PostVisibilitySeeder {

    @Bean
    public CommandLineRunner seedPostVisibilities(PostVisibilityRepository repo) {
        return args -> {
            System.out.println("✅ PostVisibility seeder running...");

            List<String> visibilities = List.of("PUBLIC", "FRIENDS", "PRIVATE");

            for (String v : visibilities) {
                if (repo.findByNameIgnoreCase(v).isEmpty()) {
                    repo.save(new PostVisibility(v));
                    System.out.println("   • inserted PostVisibility: " + v);
                }
            }
        };
    }
}
