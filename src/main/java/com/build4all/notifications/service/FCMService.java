package com.build4all.notifications.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FCMService {

    private final FirebaseMessaging firebaseMessaging;

    public FCMService(FirebaseApp firebaseApp) {
        this.firebaseMessaging = FirebaseMessaging.getInstance(firebaseApp);
    }

    public String sendNotification(String targetToken, String title, String body) throws Exception {
        if (!StringUtils.hasText(targetToken)) {
            throw new IllegalArgumentException("FCM target token is required");
        }

        String safeTitle = title == null ? "" : title;
        String safeBody = body == null ? "" : body;

        Message message = Message.builder()
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
                        .build())
                .build();

        String response = firebaseMessaging.send(message);
        System.out.println("Firebase message id => " + response);
        return response;
    }
}