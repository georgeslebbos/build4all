package com.build4all.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ci.webhook")
public class CiWebhookProperties {
    /** Optional CI URL; if blank we skip */
    private String url;
    /** Shared secret header; optional */
    private String token;

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}
