package com.authenticself.storage.local;

import com.authenticself.storage.ObjectStorageService;
import com.authenticself.storage.PutResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Local-filesystem implementation of {@link ObjectStorageService} for dev / CI.
 * <p>
 * Root directory comes from {@code app.storage.local.root} (env-overridable,
 * defaults to {@code ./var/object-storage}); the path is created on startup.
 * Keys are joined directly under the root — the caller is responsible for
 * producing safe keys (controller-side we build them from server-generated
 * roomIds, never from client filenames, per FR-3 / NFR security).
 */
@Service
public class LocalFileSystemObjectStorageService implements ObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileSystemObjectStorageService.class);

    private final Path root;

    public LocalFileSystemObjectStorageService(
            @Value("${app.storage.local.root:./var/object-storage}") String rootPath) {
        this.root = Paths.get(rootPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Cannot create local object-storage root: " + this.root, e);
        }
        log.info("LocalFileSystemObjectStorageService ready at {}", this.root);
    }

    @Override
    public PutResult upload(String objectKey, byte[] bytes, String contentType) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey must not be blank");
        }
        if (bytes == null) {
            throw new IllegalArgumentException("bytes must not be null");
        }
        Path target = resolveSafely(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(
                    target,
                    bytes,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new StorageIoException("Failed to write object " + objectKey, e);
        }
        String url = target.toUri().toString(); // file:///...
        return new PutResult(objectKey, url, bytes.length);
    }

    @Override
    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        Path target = resolveSafely(objectKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            // Compensating delete — log but do not throw. Out-of-scope task:
            // a future "storage GC" will reconcile orphans.
            log.warn("Failed to delete object {} (path {}): {}", objectKey, target, e.toString());
        }
    }

    @Override
    public boolean exists(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return false;
        }
        return Files.exists(resolveSafely(objectKey));
    }

    /**
     * Resolve {@code objectKey} under the configured root while rejecting
     * path traversal ({@code ../}) attempts.
     */
    private Path resolveSafely(String objectKey) {
        Path resolved = root.resolve(objectKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException(
                    "objectKey resolves outside storage root: " + objectKey);
        }
        return resolved;
    }

    /** Wrap IO failures so higher layers can map them to STORAGE_PERSIST_FAILED. */
    public static class StorageIoException extends RuntimeException {
        public StorageIoException(String msg, Throwable cause) { super(msg, cause); }
    }
}
