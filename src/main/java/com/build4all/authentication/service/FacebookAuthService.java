package com.build4all.authentication.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class FacebookAuthService {

    @Value("${facebook.app-id}")
    private String appId;

    @Value("${facebook.app-secret}")
    private String appSecret;

    private final String DEBUG_TOKEN_URL = "https://graph.facebook.com/debug_token";

    public boolean verifyToken(String userAccessToken) {
        RestTemplate restTemplate = new RestTemplate();
        String appAccessToken = appId + "|" + appSecret;

        String url = DEBUG_TOKEN_URL + "?input_token=" + userAccessToken + "&access_token=" + appAccessToken;

        Map response = restTemplate.getForObject(url, Map.class);
        Map data = (Map) response.get("data");

        return data != null && Boolean.TRUE.equals(data.get("is_valid"));
    }

    public Map<String, Object> getUserData(String accessToken) {
        RestTemplate restTemplate = new RestTemplate();
        String fields = "id,name,email,picture";
        String url = "https://graph.facebook.com/me?fields=" + fields + "&access_token=" + accessToken;

        return restTemplate.getForObject(url, Map.class);
    }
}
