package com.build4all.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.util.UUID;

@Service
public class LocalFileStorageService implements FileStorageService {

    @Value("uploadsPublish")
    private String uploadDir;

 
    @Override
    public String save(MultipartFile file, String folder) {
        try {
            String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
            String ext = "";
            int dot = original.lastIndexOf('.');
            if (dot >= 0) ext = original.substring(dot);

            String name = UUID.randomUUID() + ext;

            Path dir = Paths.get(uploadDir, folder).normalize();
            Files.createDirectories(dir);

            Path target = dir.resolve(name);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            // public URL
            String path = ("/" +uploadDir + "/" + folder + "/" + name).replace("\\", "/");
            return  path;

        } catch (Exception e) {
            throw new RuntimeException("Failed to store file", e);
        }
    }
}
