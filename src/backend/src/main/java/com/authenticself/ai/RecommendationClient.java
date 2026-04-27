package com.authenticself.ai;

import com.authenticself.ai.dto.AiErrorResponse;
import com.authenticself.ai.dto.RecommendationRequest;
import com.authenticself.ai.dto.RecommendationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;

/**
 * Typed HTTP client for the Python {@code POST /recommend/furniture}
 * endpoint (UC-01-recommendation FR-13).
 * <p>
 * Sibling of {@link SpaceAnalysisClient} and {@link StyleAnalysisClient}.
 * Same transport ({@link RestClient}), same error-translation table.
 * <p>
 * Timeouts: connect 2 s, read 15 s — scoring is fast (~20 ms for the
 * seeded 24-row catalog) but the longer read timeout leaves headroom for
 * catalog growth / cold-start container overhead. Both are
 * env-overridable via {@code app.ai.recommend.{connect,read}-timeout-ms}
 * (FR-21 / AC-43).
 */
@Component
public class RecommendationClient {

    private static final Logger log = LoggerFactory.getLogger(RecommendationClient.class);

    private static final String RECOMMEND_PATH = "/recommend/furniture";

    private final RestClient   restClient;
    private final String       baseUrl;
    private final ObjectMapper objectMapper;

    public RecommendationClient(
            @Value("${app.ai.recommend.base-url:${app.ai.base-url:http://localhost:8001}}")
            String baseUrl,
            @Value("${app.ai.recommend.connect-timeout-ms:2000}")
            int connectTimeoutMs,
            @Value("${app.ai.recommend.read-timeout-ms:15000}")
            int readTimeoutMs,
            ObjectMapper objectMapper
    ) {
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofMillis(connectTimeoutMs).toMillis());
        factory.setReadTimeout((int) Duration.ofMillis(readTimeoutMs).toMillis());

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Accept",       MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /** Test-visible constructor allowing direct {@link RestClient} injection. */
    RecommendationClient(RestClient restClient, String baseUrl, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
    }

    /**
     * Call the Python {@code POST /recommend/furniture} endpoint (AC-27).
     * <p>
     * Signature is intentionally {@code (RecommendationRequest) -> RecommendationResponse}
     * — one parameter, one DTO-typed return — matching the sibling-client
     * pattern so {@link RecommendationOrchestrator} can invoke it on the
     * {@code aiExecutor} pool via {@code CompletableFuture.supplyAsync}.
     *
     * @throws AIException on any non-2xx response or transport failure.
     */
    public RecommendationResponse callRecommend(RecommendationRequest req) {
        try {
            return restClient.post()
                    .uri(RECOMMEND_PATH)
                    .body(req)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, resp) -> {
                        String raw = new String(resp.getBody().readAllBytes());
                        try {
                            AiErrorResponse body = objectMapper.readValue(raw, AiErrorResponse.class);
                            AIErrorCode code = AIErrorCode.fromPythonCode(body.errorCode());
                            throw new AIException(code, safeMessage(body, raw, resp.getStatusCode()));
                        } catch (AIException ex) {
                            throw ex;
                        } catch (IOException parseEx) {
                            log.error("Un-parseable error body from recommend AI service status={} body={}",
                                    resp.getStatusCode(), raw, parseEx);
                            throw new AIException(
                                    AIErrorCode.AI_SERVICE_UNAVAILABLE,
                                    "Un-parseable response from recommend AI service",
                                    parseEx);
                        }
                    })
                    .body(RecommendationResponse.class);
        } catch (AIException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            log.error("Recommend AI transport failure url={} roomId={} cause={}",
                    baseUrl + RECOMMEND_PATH, req.roomId(), ex.toString());
            throw new AIException(
                    AIErrorCode.AI_SERVICE_UNAVAILABLE,
                    "Recommend AI service unreachable: " + ex.getMostSpecificCause().getClass().getSimpleName(),
                    ex);
        } catch (RuntimeException ex) {
            log.error("Recommend AI call unexpected failure roomId={}", req.roomId(), ex);
            throw new AIException(
                    AIErrorCode.AI_SERVICE_UNAVAILABLE,
                    "Recommend AI service call failed: " + ex.getClass().getSimpleName(),
                    ex);
        }
    }

    private String safeMessage(AiErrorResponse body, String raw, HttpStatusCode status) {
        if (body != null && body.message() != null && !body.message().isBlank()) {
            return body.message();
        }
        return "Recommend AI service returned " + status.value();
    }
}
