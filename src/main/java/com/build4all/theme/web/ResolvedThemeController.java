// src/main/java/com/build4all/theme/web/ResolvedThemeController.java
package com.build4all.theme.web;

import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.theme.domain.Theme;
import com.build4all.theme.dto.ThemeMobileDTO;
import com.build4all.theme.repository.ThemeRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    /** GET /api/public/resolved-theme?ownerId=&projectId= */
    @GetMapping("/resolved-theme")
    public ResponseEntity<?> resolvedTheme(@RequestParam Long ownerId, @RequestParam Long projectId) {
        var linkOpt = linkRepo.findByAdmin_AdminIdAndProject_Id(ownerId, projectId);
        Long themeId = linkOpt.map(l -> l.getThemeId()).orElse(null);

        Optional<Theme> theme = (themeId != null)
                ? themeRepo.findById(themeId)
                : themeRepo.findByIsActiveTrue();

        return theme.<ResponseEntity<?>>map(t -> ResponseEntity.ok(new ThemeMobileDTO(t)))
                .orElseGet(() -> ResponseEntity.ok().body(new ThemeMobileDTO(new Theme()))); // "{}"
    }
}
