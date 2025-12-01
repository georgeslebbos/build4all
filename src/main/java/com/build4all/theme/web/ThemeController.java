// src/main/java/com/build4all/theme/web/ThemeController.java
package com.build4all.theme.web;

import com.build4all.theme.domain.Theme;
import com.build4all.theme.dto.CreateThemeRequest;
import com.build4all.theme.dto.ThemeResponseDTO;
import com.build4all.theme.dto.UpdateThemeRequest;
import com.build4all.theme.service.ThemeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/themes")
public class ThemeController {

    private final ThemeService themeService;

    public ThemeController(ThemeService themeService) {
        this.themeService = themeService;
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllThemes() {
        var dtos = themeService.getAllThemes().stream()
                .map(ThemeResponseDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getTheme(@PathVariable Long id) {
        return themeService.getThemeById(id)
                .<ResponseEntity<?>>map(t -> ResponseEntity.ok(new ThemeResponseDTO(t)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Theme not found.")));
    }

    @GetMapping("/active")
    public ResponseEntity<?> getActiveTheme() {
        return themeService.getActiveTheme()
                .<ResponseEntity<?>>map(t -> ResponseEntity.ok(new ThemeResponseDTO(t)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "No active theme found.")));
    }

    @PostMapping("/create")
    public ResponseEntity<?> createTheme(@RequestBody CreateThemeRequest req) {
        try {
            Theme saved = themeService.createTheme(req);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "message", "Theme created successfully.",
                            "theme", new ThemeResponseDTO(saved)
                    ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error creating theme: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateTheme(@PathVariable Long id,
                                         @RequestBody UpdateThemeRequest req) {
        try {
            Theme updated = themeService.updateTheme(id, req);
            return ResponseEntity.ok(new ThemeResponseDTO(updated));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error updating theme: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}/set-active")
    public ResponseEntity<?> setActiveTheme(@PathVariable Long id) {
        try {
            themeService.setActiveTheme(id);
            return ResponseEntity.ok(Map.of("message", "Theme set as active."));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", ex.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error setting active theme: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTheme(@PathVariable Long id) {
        try {
            themeService.deleteTheme(id);
            return ResponseEntity.ok(Map.of("message", "Theme deleted successfully."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error deleting theme: " + e.getMessage()));
        }
    }
}
