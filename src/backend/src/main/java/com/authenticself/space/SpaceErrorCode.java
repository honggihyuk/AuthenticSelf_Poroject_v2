package com.authenticself.space;

import org.springframework.http.HttpStatus;

/**
 * Error codes for the public {@code /api/v1/spaces/*} endpoints (Task-4 FR-14,
 * AC-20 / AC-21 / AC-24 / AC-25).
 * <p>
 * Separate from {@link com.authenticself.ai.AIErrorCode} — that enum is about
 * downstream AI-pipeline failures (5xx transport, 422 analyzer). These codes
 * are about user-facing errors on the public REST surface.
 */
public enum SpaceErrorCode {

    SPACE_NOT_FOUND         (HttpStatus.NOT_FOUND,            "해당 공간을 찾을 수 없습니다."),
    SPACE_ACCESS_DENIED     (HttpStatus.FORBIDDEN,            "해당 공간에 접근할 권한이 없습니다."),
    MISSING_USER_HEADER     (HttpStatus.BAD_REQUEST,          "사용자 인증 정보가 없습니다."),
    INVALID_PREFERRED_STYLE (HttpStatus.BAD_REQUEST,          "지원하지 않는 스타일 값입니다."),
    ANALYSIS_NOT_READY      (HttpStatus.CONFLICT,             "사진 분석이 아직 완료되지 않았습니다. 분석 완료 후 다시 시도해주세요."),

    // UC-01-recommendation (Task 5) FR-19 additions.
    PREFERRED_STYLE_NOT_SET (HttpStatus.CONFLICT,             "원하는 스타일을 먼저 선택해주세요."),
    INVALID_TOP_N           (HttpStatus.BAD_REQUEST,          "요청한 추천 개수가 잘못되었습니다. 1에서 10 사이의 값을 사용해주세요.");

    private final HttpStatus httpStatus;
    private final String     defaultMessage;

    SpaceErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus()     { return httpStatus; }
    public String     defaultMessage() { return defaultMessage; }
}
