package com.foodshare.ngo;

import com.foodshare.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stores NGO registration certificates OUTSIDE the public /uploads folder. Files are only reachable through
 * controllers that check who is asking (the NGO itself or an admin). The type is detected from the file's first
 * bytes (PDF, PNG, JPG), not from the name or the browser's content type; the stored name is random.
 */
@Component
public class NgoDocumentStorage {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Pattern STORED_NAME = Pattern.compile("[0-9a-f]{32}\\.(pdf|png|jpg)");
    private final Path root;

    public NgoDocumentStorage(@Value("${sevasetu.private-dir:private-docs}") String dir) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
    }

    /** "pdf", "png", "jpg" or null when the first bytes are none of them. */
    static String detect(byte[] h) {
        if (h.length >= 4 && h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F') return "pdf";
        if (h.length >= 4 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') return "png";
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) return "jpg";
        return null;
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Upload your registration certificate (PDF, PNG or JPG).");
        }
        if (file.getSize() > MAX_BYTES) throw ApiException.badRequest("The document must be 5 MB or smaller.");
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(8);
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read the uploaded file.");
        }
        String ext = detect(head);
        if (ext == null) throw ApiException.badRequest("Only PDF, PNG or JPG documents are accepted.");
        String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(root);
            Files.copy(in, root.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Could not save the document", e);
        }
        return name;
    }

    public Resource load(String name) {
        if (name == null || !STORED_NAME.matcher(name).matches()) throw ApiException.notFound("Document not found.");
        Path p = root.resolve(name).normalize();
        if (!p.startsWith(root) || !Files.isRegularFile(p)) throw ApiException.notFound("Document not found.");
        return new FileSystemResource(p);
    }

    public void delete(String name) {
        if (name == null || !STORED_NAME.matcher(name).matches()) return;
        try {
            Files.deleteIfExists(root.resolve(name).normalize());
        } catch (IOException ignored) {
            // an orphan file is harmless
        }
    }

    public static String contentType(String name) {
        if (name != null && name.endsWith(".pdf")) return "application/pdf";
        if (name != null && name.endsWith(".png")) return "image/png";
        return "image/jpeg";
    }
}
