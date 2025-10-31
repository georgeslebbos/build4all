package com.build4all.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppEnvProperties {

    /** OwnerProjectLinkId used by the mobile app to scope tenant calls */
    private String ownerProjectLinkId;

    /** Optional: websocket path to hand to the app */
    private String wsPath = "/api/ws";

    public String getOwnerProjectLinkId() { return ownerProjectLinkId; }
    public void setOwnerProjectLinkId(String ownerProjectLinkId) { this.ownerProjectLinkId = ownerProjectLinkId; }

    public String getWsPath() { return wsPath; }
    public void setWsPath(String wsPath) { this.wsPath = wsPath; }
}
