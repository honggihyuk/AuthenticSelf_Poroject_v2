package com.authenticself.controller.dto;

/**
 * Request body for
 * {@code PUT /api/v1/spaces/{roomId}/preferred-style} (Task-4 FR-14 / AC-22).
 * <p>
 * {@code preferredStyle} is typed as {@link String} (not
 * {@link com.authenticself.space.PreferredStyle}) so Jackson does not
 * reject the whole request with a 400 / type-mismatch when the value is
 * invalid — the controller parses it manually and emits the specific
 * {@code INVALID_PREFERRED_STYLE} errorCode required by AC-24.
 */
public record SetPreferredStyleRequest(
        String preferredStyle
) {
}
