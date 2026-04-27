package com.authenticself.service;

import org.springframework.http.HttpStatus;

/**
 * Canonical table of upload-time error codes (FR-6).
 * <p>
 * Keys mirror AC-7 / AC-8 / AC-9 / AC-10 / AC-11 / AC-12 / AC-13 / AC-14
 * and the shared front-end module {@code src/mobile/src/api/errorMessages.ts}.
 */
public enum UploadErrorCode {
    EMPTY_FILE             (HttpStatus.BAD_REQUEST,              "업로드된 파일이 비어 있습니다. 다시 시도해주세요."),
    MISSING_USER_HEADER    (HttpStatus.UNAUTHORIZED,             "사용자 인증 정보가 없습니다. 앱을 다시 시작해주세요."),
    UNKNOWN_USER           (HttpStatus.UNAUTHORIZED,             "존재하지 않는 사용자입니다."),
    UNSUPPORTED_MEDIA_TYPE (HttpStatus.UNSUPPORTED_MEDIA_TYPE,   "지원하지 않는 이미지 형식입니다. JPG, PNG, WEBP만 업로드할 수 있습니다."),
    FILE_TOO_LARGE         (HttpStatus.PAYLOAD_TOO_LARGE,        "파일 용량이 너무 큽니다. 10MB 이하의 사진을 업로드해주세요."),
    IMAGE_UNREADABLE       (HttpStatus.UNPROCESSABLE_ENTITY,     "이미지를 읽을 수 없습니다. 다른 사진으로 다시 시도해주세요."),
    RESOLUTION_TOO_LOW     (HttpStatus.UNPROCESSABLE_ENTITY,     "업로드한 사진의 해상도가 너무 낮습니다. 640x480 이상 이미지를 올려주세요."),
    STORAGE_PERSIST_FAILED (HttpStatus.INTERNAL_SERVER_ERROR,    "일시적인 오류로 사진 저장에 실패했습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus httpStatus;
    private final String     defaultMessage;

    UploadErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus     = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus()     { return httpStatus; }
    public String     defaultMessage() { return defaultMessage; }
}
