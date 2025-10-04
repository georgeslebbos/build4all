package com.build4all.config;

import com.build4all.entities.ItemType;
import com.build4all.entities.Project;
import com.build4all.repositories.ItemTypeRepository;
import com.build4all.repositories.ProjectRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ItemTypeSeeder {

    @Bean
    public CommandLineRunner seedItemTypes(ItemTypeRepository itemTypeRepo,
                                           ProjectRepository projectRepo) {
        return args -> {
            System.out.println("✅ ItemType Seeder running...");

            // Ensure default project exists
            Project defaultProject = projectRepo.findByProjectNameIgnoreCase("Default Project")
                .orElseGet(() -> {
                    Project p = new Project();
                    p.setProjectName("Default Project");
                    p.setDescription("Auto-created for item types");
                    p.setActive(true);
                    return projectRepo.save(p);
                });

            // Full list of ItemTypes
            List<String> defaults = List.of(
                "Football", "Yoga", "Martial Arts", "Hiking", "Horseback Riding", "Fishing",
                "Music", "Dance", "Music Production",
                "Art", "Sculpting", "Knitting", "Calligraphy",
                "Coding", "Robotics", "Three D Printing", "Science Experiments",
                "Fitness", "Self Defense", "Meditation",
                "Cooking", "Travel", "Nature Walks",
                "Gaming", "Board Games",
                "Theater", "Stand Up Comedy", "Storytelling",
                "Language", "Public Speaking", "Writing",
                "Photography", "Film Making",
                "Diy", "Carpentry", "Interior Design",
                "Makeup Beauty",
                "Investment Finance", "Entrepreneurship",
                "Pet Training", "Podcasting", "Magic Tricks",
                "Astronomy", "Public Service", "Productivity",
                "Bird Watching", "Cultural Events"
            );

            for (String name : defaults) {
                if (itemTypeRepo.findByName(name).isEmpty()) {
                    ItemType type = new ItemType();
                    type.setName(name);
                    type.setIcon("default-icon");      // placeholder icon
                    type.setIconLibrary("Ionicons");   // default library
                    type.setProject(defaultProject);
                    itemTypeRepo.save(type);
                    System.out.println("➕ Inserted ItemType: " + name);
                }
            }
        };
    }
}
