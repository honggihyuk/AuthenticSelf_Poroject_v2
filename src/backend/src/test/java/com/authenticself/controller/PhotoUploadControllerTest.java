package com.authenticself.controller;

import com.authenticself.controller.dto.UploadPhotoResponse;
import com.authenticself.service.PhotoUploadService;
import com.authenticself.service.UploadErrorCode;
import com.authenticself.service.UploadException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link PhotoUploadController} focused on validation ordering
 * and the FR-6 error-code envelope. The service layer is mocked — the actual
 * pipeline is covered by {@code PhotoUploadServiceTest}.
 *
 * Covers AC-6 (201 happy path), AC-7 (empty file), AC-8 (missing header),
 * AC-9 (unknown user), AC-10 (unsupported MIME), AC-12 (unreadable),
 * AC-13 (resolution), AC-14 (STORAGE_PERSIST_FAILED).
 */
@WebMvcTest(controllers = PhotoUploadController.class)
@Import(UploadExceptionAdvice.class)
class PhotoUploadControllerTest {

    @Autowired MockMvc mvc;

    @MockBean PhotoUploadService service;

    // -----------------------------------------------------------------
    // AC-6 — happy path 201
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-6: 201 with roomId / uploadUrl / status on valid upload")
    void happyPath() throws Exception {
        when(service.upload(eq("u1"), any(), any(), any()))
                .thenReturn(new UploadPhotoResponse("r1", "file:///tmp/r1.jpg", "PENDING_ANALYSIS"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

        mvc.perform(multipart("/api/v1/spaces/photo")
                        .file(file)
                        .header("X-User-Id", "u1")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.roomId").value("r1"))
           .andExpect(jsonPath("$.uploadUrl").value("file:///tmp/r1.jpg"))
           .andExpect(jsonPath("$.status").value("PENDING_ANALYSIS"));
    }

    // -----------------------------------------------------------------
    // AC-7 — missing file part -> 400 EMPTY_FILE.
    // Header is present so the missing-header branch is skipped; this
    // proves the ordering: X-User-Id check first, then file presence.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-7: missing file -> 400 EMPTY_FILE")
    void emptyFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]);

        mvc.perform(multipart("/api/v1/spaces/photo")
                        .file(file)
                        .header("X-User-Id", "u1"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errorCode").value("EMPTY_FILE"))
           .andExpect(jsonPath("$.correlationId").exists());
    }

    // -----------------------------------------------------------------
    // AC-8 — missing header -> 401 MISSING_USER_HEADER, and this must
    // be detected BEFORE the file-presence check.
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-8: missing X-User-Id -> 401 MISSING_USER_HEADER (checked before file)")
    void missingHeader() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mvc.perform(multipart("/api/v1/spaces/photo").file(file))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.errorCode").value("MISSING_USER_HEADER"));
    }

    // -----------------------------------------------------------------
    // AC-9 — unknown user propagated from service -> 401 UNKNOWN_USER
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-9: service throws UNKNOWN_USER -> 401")
    void unknownUser() throws Exception {
        when(service.upload(eq("ghost"), any(), any(), any()))
                .thenThrow(new UploadException(UploadErrorCode.UNKNOWN_USER));

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mvc.perform(multipart("/api/v1/spaces/photo")
                        .file(file)
                        .header("X-User-Id", "ghost"))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.errorCode").value("UNKNOWN_USER"));
    }

    // -----------------------------------------------------------------
    // AC-10 — unsupported MIME -> 415
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-10: service throws UNSUPPORTED_MEDIA_TYPE -> 415")
    void unsupportedMime() throws Exception {
        when(service.upload(any(), any(), any(), any()))
                .thenThrow(new UploadException(UploadErrorCode.UNSUPPORTED_MEDIA_TYPE));

        MockMultipartFile file = new MockMultipartFile(
                "file", "cat.gif", "image/gif", new byte[]{1, 2});

        mvc.perform(multipart("/api/v1/spaces/photo")
                        .file(file)
                        .header("X-User-Id", "u1"))
           .andExpect(status().isUnsupportedMediaType())
           .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    // -----------------------------------------------------------------
    // AC-12 — unreadable image -> 422
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-12: service throws IMAGE_UNREADABLE -> 422")
    void unreadable() throws Exception {
        when(service.upload(any(), any(), any(), any()))
                .thenThrow(new UploadException(UploadErrorCode.IMAGE_UNREADABLE));

        MockMultipartFile file = new MockMultipartFile(
                "file", "garbage.jpg", "image/jpeg", new byte[]{9, 9, 9});

        mvc.perform(multipart("/api/v1/spaces/photo")
                        .file(file)
                        .header("X-User-Id", "u1"))
           .andExpect(status().isUnprocessableEntity())
           .andExpect(jsonPath("$.errorCode").value("IMAGE_UNREADABLE"));
    }

    // -----------------------------------------------------------------
    // AC-13 — low resolution -> 422 RESOLUTION_TOO_LOW
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-13: service throws RESOLUTION_TOO_LOW -> 422")
    void tooLowRes() throws Exception {
        when(service.upload(any(), any(), any(), any()))
                .thenThrow(new UploadException(UploadErrorCode.RESOLUTION_TOO_LOW));

        MockMultipartFile file = new MockMultipartFile(
                "file", "tiny.png", "image/png", new byte[]{1, 2});

        mvc.perform(multipart("/api/v1/spaces/photo")
                        .file(file)
                        .header("X-User-Id", "u1"))
           .andExpect(status().isUnprocessableEntity())
           .andExpect(jsonPath("$.errorCode").value("RESOLUTION_TOO_LOW"));
    }

    // -----------------------------------------------------------------
    // AC-14 — partial failure maps to 500 STORAGE_PERSIST_FAILED
    // -----------------------------------------------------------------
    @Test
    @DisplayName("AC-14: service throws STORAGE_PERSIST_FAILED -> 500")
    void storageFailed() throws Exception {
        when(service.upload(any(), any(), any(), any()))
                .thenThrow(new UploadException(UploadErrorCode.STORAGE_PERSIST_FAILED));

        MockMultipartFile file = new MockMultipartFile(
                "file", "ok.jpg", "image/jpeg", new byte[]{1, 2});

        mvc.perform(multipart("/api/v1/spaces/photo")
                        .file(file)
                        .header("X-User-Id", "u1"))
           .andExpect(status().isInternalServerError())
           .andExpect(jsonPath("$.errorCode").value("STORAGE_PERSIST_FAILED"))
           .andExpect(jsonPath("$.correlationId").exists());
    }
}
