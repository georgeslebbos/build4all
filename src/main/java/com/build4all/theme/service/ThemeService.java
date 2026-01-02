// src/main/java/com/build4all/theme/service/ThemeService.java
package com.build4all.theme.service;

import com.build4all.theme.domain.Theme;
import com.build4all.theme.dto.CreateThemeRequest;
import com.build4all.theme.dto.UpdateThemeRequest;
import com.build4all.theme.repository.ThemeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

@Service
public class ThemeService {

    private final ThemeRepository themeRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ThemeService(ThemeRepository themeRepository) {
        this.themeRepository = themeRepository;
    }

    @Transactional
    public Theme createTheme(CreateThemeRequest req) {
        if (req.getName() == null || req.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Name is required.");
        }

        String name = req.getName().trim();
        if (themeRepository.existsByName(name)) {
            throw new IllegalArgumentException("A theme with this name already exists.");
        }

        if (Boolean.TRUE.equals(req.getIsActive())) {
            themeRepository.deactivateAllThemes();
        }

        String json;
        try {
            if (req.getValues() == null) {
                json = "{}";
            } else {
                json = objectMapper.writeValueAsString(req.getValues());
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid theme values: " + e.getMessage());
        }

        Theme theme = new Theme();
        theme.setName(name);
        theme.setThemeJson(json);
        theme.setIsActive(Boolean.TRUE.equals(req.getIsActive()));

        return themeRepository.save(theme);
    }

    public List<Theme> getAllThemes() {
        return themeRepository.findAll();
    }

    public Optional<Theme> getThemeById(Long id) {
        return themeRepository.findById(id);
    }

    public Optional<Theme> getActiveTheme() {
        return themeRepository.findByIsActiveTrue();
    }

    public boolean existsByName(String name) {
        return themeRepository.existsByName(name);
    }

    @Transactional
    public void deleteTheme(Long id) {
        themeRepository.deleteById(id);
    }

    @Transactional
    public void setActiveTheme(Long id) {
        Theme theme = themeRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Theme not found."));
        themeRepository.deactivateAllThemes();
        theme.setIsActive(true);
        themeRepository.save(theme);
    }

    @Transactional
    public Theme updateTheme(Long id, UpdateThemeRequest req) {
        Theme theme = themeRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Theme not found."));

        // Name
        if (req.getName() != null) {
            String newName = req.getName().trim();
            if (newName.isEmpty()) {
                throw new IllegalArgumentException("Name cannot be empty.");
            }
            if (!newName.equalsIgnoreCase(theme.getName())
                    && themeRepository.existsByName(newName)) {
                throw new IllegalArgumentException("A theme with this name already exists.");
            }
            theme.setName(newName);
        }

        // Active flag
        if (req.getIsActive() != null) {
            if (Boolean.TRUE.equals(req.getIsActive())) {
                themeRepository.deactivateAllThemes();
                theme.setIsActive(true);
            } else {
                theme.setIsActive(false);
            }
        }

        // Values
        if (req.getValues() != null) {
            try {
                String json = objectMapper.writeValueAsString(req.getValues());
                theme.setThemeJson(json);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid theme values: " + e.getMessage());
            }
        }

        return themeRepository.save(theme);
    }
    
    private Map<String, Object> normalizeThemeMap(Map<String, Object> raw) {
        if (raw == null) return Map.of();

        if (raw.containsKey("valuesMobile")) return raw; // already good

        // flat -> wrap
        Map<String, Object> colors = new LinkedHashMap<>();
        Object primary = raw.getOrDefault("primary", "#16A34A");
        colors.put("primary", primary);
        colors.put("onPrimary", raw.getOrDefault("onPrimary", "#FFFFFF"));
        colors.put("background", raw.getOrDefault("background", "#FFFFFF"));
        colors.put("surface", raw.getOrDefault("surface", "#FFFFFF"));
        colors.put("label", raw.getOrDefault("label", "#111827"));
        colors.put("body", raw.getOrDefault("body", "#374151"));
        colors.put("border", raw.getOrDefault("border", primary));
        Object error = raw.getOrDefault("error", "#DC2626");
        colors.put("error", error);
        colors.put("danger", raw.getOrDefault("danger", error));
        colors.put("muted", raw.getOrDefault("muted", "#9CA3AF"));
        colors.put("success", raw.getOrDefault("success", primary));

        Map<String, Object> valuesMobile = new LinkedHashMap<>();
        valuesMobile.put("colors", colors);
        valuesMobile.put("card", raw.getOrDefault("card", Map.of()));
        valuesMobile.put("search", raw.getOrDefault("search", Map.of()));
        valuesMobile.put("button", raw.getOrDefault("button", Map.of()));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("menuType", raw.getOrDefault("menuType", "bottom"));
        root.put("valuesMobile", valuesMobile);

        return root;
    }

}
