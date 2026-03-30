package com.build4all.notifications.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;

@Component
public class ClasspathFirebaseCredentialResolver implements FirebaseCredentialResolver {

    @Override
    public InputStream openServiceAccountStream(String secretRef) {
        if (!StringUtils.hasText(secretRef)) {
            throw new RuntimeException("Firebase service account secret ref is required");
        }

        try {
            // 1) try as classpath resource
            ClassPathResource classPathResource = new ClassPathResource(secretRef);
            if (classPathResource.exists()) {
                return classPathResource.getInputStream();
            }

            // 2) try as filesystem path
            FileSystemResource fsResource = new FileSystemResource(secretRef);
            if (fsResource.exists()) {
                return fsResource.getInputStream();
            }

            throw new RuntimeException("Firebase service account file not found: " + secretRef);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read Firebase service account: " + secretRef, e);
        }
    }
}