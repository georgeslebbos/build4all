package com.build4all.app.internaltesting.integration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(AppStoreConnectProperties.class)
public class AppStoreConnectConfig {

    @Bean
    public WebClient appStoreConnectWebClient(WebClient.Builder builder, AppStoreConnectProperties properties) {
        return builder
                .baseUrl(properties.getBaseUrl())
                .build();
    }
}