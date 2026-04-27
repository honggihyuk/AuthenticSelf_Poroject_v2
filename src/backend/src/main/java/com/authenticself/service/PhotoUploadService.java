package com.authenticself.service;

import com.authenticself.config.UploadProperties;
import com.authenticself.controller.dto.UploadPhotoResponse;
import com.authenticself.domain.Space;
import com.authenticself.repository.SpaceRepository;
import com.authenticself.repository.UserRepository;
import com.authenticself.storage.ObjectStorageService;
import com.authenticself.storage.PutResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Core upload-handling logic (FR-2 through FR-5, FR-9).
 * <p>
 * Pipeline:
 * <ol>
 *   <li>Validate bytes in memory (MIME allow-list, size, decodability, resolution).</li>
 *   <li>Verify caller {@code X-User-Id} exists.</li>
 *   <li>Two-phase write: upload blob first, then insert DB row.
 *       If the DB insert throws, compensating {@code delete(objectKey)}.</li>
 * </ol>
 */
@Service
public class PhotoUploadService {

    private static final Logger log = LoggerFactory.getLogger(PhotoUploadService.class);

    private static final DateTimeFormatter KEY_DATE =
            DateTimeFormatter.ofPattern("yyyy'/'MM'/'dd").withLocale(Locale.ROOT);

    private final ObjectStorageService storage;
    private final SpaceRepository      spaces;
    private final UserRepository       users;
    private final UploadProperties     props;

    public PhotoUploadService(ObjectStorageService storage,
                              SpaceRepository spaces,
                              UserRepository users,
                              UploadProperties props) {
        this.storage = storage;
        this.spaces  = spaces;
        this.users   = users;
        this.props   = props;
    }

    /**
     * Entry-point called from the controller.
     *
     * @throws UploadException with the appropriate {@link UploadErrorCode} on
     *         every checked failure path.
     */
    public UploadPhotoResponse upload(String userId,
                                      String declaredContentType,
                                      String originalFilename,
                                      byte[] bytes) {
        long t0 = System.nanoTime();

        // --- 0. user header present + known ---------------------------------
        if (userId == null || userId.isBlank()) {
            throw new UploadException(UploadErrorCode.MISSING_USER_HEADER);
        }
        if (!users.existsById(userId)) {
            throw new UploadException(UploadErrorCode.UNKNOWN_USER);
        }

        // --- 1. empty-file check --------------------------------------------
        if (bytes == null || bytes.length == 0) {
            throw new UploadException(UploadErrorCode.EMPTY_FILE);
        }

        // --- 2. MIME allow-list (declared) ----------------------------------
        String normalisedContentType = declaredContentType == null
                ? "" : declaredContentType.toLowerCase(Locale.ROOT).trim();
        if (!props.getAllowedContentTypes().contains(normalisedContentType)) {
            throw new UploadException(UploadErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }

        // --- 3. size guard --------------------------------------------------
        if (bytes.length > props.getMaxSizeBytes()) {
            throw new UploadException(UploadErrorCode.FILE_TOO_LARGE);
        }

        // --- 4. decodability + resolution -----------------------------------
        BufferedImage img;
        try {
            img = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new UploadException(UploadErrorCode.IMAGE_UNREADABLE, e);
        }
        if (img == null) {
            throw new UploadException(UploadErrorCode.IMAGE_UNREADABLE);
        }
        if (img.getWidth() < props.getMinWidth() || img.getHeight() < props.getMinHeight()) {
            throw new UploadException(UploadErrorCode.RESOLUTION_TOO_LOW);
        }

        // --- 5. build identity + object key ---------------------------------
        String roomId    = UUID.randomUUID().toString();
        String ext       = extensionFor(normalisedContentType);
        String datePath  = ZonedDateTime.now(ZoneOffset.UTC).format(KEY_DATE);
        String objectKey = "spaces/" + datePath + "/" + roomId + ext;

        // --- 6. TWO-PHASE WRITE --------------------------------------------
        PutResult put;
        try {
            put = storage.upload(objectKey, bytes, normalisedContentType);
        } catch (RuntimeException e) {
            log.error("Object-storage upload failed for objectKey={}", objectKey, e);
            throw new UploadException(UploadErrorCode.STORAGE_PERSIST_FAILED, e);
        }

        try {
            Space space = new Space();
            space.setRoomId(roomId);
            space.setUserId(userId);
            space.setPhotoUrl(put.url());
            space.setOriginalFilename(originalFilename);
            space.setContentType(normalisedContentType);
            space.setFileSizeBytes((long) bytes.length);
            space.setStatus(Space.Status.PENDING_ANALYSIS);
            space.setUploadedAt(LocalDateTime.now(ZoneOffset.UTC));
            spaces.save(space);
        } catch (RuntimeException e) {
            // Catches DataAccessException (subclass of RuntimeException) and
            // any other runtime failure during JPA save (FR-5 / AC-14).
            // Compensating delete — FR-5 / AC-14
            log.error("DB insert failed after storage upload; compensating delete objectKey={}",
                    objectKey, e);
            try {
                storage.delete(objectKey);
            } catch (RuntimeException suppressed) {
                log.warn("Compensating delete also failed for objectKey={}: {}",
                        objectKey, suppressed.toString());
            }
            throw new UploadException(UploadErrorCode.STORAGE_PERSIST_FAILED, e);
        }

        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
        log.info("upload ok roomId={} userId={} contentType={} fileSizeBytes={} elapsedMs={}",
                roomId, userId, normalisedContentType, bytes.length, elapsedMs);

        return new UploadPhotoResponse(roomId, put.url(), Space.Status.PENDING_ANALYSIS.name());
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png"  -> ".png";
            case "image/webp" -> ".webp";
            // Defensive: the allow-list check upstream should preclude this.
            default           -> throw new UploadException(UploadErrorCode.UNSUPPORTED_MEDIA_TYPE);
        };
    }
}
