package com.build4all.notifications.service;

import java.io.InputStream;

public interface FirebaseCredentialResolver {
    InputStream openServiceAccountStream(String secretRef);
}