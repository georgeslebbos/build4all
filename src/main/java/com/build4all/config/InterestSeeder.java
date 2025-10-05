package com.build4all.config;

import com.build4all.entities.Category;
import com.build4all.entities.Project;
import com.build4all.repositories.CategoryRepository;
import com.build4all.repositories.ProjectRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class InterestSeeder {

    @Bean
    public CommandLineRunner seedCategorys(CategoryRepository repo, ProjectRepository projectRepo) {
        return args -> {
            System.out.println("✅ Category Seeder running...");

            // Ensure default project exists (since Category now owns project_id)
            Project defaultProject = projectRepo.findByProjectNameIgnoreCase("Default Project")
                .orElseGet(() -> {
                    Project p = new Project();
                    p.setProjectName("Default Project");
                    p.setDescription("Auto-created for categories");
                    p.setActive(true);
                    return projectRepo.save(p);
                });

            // Category name -> default Ionicons icon
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

            for (Map.Entry<String, String> e : defaults.entrySet()) {
                String name = e.getKey();
                String iconName = e.getValue();

                var existingOpt = repo.findByNameIgnoreCase(name);
                if (existingOpt.isEmpty()) {
                    Category c = new Category();
                    c.setName(name);
                    c.setIconName(iconName);        // icon_name
                    c.setIconLibrary("Ionicons");   // icon_library
                    // ⬇️ attach default project here
                    try {
                        // If you added project to Category:
                        Category.class.getMethod("setProject", Project.class).invoke(c, defaultProject);
                    } catch (Exception ignore) { /* if project not present on Category */ }

                    repo.save(c);
                    System.out.println("➕ Inserted Category: " + name + " (icon=" + iconName + ", lib=Ionicons)");
                } else {
                    // Backfill missing fields (icons / project)
                    Category c = existingOpt.get();
                    boolean changed = false;

                    if (c.getIconName() == null || c.getIconName().isBlank()) {
                        c.setIconName(iconName);
                        changed = true;
                    }
                    if (c.getIconLibrary() == null || c.getIconLibrary().isBlank()) {
                        c.setIconLibrary("Ionicons");
                        changed = true;
                    }

                    try {
                        var getProject = Category.class.getMethod("getProject");
                        var setProject = Category.class.getMethod("setProject", Project.class);
                        Object proj = getProject.invoke(c);
                        if (proj == null) {
                            setProject.invoke(c, defaultProject);
                            changed = true;
                        }
                    } catch (Exception ignore) { /* if project not present on Category */ }

                    if (changed) {
                        repo.save(c);
                        System.out.println("🛠️ Updated Category: " + name + " (backfilled icon/project)");
                    }
                }
            }
        };
    }
}
