package com.foodshare.service;

import com.foodshare.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Saves donation photos to disk under a random, safe file name. */
@Service
public class FileStorageService {

    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/gif", "gif", "image/webp", "webp");

    private final Path root;

    public FileStorageService(@Value("${foodshare.upload-dir:uploads}") String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /** Validates and stores one image, returns the stored file name. */
    public String storeImage(MultipartFile file) {
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String ext = EXTENSIONS.get(type);
        if (ext == null) {
            throw ApiException.badRequest("Only JPG, PNG, GIF or WebP images are allowed.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("Each image must be 5 MB or smaller.");
        }
        String name = UUID.randomUUID() + "." + ext;
        try {
            Files.createDirectories(root);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, root.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not save the uploaded image", e);
        }
        return name;
    }
}
