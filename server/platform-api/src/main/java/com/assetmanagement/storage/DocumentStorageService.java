package com.assetmanagement.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

@Component
public class DocumentStorageService {

    private final Path root;

    public DocumentStorageService(
            @Value("${app.storage.documents-root:./.data/documents}") String documentsRoot
    ) throws IOException {
        this.root = Path.of(documentsRoot).toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    public StoredObject store(UUID projectId, MultipartFile file) throws IOException {
        String original = file.getOriginalFilename() == null ? "file.bin" : file.getOriginalFilename();
        String safeName = sanitizeFileName(original);
        String objectKey = projectId + "/" + UUID.randomUUID() + "-" + safeName;
        Path target = resolveObject(objectKey);
        Files.createDirectories(target.getParent());
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        String contentType = file.getContentType() == null || file.getContentType().isBlank()
                ? "application/octet-stream"
                : file.getContentType();
        return new StoredObject("local://" + objectKey, safeName, contentType, Files.size(target));
    }

    public Resource load(String storagePath) throws IOException {
        Path path = resolveStoragePath(storagePath);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IOException("Stored document was not found");
        }
        Resource resource = new UrlResource(path.toUri());
        if (!resource.exists() || !resource.isReadable()) {
            throw new IOException("Stored document is not readable");
        }
        return resource;
    }

    public void deleteQuietly(String storagePath) {
        try {
            Path path = resolveStoragePath(storagePath);
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // best-effort cleanup
        }
    }

    private Path resolveStoragePath(String storagePath) throws IOException {
        if (storagePath == null || !storagePath.startsWith("local://")) {
            throw new IOException("Unsupported storage path");
        }
        String objectKey = storagePath.substring("local://".length());
        return resolveObject(objectKey);
    }

    private Path resolveObject(String objectKey) throws IOException {
        Path resolved = root.resolve(objectKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new IOException("Illegal storage path");
        }
        return resolved;
    }

    private static String sanitizeFileName(String original) {
        String name = original.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[^A-Za-z0-9._\\-\\u4e00-\\u9fff]", "_");
        if (name.isBlank()) {
            name = "file.bin";
        }
        if (name.length() > 180) {
            name = name.substring(name.length() - 180);
        }
        return name.toLowerCase(Locale.ROOT).equals(".gitkeep") ? "file.bin" : name;
    }

    public record StoredObject(String storagePath, String fileName, String contentType, long sizeBytes) {
    }
}
