package com.build4all.notifications.service;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;

@Component
public class ClasspathFirebaseCredentialResolver implements FirebaseCredentialResolver {

    private final ResourceLoader resourceLoader;

    public ClasspathFirebaseCredentialResolver(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public InputStream openServiceAccountStream(String secretRef) {
        if (!StringUtils.hasText(secretRef)) {
            throw new RuntimeException("Firebase service account secret ref is required");
        }

        try {
            Resource resource = resourceLoader.getResource(secretRef);

            if (!resource.exists()) {
                throw new RuntimeException("Firebase service account resource not found: " + secretRef);
            }

            return resource.getInputStream();

        } catch (IOException e) {
            throw new RuntimeException("Failed to read Firebase service account: " + secretRef, e);
        }
    }
}