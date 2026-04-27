package com.authenticself.controller.dto;

/**
 * Success response body for {@code POST /api/v1/spaces/photo}.
 * <p>
 * Mirrors FR-1 / AC-6. {@code status} is a plain string (not the enum itself)
 * so the JSON shape never drifts if the enum is renamed internally.
 */
public record UploadPhotoResponse(
        String roomId,
        String uploadUrl,
        String status
) {
}
