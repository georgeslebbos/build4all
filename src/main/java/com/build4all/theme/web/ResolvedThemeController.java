// src/main/java/com/build4all/theme/web/ResolvedThemeController.java
package com.build4all.theme.web;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.theme.domain.Theme;
import com.build4all.theme.dto.ThemeResolvedDTO;
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

    public ResolvedThemeController(AdminUserProjectRepository linkRepo,
                                   ThemeRepository themeRepo) {
        this.linkRepo = linkRepo;
        this.themeRepo = themeRepo;
    }

    /**
     * Resolve the theme JSON for an app.
     *
     *  - If slug provided: use that app row (ownerId, projectId, slug).
     *  - Else: look at all apps under (ownerId, projectId), pick the most
     *    recently updated row that has a non-null themeId.
     *  - If no theme found: fall back to active global theme.
     *  - If still nothing: return an empty default "{}".
     *
     * Examples:
     *   /api/public/resolved-theme?ownerId=1&projectId=10&slug=my-app
     *   /api/public/resolved-theme?ownerId=1&projectId=10
     */
    @GetMapping("/resolved-theme")
    public ResponseEntity<ThemeResolvedDTO> resolvedTheme(@RequestParam Long ownerId,
                                                          @RequestParam Long projectId,
                                                          @RequestParam(required = false) String slug) {

        // 1) If slug provided: app-specific
        if (slug != null && !slug.isBlank()) {
            return linkRepo.findByAdmin_AdminIdAndProject_IdAndSlug(ownerId, projectId, slugify(slug))
                    .map(AdminUserProject::getThemeId)
                    .flatMap(this::findThemeByIdOrActive)
                    .map(t -> ResponseEntity.ok(new ThemeResolvedDTO(t)))
                    .orElseGet(this::fallbackThemeResponse);
        }

        // 2) No slug: check all apps for this owner+project
        List<AdminUserProject> apps =
                linkRepo.findByAdmin_AdminIdAndProject_Id(ownerId, projectId);

        if (apps == null || apps.isEmpty()) {
            // No apps: fallback to active or default
            return fallbackThemeResponse();
        }

        // pick most recently updated with a themeId
        Optional<Long> chosenThemeId = apps.stream()
                .filter(a -> a.getThemeId() != null)
                .sorted(Comparator.comparing(ResolvedThemeController::stamp).reversed())
                .map(AdminUserProject::getThemeId)
                .findFirst();

        return chosenThemeId
                .flatMap(this::findThemeByIdOrActive)
                .map(t -> ResponseEntity.ok(new ThemeResolvedDTO(t)))
                .orElseGet(this::fallbackThemeResponse);
    }

    // ---------- helpers ----------

    private Optional<Theme> findThemeByIdOrActive(Long themeId) {
        if (themeId == null) {
            return themeRepo.findByIsActiveTrue();
        }
        return themeRepo.findById(themeId).or(() -> themeRepo.findByIsActiveTrue());
    }

    private ResponseEntity<ThemeResolvedDTO> fallbackThemeResponse() {
        return themeRepo.findByIsActiveTrue()
                .map(t -> ResponseEntity.ok(new ThemeResolvedDTO(t)))
                .orElseGet(() -> ResponseEntity.ok(new ThemeResolvedDTO())); // default "{}"
    }

    private static String slugify(String s) {
        if (s == null) return "app";
        return s.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private static LocalDateTime stamp(AdminUserProject a) {
        LocalDateTime u = a.getUpdatedAt();
        if (u != null) return u;
        LocalDateTime c = a.getCreatedAt();
        return c != null ? c : LocalDateTime.MIN;
    }
}
