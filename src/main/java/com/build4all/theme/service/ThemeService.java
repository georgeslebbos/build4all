package com.build4all.theme.service;

import com.build4all.theme.domain.Theme;

import com.build4all.theme.repository.ThemeRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.NoSuchElementException;
import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ThemeService {

    @Autowired
    private ThemeRepository themeRepository;

    // === SUPERADMIN: Global Themes ===

@Transactional
public void setActiveTheme(Long id) {
    System.out.println("Deactivating all themes...");
    themeRepository.deactivateAllThemes();

    Theme theme = themeRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Theme not found"));
    System.out.println("Activating theme: " + theme.getName() + " (id: " + id + ")");
    theme.setIsActive(true);
    themeRepository.save(theme);

    // Print all themes and status
    System.out.println("Themes after activation:");
    for (Theme t : themeRepository.findAll()) {
        System.out.println("  Theme " + t.getId() + ": " + t.getName() + " - active: " + t.getIsActive());
    }
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

        // ===== NAME (optional, but if present: validate & unique)
        if (incoming.getName() != null) {
            String newName = incoming.getName().trim();
            if (newName.isEmpty()) throw new IllegalArgumentException("Name cannot be empty.");
            if (!newName.equalsIgnoreCase(theme.getName()) && existsByName(newName)) {
                throw new IllegalArgumentException("A theme with this name already exists.");
            }
            theme.setName(newName);
        }

        // ===== MENU TYPE (optional)
        if (incoming.getMenuType() != null) {
            String mt = incoming.getMenuType().trim().toLowerCase();
            Set<String> allowed = Set.of("bottom", "top", "sandwich");
            if (!allowed.contains(mt)) {
                throw new IllegalArgumentException("menuType must be one of: bottom | top | sandwich");
            }
            theme.setMenuType(mt);
        }

        // ===== JSON FIELDS (optional) — validate JSON before saving
        ObjectMapper mapper = new ObjectMapper();

        if (incoming.getValues() != null) {
            String raw = incoming.getValues().trim();
            // basic sanity: ensure valid JSON
            try { mapper.readTree(raw); } 
            catch (Exception ex) { throw new IllegalArgumentException("values must be valid JSON."); }
            theme.setValues(raw);
        }

        if (incoming.getValuesMobile() != null) {
            String rawMob = incoming.getValuesMobile().trim();
            if (!rawMob.isEmpty()) {
                try { mapper.readTree(rawMob); }
                catch (Exception ex) { throw new IllegalArgumentException("valuesMobile must be valid JSON."); }
                theme.setValuesMobile(rawMob);
            } else {
                // allow clearing? pick behavior. Here: allow null to “unset” mobile overrides.
                theme.setValuesMobile(null);
            }
        }

        // ===== DO NOT COPY: isActive, id, created_at, updated_at (JPA sets timestamps)
        // active toggling goes through setActiveTheme(id)

        return themeRepository.save(theme);
    }
    
    @Transactional
    public int deactivateAllThemes() {
        return themeRepository.deactivateAllThemes();
    }
  
	

}