package com.authenticself.storage;

/**
 * Immutable value returned by {@link ObjectStorageService#upload}.
 * Carries only transport-neutral fields — no SDK types may appear here.
 */
public record PutResult(String objectKey, String url, long sizeBytes) {
}
