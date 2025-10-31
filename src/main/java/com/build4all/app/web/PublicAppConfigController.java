// src/main/java/com/build4all/app/web/PublicAppConfigController.java
package com.build4all.app.web;

import com.build4all.app.config.AppEnvProperties;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.theme.dto.ThemeMobileDTO;
import com.build4all.theme.domain.Theme;
import com.build4all.theme.service.ThemeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/public")
public class PublicAppConfigController {

    private final AppEnvProperties props;
    private final AdminUserProjectRepository aupRepo;
    private final ThemeService themeService;

    public PublicAppConfigController(AppEnvProperties props,
                                     AdminUserProjectRepository aupRepo,
                                     ThemeService themeService) {
        this.props = props;
        this.aupRepo = aupRepo;
        this.themeService = themeService;
    }

    @GetMapping("/app-config")
    public Map<String, Object> getAppConfig() {
        return Map.of(
            "ownerProjectLinkId", props.getOwnerProjectLinkId(),
            "wsPath", props.getWsPath()
        );
    }

    /**
     * Returns the mobile theme for a specific owner+project.
     * If that link has no theme, falls back to the active global theme.
     */
    @GetMapping("/mobile-theme")
    public ResponseEntity<?> themeForOwnerProject(@RequestParam Long ownerId,
                                                  @RequestParam Long projectId) {
        return aupRepo.findByAdmin_AdminIdAndProject_Id(ownerId, projectId)
            .map(link -> {
                Long themeId = link.getThemeId(); // <- stored as Long on AdminUserProject
                Optional<Theme> selectedOrActive = themeService.getSelectedOrActive(themeId);
                return selectedOrActive
                    .<ResponseEntity<?>>map(t -> ResponseEntity.ok(new ThemeMobileDTO(t)))
                    .orElseGet(() -> ResponseEntity.notFound().build());
            })
            .orElseThrow();
    }
}
