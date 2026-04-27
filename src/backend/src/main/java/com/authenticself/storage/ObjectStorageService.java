package com.authenticself.storage;

/**
 * Abstraction over the object-storage backend used to persist uploaded photos.
 * <p>
 * Per FR-3 / AC-15 no cloud-SDK types (S3, GCS, Azure) may leak outside the
 * {@code com.authenticself.storage.<provider>} subpackages — controller,
 * service, and repository layers must only reference this interface.
 * <p>
 * Implementations:
 * <ul>
 *   <li>{@code LocalFileSystemObjectStorageService} — dev / CI default.</li>
 *   <li>future S3 / GCS implementations live under their own subpackages.</li>
 * </ul>
 */
public interface ObjectStorageService {

    /**
     * Persist the given bytes at the supplied object key.
     *
     * @param objectKey   storage-relative key, e.g. {@code spaces/2026/04/17/abc.jpg}.
     *                    Must never be derived from un-sanitized user input.
     * @param bytes       the payload; must be non-null.
     * @param contentType validated MIME type (diagnostic; some backends store it
     *                    as metadata, the local-FS impl ignores it).
     * @return a {@link PutResult} carrying the resolved URL and byte length.
     */
    PutResult upload(String objectKey, byte[] bytes, String contentType);

    /**
     * Remove the blob at the given key. Must be idempotent — callers use this
     * for compensating-delete cleanup (see FR-5) so "already gone" is not
     * an error.
     */
    void delete(String objectKey);

    /**
     * @return {@code true} if the given object key currently resolves to a blob.
     */
    boolean exists(String objectKey);
}
