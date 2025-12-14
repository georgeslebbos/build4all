package com.build4all.admin.domain;

import com.build4all.role.domain.Role;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.build4all.role.repository.RoleRepository;

@Configuration
// Runs on application startup and seeds required roles if missing.
// Useful for local/dev environments and first-time deployments.
public class DataInitializer {

    @Bean
    public CommandLineRunner initRoles(RoleRepository roleRepository) {
        return args -> {
            // Each check avoids duplicate inserts on every restart.
            if (!roleRepository.existsByNameIgnoreCase("SUPER_ADMIN")) {
                roleRepository.save(new Role("SUPER_ADMIN"));
            }
            if (!roleRepository.existsByNameIgnoreCase("MANAGER")) {
                roleRepository.save(new Role("MANAGER"));
            }
            if (!roleRepository.existsByNameIgnoreCase("OWNER")) {
                roleRepository.save(new Role("OWNER"));
            }
            if (!roleRepository.existsByNameIgnoreCase("USER")) {
                roleRepository.save(new Role("USER"));
            }
            if (!roleRepository.existsByNameIgnoreCase("BUSINESS")) {
                roleRepository.save(new Role("BUSINESS"));
            }

            // Tip: If your Role table has a UNIQUE constraint on name (recommended),
            // this seeding becomes safe even under concurrent startups.
        };
    }

}
