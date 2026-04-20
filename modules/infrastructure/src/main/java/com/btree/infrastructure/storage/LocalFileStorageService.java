package com.btree.infrastructure.storage;

import com.btree.infrastructure.config.LocalStorageProperties;
import com.btree.shared.contract.FileStorageService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Component
public class LocalFileStorageService implements FileStorageService {

    private final LocalStorageProperties properties;
    private Path uploadPath;

    public LocalFileStorageService(final LocalStorageProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() throws IOException {
        uploadPath = Paths.get(properties.getUploadDir()).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);
    }

    @Override
    public String store(
            final String originalFilename,
            final InputStream content,
            final long contentLength,
            final String contentType
    ) {
        final String objectName = UUID.randomUUID() + extractExtension(originalFilename);
        final Path destination = uploadPath.resolve(objectName);

        try {
            Files.copy(content, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Falha ao armazenar o arquivo: " + e.getMessage(), e);
        }

        return properties.getBaseUrl() + "/uploads/" + objectName;
    }

    private String extractExtension(final String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.')).toLowerCase();
    }
}
