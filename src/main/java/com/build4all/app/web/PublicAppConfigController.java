package com.build4all.app.web;

import com.build4all.app.config.AppEnvProperties;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class PublicAppConfigController {

    private final AppEnvProperties props;

    public PublicAppConfigController(AppEnvProperties props) {
        this.props = props;
    }

    @GetMapping("/app-config")
    public Map<String, Object> getAppConfig() {
        // Only expose non-sensitive fields needed by the client
        return Map.of(
            "ownerProjectLinkId", props.getOwnerProjectLinkId(),  // may be null/empty if not configured
            "wsPath", props.getWsPath()
        );
    }
}
