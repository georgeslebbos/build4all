package com.build4all.config;

import com.build4all.services.IconJsonImporter;
import com.build4all.services.IconJsonImporter.ImportStats;
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

    @Value("${icons.import.locations:}")     // e.g. "file:/data/icons/*.json,classpath*:icons/*.json"
    private String locations;

    @Value("${icons.import.dir:}")           // e.g. "/data/icons"  (loads all *.json)
    private String importDir;

    @Value("${icons.import.prefix-names:true}")
    private boolean prefixNames;

    @Value("${icons.import.include-aliases:false}")
    private boolean includeAliases;

    @Bean
    public CommandLineRunner seedIcons(IconJsonImporter importer) {
        return args -> {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            List<Resource> files = new ArrayList<>();

            if (locations != null && !locations.isBlank()) {
                for (String pattern : locations.split(",")) {
                    String p = pattern.trim();
                    if (p.isEmpty()) continue;
                    files.addAll(Arrays.asList(resolver.getResources(p)));
                }
            } else if (importDir != null && !importDir.isBlank()) {
                String base = importDir.trim();
                String pattern = (base.startsWith("classpath:") ? base.replace("classpath:", "classpath*:") : "file:" + base);
                if (!pattern.endsWith("/")) pattern += "/";
                pattern += "*.json";
                files.addAll(Arrays.asList(resolver.getResources(pattern)));
            } else {
                // default: look in classpath folder src/main/resources/icons
                files.addAll(Arrays.asList(resolver.getResources("classpath*:icons/*.json")));
            }

            if (files.isEmpty()) {
                System.out.println("ℹ️ No icon JSON files found to import.");
                return;
            }

            System.out.println("✅ Icon JSON import starting...");
            for (Resource r : files) {
                try {
                    ImportStats stats = importer.importFile(r, prefixNames, includeAliases);
                    System.out.printf("   • %s  -> inserted=%d, skipped=%d%n",
                            r.getFilename(), stats.getInserted(), stats.getSkipped());
                } catch (Exception e) {
                    System.err.printf("   ✖ Failed to import %s: %s%n", r.getDescription(), e.getMessage());
                }
            }
            System.out.println("✅ Icon JSON import finished.");
        };
    }
}
