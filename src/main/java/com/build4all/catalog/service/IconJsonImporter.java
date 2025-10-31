package com.build4all.catalog.service;

import com.build4all.catalog.domain.Icon;
import com.build4all.catalog.repository.IconRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;

@Service
public class IconJsonImporter {

    public static class ImportStats {
        private int inserted;
        private int skipped;

        public ImportStats(int inserted, int skipped) {
            this.inserted = inserted;
            this.skipped = skipped;
        }
        public int getInserted() { return inserted; }
        public int getSkipped() { return skipped; }
    }

    private final IconRepository repo;
    private final ObjectMapper mapper = new ObjectMapper();

    public IconJsonImporter(IconRepository repo) {
        this.repo = repo;
    }

    /** Auto-detects JSON format and imports. */
    public ImportStats importFile(Resource resource, boolean prefixNames, boolean includeAliases) throws Exception {
        try (InputStream in = resource.getInputStream()) {
            JsonNode root = mapper.readTree(in);

            // Iconify Collection?
            if (root.has("prefix") && (root.has("categories") || root.has("uncategorized"))) {
                return importIconifyCollection(root, prefixNames, includeAliases);
            }

            // Generic formats (array or object with "icons")
            return importGeneric(root, prefixNames);
        }
    }

    /** Generic JSON:
     *  - Array of objects: [{ "name": "...", "library": "..." }, ...]
     *  - Array of strings + default library (not recommended unless you prefix)
     *  - Object: { "library": "Ionicons", "icons": ["home","heart"] }
     */
    private ImportStats importGeneric(JsonNode root, boolean prefixNames) {
        int inserted = 0, skipped = 0;

        if (root.isArray()) {
            for (JsonNode n : root) {
                if (n.isObject()) {
                    String name = text(n, "name");
                    String library = text(n, "library");
                    if (name == null || name.isBlank()) { skipped++; continue; }

                    // if prefixNames && library provided, prefix if not already prefixed
                    String storedName = name;
                    if (prefixNames && library != null && !library.isBlank() && !name.contains(":")) {
                        storedName = toPrefix(library) + ":" + name;
                    }

                    if (repo.existsByNameIgnoreCase(storedName)) { skipped++; continue; }
                    repo.save(new Icon(storedName, library));
                    inserted++;
                } else if (n.isTextual()) {
                    // array of strings: need some default library to prefix or store raw
                    String name = n.asText();
                    if (name == null || name.isBlank()) { skipped++; continue; }
                    // no library known — if prefixNames is ON and name has no ':', we can’t prefix safely here
                    // so just save raw; collisions may occur and be skipped by UNIQUE constraint
                    if (repo.existsByNameIgnoreCase(name)) { skipped++; continue; }
                    repo.save(new Icon(name, null));
                    inserted++;
                }
            }
            return new ImportStats(inserted, skipped);
        }

        if (root.isObject()) {
            // { "library": "Ionicons", "icons": ["home","heart"] }
            String library = text(root, "library");
            JsonNode icons = root.get("icons");
            if (icons != null && icons.isArray()) {
                for (JsonNode n : icons) {
                    String name = n.asText();
                    if (name == null || name.isBlank()) { skipped++; continue; }

                    String storedName = name;
                    if (prefixNames && library != null && !library.isBlank() && !name.contains(":")) {
                        storedName = toPrefix(library) + ":" + name;
                    }

                    if (repo.existsByNameIgnoreCase(storedName)) { skipped++; continue; }
                    repo.save(new Icon(storedName, library));
                    inserted++;
                }
            }
        }

        return new ImportStats(inserted, skipped);
    }

    /** Iconify collection JSON: { prefix, categories{...}, uncategorized[], aliases{...}, ... } */
    private ImportStats importIconifyCollection(JsonNode root, boolean prefixNames, boolean includeAliases) {
        String prefix = text(root, "prefix"); // e.g., "ion"
        String library = guessLibrary(prefix);

        Set<String> names = new LinkedHashSet<>();

        // uncategorized
        JsonNode uncategorized = root.get("uncategorized");
        if (uncategorized != null && uncategorized.isArray()) {
            for (JsonNode n : uncategorized) names.add(n.asText());
        }
        // categories
        JsonNode categories = root.get("categories");
        if (categories != null && categories.isObject()) {
            categories.fields().forEachRemaining(e -> {
                JsonNode arr = e.getValue();
                if (arr != null && arr.isArray()) {
                    for (JsonNode n : arr) names.add(n.asText());
                }
            });
        }
        // aliases (optional)
        if (includeAliases) {
            JsonNode aliases = root.get("aliases");
            if (aliases != null && aliases.isObject()) {
                names.addAll(toKeys(aliases));
            }
        }

        int inserted = 0, skipped = 0;
        for (String name : names) {
            if (name == null || name.isBlank()) { skipped++; continue; }

            String storedName = prefixNames ? prefix + ":" + name : name;

            if (repo.existsByNameIgnoreCase(storedName)) { skipped++; continue; }
            repo.save(new Icon(storedName, library));
            inserted++;
        }
        return new ImportStats(inserted, skipped);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static List<String> toKeys(JsonNode obj) {
        List<String> keys = new ArrayList<>();
        obj.fieldNames().forEachRemaining(keys::add);
        return keys;
    }

    private static String toPrefix(String library) {
        // naive mapping: keep alphanumerics & dashes lowercased
        return library.toLowerCase().replaceAll("[^a-z0-9-]+", "-");
    }

    private static String guessLibrary(String prefix) {
        // simple friendly mapping; customize if you want
        return switch (prefix) {
            case "ion" -> "Ionicons";
            case "mdi" -> "Material Design Icons";
            case "material-symbols" -> "Material Symbols";
            case "fa6-solid" -> "Font Awesome 6 Solid";
            case "fa6-regular" -> "Font Awesome 6 Regular";
            case "fa6-brands" -> "Font Awesome 6 Brands";
            default -> prefix;
        };
    }
}
