package com.build4all.notifications.service;

import com.build4all.notifications.config.FirebaseConfigStorageProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.Base64;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class FirebaseConfigStorageService {

    private final FirebaseConfigStorageProperties storageProperties;

    public FirebaseConfigStorageService(FirebaseConfigStorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    public String saveAndroidConfig(Long ownerProjectLinkId, MultipartFile file) throws IOException {
        validateLinkId(ownerProjectLinkId);
        validateFile(file, "google-services.json");

        Path target = buildTargetPath(ownerProjectLinkId, "android", "google-services.json");
        Files.createDirectories(target.getParent());
        file.transferTo(target.toFile());

        return target.toAbsolutePath().toString();
    }

    public String saveIosConfig(Long ownerProjectLinkId, MultipartFile file) throws IOException {
        validateLinkId(ownerProjectLinkId);
        validateFile(file, "GoogleService-Info.plist");

        Path target = buildTargetPath(ownerProjectLinkId, "ios", "GoogleService-Info.plist");
        Files.createDirectories(target.getParent());
        file.transferTo(target.toFile());

        return target.toAbsolutePath().toString();
    }

    public byte[] readAndroidConfig(String absolutePath) throws IOException {
        return Files.readAllBytes(Paths.get(absolutePath));
    }

    public byte[] readIosConfig(String absolutePath) throws IOException {
        return Files.readAllBytes(Paths.get(absolutePath));
    }

    private Path buildTargetPath(Long ownerProjectLinkId, String platform, String filename) {
        String root = storageProperties.getRootDir();
        if (root == null || root.isBlank()) {
            throw new IllegalStateException("app.firebase.storage.root-dir is not configured");
        }

        return Paths.get(root, "link-" + ownerProjectLinkId, platform, filename);
    }
    
    public String saveAndroidConfigFromBase64(Long ownerProjectLinkId, String base64Contents) throws IOException {
        validateLinkId(ownerProjectLinkId);

        if (base64Contents == null || base64Contents.isBlank()) {
            throw new IllegalArgumentException("Android Firebase config contents are empty");
        }

        Path target = buildTargetPath(ownerProjectLinkId, "android", "google-services.json");
        Files.createDirectories(target.getParent());

        byte[] decoded = Base64.getDecoder().decode(base64Contents);
        Files.write(target, decoded);

        return target.toAbsolutePath().toString();
    }

    public String saveIosConfigFromBase64(Long ownerProjectLinkId, String base64Contents) throws IOException {
        validateLinkId(ownerProjectLinkId);

        if (base64Contents == null || base64Contents.isBlank()) {
            throw new IllegalArgumentException("iOS Firebase config contents are empty");
        }

        Path target = buildTargetPath(ownerProjectLinkId, "ios", "GoogleService-Info.plist");
        Files.createDirectories(target.getParent());

        byte[] decoded = Base64.getDecoder().decode(base64Contents);
        Files.write(target, decoded);

        return target.toAbsolutePath().toString();
    }

    private void validateLinkId(Long ownerProjectLinkId) {
        if (ownerProjectLinkId == null || ownerProjectLinkId <= 0) {
            throw new IllegalArgumentException("ownerProjectLinkId is invalid");
        }
    }
    

    private void validateFile(MultipartFile file, String expectedName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            throw new IllegalArgumentException("Uploaded file must have a filename");
        }

        if (!original.equalsIgnoreCase(expectedName)) {
            throw new IllegalArgumentException("Expected file name: " + expectedName);
        }
    }
}