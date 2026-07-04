package com.polaroid.storage;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
@Slf4j
public class LocalStorageService implements StorageService {

    @Value("${app.storage.local.directory:.dev-storage}")
    private String storageDirectory;

    @Value("${app.storage.local.bucket:polaroid-glossy}")
    private String bucketName;

    private Path root;

    @PostConstruct
    public void init() {
        root = Path.of(storageDirectory).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root.resolve(bucketName));
        } catch (IOException e) {
            log.warn("Could not create storage directory: {}", e.getMessage());
        }
        log.info("LocalStorageService initialized at: {}", root.resolve(bucketName));
    }

    private Path resolve(String key) {
        Path file = root.resolve(bucketName).resolve(key).normalize();
        if (!file.startsWith(root)) {
            throw new SecurityException("Invalid storage key: path traversal detected");
        }
        return file;
    }

    @Override
    public String upload(String key, byte[] data, String contentType) {
        try {
            Path file = resolve(key);
            Files.createDirectories(file.getParent());
            Files.write(file, data);
            log.debug("Local upload: {}", file);
            return key;
        } catch (IOException e) {
            throw new RuntimeException("Failed to write local file", e);
        }
    }

    @Override
    public byte[] download(String key) {
        try {
            Path file = resolve(key);
            if (Files.exists(file)) {
                return Files.readAllBytes(file);
            }
            return new byte[0];
        } catch (IOException e) {
            log.warn("Failed to read local file {}: {}", key, e.getMessage());
            return new byte[0];
        }
    }

    @Override
    public void delete(String key) {
        try {
            Path file = resolve(key);
            Files.deleteIfExists(file);
            log.debug("Local delete: {}", file);
        } catch (IOException e) {
            log.error("Failed to delete local file {}: {}", key, e.getMessage());
        }
    }

    @Override
    public List<StorageFileInfo> listFiles(String prefix) {
        List<StorageFileInfo> files = new ArrayList<>();
        Path dir = resolve(prefix);
        if (!Files.exists(dir)) return files;

        try (Stream<Path> walk = Files.walk(dir, 1)) {
            walk.filter(Files::isRegularFile).forEach(file -> {
                String key = prefix + "/" + file.getFileName().toString();
                String name = file.getFileName().toString();
                files.add(new StorageFileInfo(name, key, file.toUri().toString()));
            });
        } catch (IOException e) {
            log.warn("Failed to list local files for {}: {}", prefix, e.getMessage());
        }
        return files;
    }

    @Override
    public String getSignedUrl(String key, int expirationSeconds) {
        Path file = resolve(key);
        if (Files.exists(file)) {
            return file.toUri().toString();
        }
        throw new RuntimeException("File not found: " + key);
    }

    @Override
    public long getStorageUsage() {
        try (Stream<Path> walk = Files.walk(root.resolve(bucketName))) {
            return walk.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    public boolean isAvailable() {
        return Files.exists(root.resolve(bucketName));
    }
}
