package com.authenticself.ai;

import org.springframework.http.HttpStatus;

/**
 * Canonical Spring-side error codes for the AI pipeline (FR-15).
 * <p>
 * One-to-one mapping with Python {@code errorCode} strings plus the
 * transport bucket that the Python side cannot emit. Enums decouple wire
 * strings from HTTP status so the mapping is reviewed in one place.
 */
public enum AIErrorCode {

    ANALYSIS_IMAGE_NOT_FOUND  (HttpStatus.UNPROCESSABLE_ENTITY, "분석 요청된 이미지 파일을 찾을 수 없습니다."),
    ANALYSIS_IMAGE_READ_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "이미지 파일을 읽지 못했습니다."),
    ANALYSIS_FAILED           (HttpStatus.UNPROCESSABLE_ENTITY, "사진 분석에 실패했습니다. 다른 사진으로 다시 시도해주세요."),
    AI_SERVICE_UNAVAILABLE    (HttpStatus.BAD_GATEWAY,          "AI 서비스가 일시적으로 응답하지 않습니다. 잠시 후 다시 시도해주세요."),
    SPACE_NOT_FOUND           (HttpStatus.NOT_FOUND,            "해당 공간을 찾을 수 없습니다."),

    // UC-01-recommendation (Task 5) FR-12 / FR-19 additions.
    CATALOG_EMPTY             (HttpStatus.UNPROCESSABLE_ENTITY, "추천할 가구 카탈로그가 비어 있습니다."),
    RECOMMENDATION_FAILED     (HttpStatus.UNPROCESSABLE_ENTITY, "추천 생성에 실패했습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus httpStatus;
    private final String     defaultMessage;

    AIErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus     = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus()     { return httpStatus; }
    public String     defaultMessage() { return defaultMessage; }

    /**
     * Translate a Python-side {@code errorCode} string into a Spring-side
     * {@link AIErrorCode}. Unknown values fall through to
     * {@link #AI_SERVICE_UNAVAILABLE} — that is the Python-INTERNAL_ERROR case
     * per FR-15 ("Python INTERNAL_ERROR → 502 AI_SERVICE_UNAVAILABLE").
     */
    public static AIErrorCode fromPythonCode(String pythonCode) {
        if (pythonCode == null) return AI_SERVICE_UNAVAILABLE;
        return switch (pythonCode) {
            case "IMAGE_NOT_FOUND"        -> ANALYSIS_IMAGE_NOT_FOUND;
            case "IMAGE_READ_FAILED"      -> ANALYSIS_IMAGE_READ_FAILED;
            case "ANALYSIS_FAILED",
                 "INVALID_REQUEST"        -> ANALYSIS_FAILED;
            case "CATALOG_EMPTY"          -> CATALOG_EMPTY;
            case "RECOMMENDATION_FAILED"  -> RECOMMENDATION_FAILED;
            default                       -> AI_SERVICE_UNAVAILABLE;
        };
    }
}
