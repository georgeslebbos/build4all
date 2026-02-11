package com.build4all.app.web;

import com.build4all.app.config.AppEnvProperties;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.theme.service.ThemeService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

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
        // ❌ Map.of(...) explodes if any value is null
        // ✅ HashMap allows null values without throwing
        Map<String, Object> body = new HashMap<>();

        body.put("ownerProjectLinkId", props.getOwnerProjectLinkId());
        body.put("wsPath", props.getWsPath());

        return body;
    }
}
