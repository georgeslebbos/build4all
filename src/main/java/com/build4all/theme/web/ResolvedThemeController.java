// src/main/java/com/build4all/theme/web/ResolvedThemeController.java
package com.build4all.theme.web;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.theme.domain.Theme;
import com.build4all.theme.dto.ThemeMobileDTO;
import com.build4all.theme.repository.ThemeRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/public")
public class ResolvedThemeController {

    private final AdminUserProjectRepository linkRepo;
    private final ThemeRepository themeRepo;

    public ResolvedThemeController(AdminUserProjectRepository linkRepo, ThemeRepository themeRepo) {
        this.linkRepo = linkRepo;
        this.themeRepo = themeRepo;
    }

    /**
     * Resolve the mobile theme:
     * - If slug is provided: use the app row (ownerId, projectId, slug).
     * - Else: search all apps under (ownerId, projectId), pick the most recently updated row
     *   that has a non-null themeId; otherwise fall back to the active global theme.
     *
     * Examples:
     *   /api/public/resolved-theme?ownerId=1&projectId=10&slug=my-app
     *   /api/public/resolved-theme?ownerId=1&projectId=10
     */
    @GetMapping("/resolved-theme")
    public ResponseEntity<ThemeMobileDTO> resolvedTheme(@RequestParam Long ownerId,
                                                        @RequestParam Long projectId,
                                                        @RequestParam(required = false) String slug) {

        // 1) If slug provided → app-scoped resolution
        if (slug != null && !slug.isBlank()) {
            return linkRepo.findByAdmin_AdminIdAndProject_IdAndSlug(ownerId, projectId, slugify(slug))
                .map(AdminUserProject::getThemeId)
                .flatMap(this::findThemeByIdOrActive)
                .map(t -> ResponseEntity.ok(new ThemeMobileDTO(t)))
                .orElseGet(() -> themeRepo.findByIsActiveTrue()
                    .map(t -> ResponseEntity.ok(new ThemeMobileDTO(t)))
                    .orElseGet(() -> ResponseEntity.ok(new ThemeMobileDTO(new Theme()))));
        }

        // 2) No slug → scan all apps for this owner+project
        List<AdminUserProject> apps = linkRepo.findByAdmin_AdminIdAndProject_Id(ownerId, projectId);
        if (apps == null || apps.isEmpty()) {
            // No app rows → fall back to active global theme (or empty DTO)
            return themeRepo.findByIsActiveTrue()
                .map(t -> ResponseEntity.ok(new ThemeMobileDTO(t)))
                .orElseGet(() -> ResponseEntity.ok(new ThemeMobileDTO(new Theme())));
        }

        // Prefer the most recently updated row with a themeId
        Optional<Long> chosenThemeId = apps.stream()
            .filter(a -> a.getThemeId() != null)
            .sorted(Comparator.comparing(ResolvedThemeController::stamp).reversed())
            .map(AdminUserProject::getThemeId)
            .findFirst();

        return chosenThemeId
            .flatMap(this::findThemeByIdOrActive)
            .map(t -> ResponseEntity.ok(new ThemeMobileDTO(t)))
            .orElseGet(() -> themeRepo.findByIsActiveTrue()
                .map(t -> ResponseEntity.ok(new ThemeMobileDTO(t)))
                .orElseGet(() -> ResponseEntity.ok(new ThemeMobileDTO(new Theme()))));
    }

    // ---------- helpers ----------

    private Optional<Theme> findThemeByIdOrActive(Long themeId) {
        if (themeId == null) return themeRepo.findByIsActiveTrue();
        return themeRepo.findById(themeId).or(() -> themeRepo.findByIsActiveTrue());
    }

    private static String slugify(String s) {
        if (s == null) return "app";
        return s.trim().toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-|-$)", "");
    }

    private static LocalDateTime stamp(AdminUserProject a) {
        // prefer updatedAt if present, else createdAt, else epoch
        LocalDateTime u = a.getUpdatedAt();
        if (u != null) return u;
        LocalDateTime c = a.getCreatedAt();
        return c != null ? c : LocalDateTime.MIN;
    }
}
