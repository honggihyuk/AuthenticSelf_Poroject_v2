package com.authenticself.controller;

import com.authenticself.controller.dto.UploadPhotoResponse;
import com.authenticself.service.PhotoUploadService;
import com.authenticself.service.UploadErrorCode;
import com.authenticself.service.UploadException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * REST adapter for {@code POST /api/v1/spaces/photo} (FR-1).
 * <p>
 * Delegates all validation / persistence logic to {@link PhotoUploadService}
 * and converts {@link UploadException} into the structured error envelope via
 * {@code UploadExceptionAdvice}.
 */
@RestController
@RequestMapping("/api/v1/spaces")
public class PhotoUploadController {

    private final PhotoUploadService service;

    public PhotoUploadController(PhotoUploadService service) {
        this.service = service;
    }

    @PostMapping(path = "/photo", consumes = "multipart/form-data")
    public ResponseEntity<UploadPhotoResponse> uploadPhoto(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(value = "file",      required = false) MultipartFile file
    ) {
        // Missing-header check FIRST so AC-8 passes regardless of file presence.
        if (userId == null || userId.isBlank()) {
            throw new UploadException(UploadErrorCode.MISSING_USER_HEADER);
        }
        if (file == null || file.isEmpty()) {
            throw new UploadException(UploadErrorCode.EMPTY_FILE);
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UploadException(UploadErrorCode.STORAGE_PERSIST_FAILED, e);
        }

        UploadPhotoResponse resp = service.upload(
                userId,
                file.getContentType(),
                file.getOriginalFilename(),
                bytes);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }
}
