package com.build4all.notifications.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
public class FrontPushService {

    private final FirebaseAppRegistry firebaseAppRegistry;

    public FrontPushService(FirebaseAppRegistry firebaseAppRegistry) {
        this.firebaseAppRegistry = firebaseAppRegistry;
    }

    public String sendPush(
            Long ownerProjectLinkId,
            String targetToken,
            String title,
            String body,
            Map<String, String> data
    ) throws Exception {

        if (ownerProjectLinkId == null) {
            throw new RuntimeException("ownerProjectLinkId is required");
        }

        if (!StringUtils.hasText(targetToken)) {
            throw new RuntimeException("FCM target token is required");
        }

        String safeTitle = title == null ? "" : title;
        String safeBody = body == null ? "" : body;

        FirebaseApp firebaseApp = firebaseAppRegistry.getOrCreate(ownerProjectLinkId);
        FirebaseMessaging firebaseMessaging = FirebaseMessaging.getInstance(firebaseApp);

        Message.Builder builder = Message.builder()
                .setToken(targetToken)
                .setNotification(Notification.builder()
                        .setTitle(safeTitle)
                        .setBody(safeBody)
                        .build())
                .putData("title", safeTitle)
                .putData("body", safeBody)
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder()
                                .setSound("default")
                                .build())
                        .build());

        if (data != null) {
            data.forEach((k, v) -> {
                if (k != null && v != null) {
                    builder.putData(k, v);
                }
            });
        }

        String response = firebaseMessaging.send(builder.build());
        System.out.println("Front push sent => " + response);
        return response;
    }
}