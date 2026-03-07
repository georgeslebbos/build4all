package com.build4all.catalog.config;

import com.build4all.catalog.domain.ItemStatus;
import com.build4all.catalog.repository.ItemStatusRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ItemStatusSeeder implements CommandLineRunner {

    private final ItemStatusRepository repository;

    public ItemStatusSeeder(ItemStatusRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {

        createIfNotExists("DRAFT", "Draft", 1);
        createIfNotExists("UPCOMING", "Upcoming", 2);
        createIfNotExists("PUBLISHED", "Published", 3);
        createIfNotExists("ARCHIVED", "Archived", 4);
    }

    private void createIfNotExists(String code, String name, int order) {

        repository.findByCode(code).ifPresentOrElse(
            s -> {},
            () -> repository.save(new ItemStatus(code, name, true, order))
        );
    }
}