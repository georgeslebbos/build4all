package com.build4all.notifications.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class FCMService {

    private final String FCM_API_URL = "https://fcm.googleapis.com/fcm/send";

    @Value("${fcm.server.key}")
    private String serverKey;

    public void sendNotification(String targetToken, String title, String body) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "key=" + serverKey);

        Map<String, Object> notification = new HashMap<>();
        notification.put("title", title);
        notification.put("body", body);

        Map<String, Object> payload = new HashMap<>();
        payload.put("to", targetToken);
        payload.put("notification", notification);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        restTemplate.postForEntity(FCM_API_URL, request, String.class);
    }
}
