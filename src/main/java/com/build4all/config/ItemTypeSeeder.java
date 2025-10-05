package com.build4all.config;

import com.build4all.entities.Category;
import com.build4all.entities.ItemType;
import com.build4all.entities.Project;
import com.build4all.repositories.CategoryRepository;
import com.build4all.repositories.ItemTypeRepository;
import com.build4all.repositories.ProjectRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class ItemTypeSeeder {

    /** Simple holder for seeding meta */
    private static class Seed {
        final String itemTypeName;   // "Football"
        final String categoryName;   // "SPORTS"
        final String iconName;       // "american-football"
        final String iconLibrary;    // "Ionicons"

        Seed(String itemTypeName, String categoryName, String iconName, String iconLibrary) {
            this.itemTypeName = itemTypeName;
            this.categoryName = categoryName;
            this.iconName = iconName;
            this.iconLibrary = iconLibrary;
        }
    }

    @Bean
    public CommandLineRunner seedItemTypes(ItemTypeRepository itemTypeRepo,
                                           ProjectRepository projectRepo,
                                           CategoryRepository categoryRepo) {
        return args -> {
            System.out.println("✅ ItemType Seeder running...");

            // Ensure default project exists to attach to *categories* (not item types)
            Project defaultProject = projectRepo.findByProjectNameIgnoreCase("Default Project")
                .orElseGet(() -> {
                    Project p = new Project();
                    p.setProjectName("Default Project");
                    p.setDescription("Auto-created for item types");
                    p.setActive(true);
                    return projectRepo.save(p);
                });

            // Default icon per category (used if we need to create a Category on the fly)
            Map<String, String> defaultCategoryIcons = new LinkedHashMap<>();
            defaultCategoryIcons.put("SPORTS",       "basketball");
            defaultCategoryIcons.put("MUSIC",        "musical-notes");
            defaultCategoryIcons.put("ART",          "color-palette");
            defaultCategoryIcons.put("TECH",         "code-slash");
            defaultCategoryIcons.put("FITNESS",      "barbell");
            defaultCategoryIcons.put("COOKING",      "restaurant");
            defaultCategoryIcons.put("TRAVEL",       "globe");
            defaultCategoryIcons.put("GAMING",       "game-controller");
            defaultCategoryIcons.put("THEATER",      "happy");
            defaultCategoryIcons.put("LANGUAGE",     "language");
            defaultCategoryIcons.put("PHOTOGRAPHY",  "camera");
            defaultCategoryIcons.put("DIY",          "construct");
            defaultCategoryIcons.put("BEAUTY",       "color-wand");
            defaultCategoryIcons.put("FINANCE",      "stats-chart");
            defaultCategoryIcons.put("OTHER",        "star");

            // Full seed list for ItemTypes → (category, icon_name, icon_library)
            List<Seed> seeds = List.of(
                // SPORTS
                new Seed("Football",          "SPORTS",      "american-football", "Ionicons"),
                new Seed("Yoga",              "SPORTS",      "leaf",              "Ionicons"),
                new Seed("Martial Arts",      "SPORTS",      "shield",            "Ionicons"),
                new Seed("Hiking",            "SPORTS",      "walk",              "Ionicons"),
                new Seed("Horseback Riding",  "SPORTS",      "paw",               "Ionicons"),
                new Seed("Fishing",           "SPORTS",      "fish",              "Ionicons"),

                // MUSIC
                new Seed("Music",             "MUSIC",       "musical-notes",     "Ionicons"),
                new Seed("Dance",             "MUSIC",       "musical-notes",     "Ionicons"),
                new Seed("Music Production",  "MUSIC",       "musical-note",      "Ionicons"),

                // ART
                new Seed("Art",               "ART",         "color-palette",     "Ionicons"),
                new Seed("Sculpting",         "ART",         "color-palette",     "Ionicons"),
                new Seed("Knitting",          "ART",         "color-palette",     "Ionicons"),
                new Seed("Calligraphy",       "ART",         "color-palette",     "Ionicons"),

                // TECH
                new Seed("Coding",            "TECH",        "code-slash",        "Ionicons"),
                new Seed("Robotics",          "TECH",        "hardware-chip",     "Ionicons"),
                new Seed("Three D Printing",  "TECH",        "cube",              "Ionicons"),
                new Seed("Science Experiments","TECH",       "flask",             "Ionicons"),

                // FITNESS
                new Seed("Fitness",           "FITNESS",     "barbell",           "Ionicons"),
                new Seed("Self Defense",      "FITNESS",     "shield",            "Ionicons"),
                new Seed("Meditation",        "FITNESS",     "moon",              "Ionicons"),

                // COOKING
                new Seed("Cooking",           "COOKING",     "restaurant",        "Ionicons"),

                // TRAVEL
                new Seed("Travel",            "TRAVEL",      "globe",             "Ionicons"),
                new Seed("Nature Walks",      "TRAVEL",      "walk",              "Ionicons"),

                // GAMING
                new Seed("Gaming",            "GAMING",      "game-controller",   "Ionicons"),
                new Seed("Board Games",       "GAMING",      "grid",              "Ionicons"),

                // THEATER
                new Seed("Theater",           "THEATER",     "happy",             "Ionicons"),
                new Seed("Stand Up Comedy",   "THEATER",     "happy-outline",     "Ionicons"),
                new Seed("Storytelling",      "THEATER",     "book",              "Ionicons"),

                // LANGUAGE
                new Seed("Language",          "LANGUAGE",    "language",          "Ionicons"),
                new Seed("Public Speaking",   "LANGUAGE",    "mic",               "Ionicons"),
                new Seed("Writing",           "LANGUAGE",    "pencil",            "Ionicons"),

                // PHOTOGRAPHY
                new Seed("Photography",       "PHOTOGRAPHY", "camera",            "Ionicons"),
                new Seed("Film Making",       "PHOTOGRAPHY", "videocam",          "Ionicons"),

                // DIY
                new Seed("Diy",               "DIY",         "construct",         "Ionicons"),
                new Seed("Carpentry",         "DIY",         "construct",         "Ionicons"),
                new Seed("Interior Design",   "DIY",         "construct",         "Ionicons"),

                // BEAUTY
                new Seed("Makeup Beauty",     "BEAUTY",      "color-wand",        "Ionicons"),

                // FINANCE
                new Seed("Investment Finance","FINANCE",     "stats-chart",       "Ionicons"),
                new Seed("Entrepreneurship",  "FINANCE",     "briefcase",         "Ionicons"),

                // OTHER
                new Seed("Pet Training",      "OTHER",       "paw",               "Ionicons"),
                new Seed("Podcasting",        "OTHER",       "mic-circle",        "Ionicons"),
                new Seed("Magic Tricks",      "OTHER",       "sparkles",          "Ionicons"),
                new Seed("Astronomy",         "OTHER",       "planet",            "Ionicons"),
                new Seed("Public Service",    "OTHER",       "people",            "Ionicons"),
                new Seed("Productivity",      "OTHER",       "time",              "Ionicons"),
                new Seed("Bird Watching",     "OTHER",       "search",            "Ionicons"),
                new Seed("Cultural Events",   "OTHER",       "people",            "Ionicons")
            );

            for (Seed s : seeds) {
                if (itemTypeRepo.findByName(s.itemTypeName).isPresent()) {
                    continue; // already seeded
                }

                // Ensure Category exists; if not, create it (and attach project to Category)
                Category category = categoryRepo.findByNameIgnoreCase(s.categoryName)
                        .orElseGet(() -> {
                            Category i = new Category();
                            i.setName(s.categoryName);
                            String fallbackIcon = defaultCategoryIcons.getOrDefault(s.categoryName, "star");
                            i.setIconName(fallbackIcon);
                            i.setIconLibrary("Ionicons");
                            try {
                                // If you added project to Category, attach default project
                                Category.class.getMethod("setProject", Project.class).invoke(i, defaultProject);
                            } catch (Exception ignore) { /* if project not present on Category */ }
                            return categoryRepo.save(i);
                        });

                // Backfill project on existing Category if missing
                try {
                    var getProject = Category.class.getMethod("getProject");
                    var setProject = Category.class.getMethod("setProject", Project.class);
                    Object proj = getProject.invoke(category);
                    if (proj == null) {
                        setProject.invoke(category, defaultProject);
                        categoryRepo.save(category);
                    }
                } catch (Exception ignore) { /* if project not present on Category */ }

                ItemType t = new ItemType();
                t.setName(s.itemTypeName);
                t.setIcon(s.iconName);          // icon_name (String field)
                t.setIconLibrary(s.iconLibrary);// icon_library
                t.setCategory(category);        // 🚩 NO project on ItemType
                itemTypeRepo.save(t);

                System.out.println("➕ Inserted ItemType: " + s.itemTypeName +
                        " (category=" + s.categoryName +
                        ", icon=" + s.iconName +
                        ", lib=" + s.iconLibrary + ")");
            }
        };
    }
}
