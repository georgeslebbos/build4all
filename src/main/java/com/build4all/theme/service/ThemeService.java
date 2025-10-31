// src/main/java/com/build4all/theme/service/ThemeService.java
package com.build4all.theme.service;

import com.build4all.theme.domain.Theme;
import com.build4all.theme.dto.CreateThemeRequest;
import com.build4all.theme.repository.ThemeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ThemeService {

    @Autowired
    private ThemeRepository themeRepository;

    @Transactional
    public void setActiveTheme(Long id) {
        themeRepository.deactivateAllThemes();
        Theme theme = themeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Theme not found"));
        theme.setIsActive(true);
        themeRepository.save(theme);
    }

    @Transactional
    public Theme saveTheme(Theme theme) {
        if (Boolean.TRUE.equals(theme.getIsActive())) {
            themeRepository.deactivateAllThemes();
        }
        return themeRepository.save(theme);
    }

    public Optional<Theme> getActiveTheme() {
        return themeRepository.findByIsActiveTrue();
    }

    /** Returns selected if non-null; otherwise the active theme, if any */
    public Optional<Theme> getSelectedOrActive(Long themeId) {
        if (themeId != null) {
            var selected = themeRepository.findById(themeId);
            if (selected.isPresent()) return selected;
        }
        return themeRepository.findByIsActiveTrue();
    }
    
    public List<Theme> getAllThemes() {
        return themeRepository.findAll();
    }

    public Optional<Theme> getThemeById(Long id) {
        return themeRepository.findById(id);
    }

    public void deleteTheme(Long id) {
        themeRepository.deleteById(id);
    }

    public boolean existsByName(String name) {
        return themeRepository.existsByName(name);
    }

    @Transactional
    public boolean updateMenuType(Long id, String menuType) {
        Optional<Theme> themeOpt = themeRepository.findById(id);
        if (themeOpt.isEmpty()) return false;
        Theme theme = themeOpt.get();
        theme.setMenuType(menuType);
        themeRepository.save(theme);
        return true;
    }

    @Transactional
    public Theme putThemeMerge(Long id, Theme incoming) {
        Theme theme = themeRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Theme not found."));

        if (incoming.getName() != null) {
            String newName = incoming.getName().trim();
            if (newName.isEmpty()) throw new IllegalArgumentException("Name cannot be empty.");
            if (!newName.equalsIgnoreCase(theme.getName()) && existsByName(newName)) {
                throw new IllegalArgumentException("A theme with this name already exists.");
            }
            theme.setName(newName);
        }

        if (incoming.getMenuType() != null) {
            String mt = incoming.getMenuType().trim().toLowerCase();
            Set<String> allowed = Set.of("bottom", "top", "sandwich");
            if (!allowed.contains(mt)) {
                throw new IllegalArgumentException("menuType must be one of: bottom | top | sandwich");
            }
            theme.setMenuType(mt);
        }

        if (incoming.getValues() != null) {
            theme.setValues("{}");
        }

        if (incoming.getValuesMobile() != null && incoming.getValuesMobile().trim().isEmpty()) {
            theme.setValuesMobile("{}");
        }

        return themeRepository.save(theme);
    }

    @Transactional
    public int deactivateAllThemes() {
        return themeRepository.deactivateAllThemes();
    }

    @Transactional
    public Theme createTheme(CreateThemeRequest req) {
        if (req.getName() == null || req.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Name is required.");
        }

        String menuType = (req.getMenuType() == null || req.getMenuType().isBlank())
                ? "bottom" : req.getMenuType().trim().toLowerCase();
        Set<String> allowed = Set.of("bottom", "top", "sandwich");
        if (!allowed.contains(menuType)) {
            throw new IllegalArgumentException("menuType must be one of: bottom | top | sandwich");
        }

        if (Boolean.TRUE.equals(req.getIsActive())) {
            themeRepository.deactivateAllThemes();
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            String valuesMobileJson = (req.getValuesMobile() == null)
                    ? "{}"
                    : mapper.writeValueAsString(req.getValuesMobile());

            Theme t = new Theme();
            t.setName(req.getName().trim());
            t.setMenuType(menuType);
            t.setValues("{}");
            t.setValuesMobile(valuesMobileJson);
            t.setIsActive(Boolean.TRUE.equals(req.getIsActive()));

            return themeRepository.save(t);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid valuesMobile: " + e.getMessage());
        }
    }
}
