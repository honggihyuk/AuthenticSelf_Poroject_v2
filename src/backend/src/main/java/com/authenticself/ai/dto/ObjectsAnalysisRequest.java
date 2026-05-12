package com.authenticself.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Request body for AI {@code POST /analyze/objects}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ObjectsAnalysisRequest(String roomId, String photoUrl) {}
