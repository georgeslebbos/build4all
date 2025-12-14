package com.build4all.feeders;

import com.build4all.catalog.domain.Category;
import com.build4all.catalog.repository.CategoryRepository;
import com.build4all.project.domain.Project;
import com.build4all.project.repository.ProjectRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class InterestSeeder {

    @Bean
    public CommandLineRunner seedCategories(CategoryRepository categoryRepo,
                                            ProjectRepository projectRepo) {
        return args -> {
            System.out.println("✅ Category seeder running...");

            Project defaultProject = projectRepo.findByProjectNameIgnoreCase("Default Project")
                    .orElseGet(() -> {
                        Project p = new Project();
                        p.setProjectName("Default Project");
                        p.setDescription("Auto-created for categories");
                        p.setActive(true);
                        return projectRepo.save(p);
                    });

            Map<String, String> defaults = new LinkedHashMap<>();
            defaults.put("SPORTS",       "basketball");
            defaults.put("MUSIC",        "musical-notes");
            defaults.put("ART",          "color-palette");
            defaults.put("TECH",         "code-slash");
            defaults.put("FITNESS",      "barbell");
            defaults.put("COOKING",      "restaurant");
            defaults.put("TRAVEL",       "globe");
            defaults.put("GAMING",       "game-controller");
            defaults.put("THEATER",      "happy");
            defaults.put("LANGUAGE",     "language");
            defaults.put("PHOTOGRAPHY",  "camera");
            defaults.put("DIY",          "construct");
            defaults.put("BEAUTY",       "color-wand");
            defaults.put("FINANCE",      "stats-chart");
            defaults.put("OTHER",        "star");

            for (var e : defaults.entrySet()) {
                String name = e.getKey();
                String icon = e.getValue();

                categoryRepo.findByNameIgnoreCaseAndProject_Id(name, defaultProject.getId())
                        .orElseGet(() -> {
                            Category c = new Category();
                            c.setName(name);
                            c.setIconName(icon);
                            c.setIconLibrary("Ionicons");
                            c.setProject(defaultProject);
                            System.out.println("   • inserted Category: " + name);
                            return categoryRepo.save(c);
                        });
            }
        };
    }
}
