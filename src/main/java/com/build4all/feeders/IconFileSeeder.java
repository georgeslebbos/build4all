package com.build4all.feeders;

import com.build4all.catalog.service.IconJsonImporter;
import com.build4all.catalog.service.IconJsonImporter.ImportStats;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
public class IconFileSeeder {

    @Value("${icons.import.locations:}")     // e.g. "classpath*:icons/*.json,file:/data/icons/*.json"
    private String locations;

    @Bean
    public CommandLineRunner seedIcons(IconJsonImporter importer) {
        return args -> {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            List<Resource> files = new ArrayList<>();

            if (locations != null && !locations.isBlank()) {
                for (String pattern : locations.split(",")) {
                    String p = pattern.trim();
                    if (!p.isEmpty()) {
                        files.addAll(Arrays.asList(resolver.getResources(p)));
                    }
                }
            } else {
                // default: look in classpath /resources/icons/*.json
                files.addAll(Arrays.asList(resolver.getResources("classpath*:icons/*.json")));
            }

            if (files.isEmpty()) {
                System.out.println("ℹ️ No icon JSON files found to import (looked under /resources/icons).");
                return;
            }

            System.out.println("✅ Icon JSON import starting...");
            for (Resource r : files) {
                try {
                    ImportStats stats = importer.importFile(r, true, false);
                    System.out.printf("   • %s -> inserted=%d, skipped=%d%n",
                            r.getFilename(), stats.getInserted(), stats.getSkipped());
                } catch (Exception e) {
                    System.err.printf("   ✖ Failed to import %s: %s%n", r.getDescription(), e.getMessage());
                }
            }
            System.out.println("✅ Icon JSON import finished.");
        };
    }
}
