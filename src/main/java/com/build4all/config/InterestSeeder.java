package com.build4all.config;

import com.build4all.entities.Interest;
import com.build4all.repositories.InterestRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class InterestSeeder {

	@Bean
	public CommandLineRunner seedInterests(InterestRepository repo) {
	    return args -> {
	        System.out.println("✅ Interest Seeder running...");

	        List<String> defaults = List.of(
	            "SPORTS", "MUSIC", "ART", "TECH", "FITNESS", "COOKING",
	            "TRAVEL", "GAMING", "THEATER", "LANGUAGE", "PHOTOGRAPHY",
	            "DIY", "BEAUTY", "FINANCE", "OTHER"
	        );

	        for (String name : defaults) {
	            if (!repo.existsByNameIgnoreCase(name)) {
	                Interest interest = new Interest();
	                interest.setName(name);
	                interest.setIcon("default-icon");      // placeholder
	                interest.setIconLibrary("Ionicons");   // default library
	                repo.save(interest);
	                System.out.println("➕ Inserted Interest: " + name);
	            }
	        }
	    };
	}

}
