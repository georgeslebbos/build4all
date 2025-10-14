package com.build4all.theme.web;

import com.build4all.theme.dto.CreateThemeRequest;
import com.build4all.theme.dto.ThemeMobileDTO;
import com.build4all.theme.dto.ThemeResponseDTO;
import com.build4all.theme.domain.Theme;
import com.build4all.theme.service.ThemeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/themes")
public class ThemeController {

    @Autowired
    private ThemeService themeService;

    @PutMapping("/{id}/set-active")
    public ResponseEntity<?> setActiveTheme(@PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> ignored) {
        try {
            themeService.setActiveTheme(id);
            return ResponseEntity.ok(Map.of("message", "Theme set as active."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error setting active theme: " + e.getMessage()));
        }
    }

    @GetMapping("/active")
    public ResponseEntity<?> getActiveTheme() {
        return themeService.getActiveTheme()
                .<ResponseEntity<?>>map(t -> ResponseEntity.ok(new ThemeResponseDTO(t)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "No active theme found.")));
    }

    @GetMapping("/active/mobile")
    public ResponseEntity<?> getActiveMobileTheme() {
        return themeService.getActiveTheme()
                .<ResponseEntity<?>>map(t -> ResponseEntity.ok(new ThemeMobileDTO(t)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "No active theme found.")));
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllThemes() {
        var dtos = themeService.getAllThemes().stream()
                .map(ThemeResponseDTO::new).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/all/mobile")
    public ResponseEntity<?> getAllThemesMobile() {
        var dtos = themeService.getAllThemes().stream()
                .map(ThemeMobileDTO::new).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    // IMPORTANT: create uses DTO (maps) not Theme
    @PostMapping("/create")
    public ResponseEntity<?> createTheme(@RequestBody CreateThemeRequest req) {
        try {
            if (req.getName() == null || req.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Name is required."));
            }
            if (themeService.existsByName(req.getName())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "A theme with this name already exists."));
            }
            Theme saved = themeService.createTheme(req);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "Theme created successfully.", "theme", new ThemeResponseDTO(saved)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error creating theme: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/delete")
    public ResponseEntity<?> deleteTheme(@PathVariable Long id) {
        return themeService.getThemeById(id)
                .<ResponseEntity<?>>map(t -> { themeService.deleteTheme(id);
                    return ResponseEntity.ok(Map.of("message", "Theme deleted successfully.")); })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Theme not found.")));
    }

    @PutMapping("/{id}/set-menu-type")
    public ResponseEntity<?> setMenuType(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            String newType = body.get("menuType");
            var opt = themeService.getThemeById(id);
            if (opt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message","Theme not found."));
            Theme theme = opt.get();
            theme.setMenuType(newType);
            themeService.saveTheme(theme);
            return ResponseEntity.ok(Map.of("message", "Menu type updated to '" + newType + "'"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error updating menu type: " + e.getMessage()));
        }
    }

    // Update stays binding to Theme (string fields). Our @JsonSetter now handles object-or-string.
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> updateTheme(@PathVariable Long id, @RequestBody Theme incoming) {
        try {
            Theme updated = themeService.putThemeMerge(id, incoming);
            return ResponseEntity.ok(new ThemeResponseDTO(updated));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error updating theme: " + e.getMessage()));
        }
    }
}
