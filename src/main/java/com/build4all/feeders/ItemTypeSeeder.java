package com.build4all.feeders;

import com.build4all.catalog.domain.Category;
import com.build4all.catalog.domain.ItemType;
import com.build4all.catalog.repository.CategoryRepository;
import com.build4all.catalog.repository.ItemTypeRepository;
import com.build4all.project.domain.Project;
import com.build4all.project.repository.ProjectRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Configuration
public class ItemTypeSeeder {
/*
    private static class Seed {
        final String itemTypeName;   // e.g., "Football"
        final String categoryName;   // e.g., "SPORTS"
        final String iconName;       // e.g., "american-football"
        final String iconLibrary;    // e.g., "Ionicons"
        Seed(String t, String c, String i, String l) {
            this.itemTypeName = t; this.categoryName = c; this.iconName = i; this.iconLibrary = l;
        }
    }

    @Bean
    public CommandLineRunner seedItemTypes(ItemTypeRepository itemTypeRepo,
                                           CategoryRepository categoryRepo,
                                           ProjectRepository projectRepo) {
        return args -> {
            System.out.println("✅ ItemType seeder running...");

            Project defaultProject = projectRepo.findByProjectNameIgnoreCase("Default Project")
                    .orElseGet(() -> {
                        Project p = new Project();
                        p.setProjectName("Default Project");
                        p.setDescription("Auto-created for item types");
                        p.setActive(true);
                        return projectRepo.save(p);
                    });

            Map<String, String> defaultCategoryIcons = new LinkedHashMap<>();
            defaultCategoryIcons.put("SPORTS", "basketball");
            defaultCategoryIcons.put("MUSIC", "musical-notes");
            defaultCategoryIcons.put("ART", "color-palette");
            defaultCategoryIcons.put("TECH", "code-slash");
            defaultCategoryIcons.put("FITNESS", "barbell");
            defaultCategoryIcons.put("COOKING", "restaurant");
            defaultCategoryIcons.put("TRAVEL", "globe");
            defaultCategoryIcons.put("GAMING", "game-controller");
            defaultCategoryIcons.put("THEATER", "happy");
            defaultCategoryIcons.put("LANGUAGE", "language");
            defaultCategoryIcons.put("PHOTOGRAPHY", "camera");
            defaultCategoryIcons.put("DIY", "construct");
            defaultCategoryIcons.put("BEAUTY", "color-wand");
            defaultCategoryIcons.put("FINANCE", "stats-chart");
            defaultCategoryIcons.put("OTHER", "star");

            List<Seed> seeds = List.of(
                    new Seed("Football", "SPORTS", "american-football", "Ionicons"),
                    new Seed("Yoga", "SPORTS", "leaf", "Ionicons"),
                    new Seed("Martial Arts", "SPORTS", "shield", "Ionicons"),
                    new Seed("Hiking", "SPORTS", "walk", "Ionicons"),
                    new Seed("Fishing", "SPORTS", "fish", "Ionicons"),

                    new Seed("Music", "MUSIC", "musical-notes", "Ionicons"),
                    new Seed("Dance", "MUSIC", "musical-notes", "Ionicons"),
                    new Seed("Music Production", "MUSIC", "musical-note", "Ionicons"),

                    new Seed("Art", "ART", "color-palette", "Ionicons"),
                    new Seed("Sculpting", "ART", "color-palette", "Ionicons"),

                    new Seed("Coding", "TECH", "code-slash", "Ionicons"),
                    new Seed("Robotics", "TECH", "hardware-chip", "Ionicons"),
                    new Seed("3D Printing", "TECH", "cube", "Ionicons"),

                    new Seed("Fitness", "FITNESS", "barbell", "Ionicons"),
                    new Seed("Meditation", "FITNESS", "moon", "Ionicons"),

                    new Seed("Cooking", "COOKING", "restaurant", "Ionicons"),

                    new Seed("Travel", "TRAVEL", "globe", "Ionicons"),

                    new Seed("Gaming", "GAMING", "game-controller", "Ionicons"),

                    new Seed("Theater", "THEATER", "happy", "Ionicons"),

                    new Seed("Language", "LANGUAGE", "language", "Ionicons"),

                    new Seed("Photography", "PHOTOGRAPHY", "camera", "Ionicons"),

                    new Seed("DIY", "DIY", "construct", "Ionicons"),

                    new Seed("Beauty", "BEAUTY", "color-wand", "Ionicons"),

                    new Seed("Finance", "FINANCE", "stats-chart", "Ionicons"),

                    new Seed("Podcasting", "OTHER", "mic-circle", "Ionicons")
            );

            for (Seed s : seeds) {
                // ensure category (under default project) exists
                Category category = categoryRepo
                        .findByNameIgnoreCaseAndProject_Id(s.categoryName, defaultProject.getId())
                        .orElseGet(() -> {
                            Category c = new Category();
                            c.setName(s.categoryName);
                            c.setIconName(defaultCategoryIcons.getOrDefault(s.categoryName, "star"));
                            c.setIconLibrary("Ionicons");
                            c.setProject(defaultProject);
                            return categoryRepo.save(c);
                        });

                // create item type if missing in that category
                Optional<ItemType> existing = itemTypeRepo.findByName(s.itemTypeName);
                if (existing.isEmpty()) {
                    ItemType t = new ItemType();
                    t.setName(s.itemTypeName);
                    t.setIcon(s.iconName);
                    t.setIconLibrary(s.iconLibrary);
                    t.setCategory(category);
                    itemTypeRepo.save(t);
                    System.out.println("   • inserted ItemType: " + s.itemTypeName + " (cat=" + s.categoryName + ")");
                }
            }
        };
    }*/
}
