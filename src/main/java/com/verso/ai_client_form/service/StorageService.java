package com.verso.ai_client_form.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StorageService {

    @Value("${app.storage.root:./storage}")
    private String storageRoot;

    public StoredFile store(UUID projectId, MultipartFile file) {
        String originalName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String safeName = originalName.replaceAll("[^A-Za-z0-9._-]", "_");
        String fileName = UUID.randomUUID() + "_" + safeName;

        Path base = Paths.get(storageRoot, projectId.toString());
        try {
            Files.createDirectories(base);
            Path target = base.resolve(fileName);
            file.transferTo(target);
            String relative = projectId + "/" + fileName;
            return new StoredFile(originalName, relative, file.getContentType(), file.getSize());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store file", e);
        }
    }

    public record StoredFile(String originalName, String relativePath, String mimeType, long sizeBytes) {}
}

