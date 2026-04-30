package com.authenticself.ai;

import com.authenticself.ai.dto.AiErrorResponse;
import com.authenticself.ai.dto.SpaceAnalysisRequest;
import com.authenticself.ai.dto.SpaceAnalysisResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Typed HTTP client for the Python space-analysis service (FR-11).
 * <p>
 * Uses Spring 6 {@link RestClient} (NOT the JDK's low-level HTTP primitives —
 * AC-23). Connect/read timeouts come from {@code app.ai.*} config; errors
 * are translated into {@link AIException} with a structured {@link AIErrorCode}
 * so the orchestrator can branch on transport-vs-analyzer failures.
 */
@Component
public class SpaceAnalysisClient {

    private static final Logger log = LoggerFactory.getLogger(SpaceAnalysisClient.class);

    private static final String ANALYZE_SPACE_PATH = "/analyze/space";

    private final RestClient restClient;
    private final String     baseUrl;
    private final ObjectMapper objectMapper;

    @Autowired
    public SpaceAnalysisClient(
            @Value("${app.ai.base-url:${app.ai.space.base-url:http://localhost:8001}}")
            String baseUrl,
            @Value("${app.ai.connect-timeout-ms:${app.ai.space.connect-timeout-ms:2000}}")
            int connectTimeoutMs,
            @Value("${app.ai.read-timeout-ms:${app.ai.space.read-timeout-ms:10000}}")
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
    SpaceAnalysisClient(RestClient restClient, String baseUrl, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
    }

    /**
     * Call the Python {@code POST /analyze/space} endpoint.
     * <p>
     * Signature is intentionally {@code (String, String) -> DTO} (AC-24) — no
     * {@link org.springframework.http.ResponseEntity}, no Spring-web types —
     * so Task 4 can invoke it in parallel via
     * {@code CompletableFuture.supplyAsync(() -> client.callSpaceAnalysis(...))}.
     *
     * @throws AIException on any non-2xx response or transport failure.
     */
    public SpaceAnalysisResponse callSpaceAnalysis(String roomId, String photoUrl) {
        SpaceAnalysisRequest request = new SpaceAnalysisRequest(roomId, photoUrl);
        try {
            return restClient.post()
                    .uri(ANALYZE_SPACE_PATH)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        String raw = new String(resp.getBody().readAllBytes());
                        AIErrorCode code;
                        try {
                            AiErrorResponse body = objectMapper.readValue(raw, AiErrorResponse.class);
                            code = AIErrorCode.fromPythonCode(body.errorCode());
                            throw new AIException(code, safeMessage(body, raw, resp.getStatusCode()));
                        } catch (AIException ex) {
                            throw ex;
                        } catch (IOException parseEx) {
                            // Unparseable body — treat as transport-level failure so the
                            // orchestrator keeps the row at PENDING_ANALYSIS.
                            log.error("Un-parseable error body from AI service status={} body={}",
                                    resp.getStatusCode(), raw, parseEx);
                            throw new AIException(
                                    AIErrorCode.AI_SERVICE_UNAVAILABLE,
                                    "Un-parseable response from AI service",
                                    parseEx);
                        }
                    })
                    .body(SpaceAnalysisResponse.class);
        } catch (AIException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            // Connect/read timeout or ConnectException — transport failure (FR-15).
            log.error("AI transport failure url={} roomId={} cause={}",
                    baseUrl + ANALYZE_SPACE_PATH, roomId, ex.toString());
            throw new AIException(
                    AIErrorCode.AI_SERVICE_UNAVAILABLE,
                    "AI service unreachable: " + ex.getMostSpecificCause().getClass().getSimpleName(),
                    ex);
        } catch (RuntimeException ex) {
            log.error("AI call unexpected failure roomId={}", roomId, ex);
            throw new AIException(
                    AIErrorCode.AI_SERVICE_UNAVAILABLE,
                    "AI service call failed: " + ex.getClass().getSimpleName(),
                    ex);
        }
    }

    private String safeMessage(AiErrorResponse body, String raw, HttpStatusCode status) {
        if (body != null && body.message() != null && !body.message().isBlank()) {
            return body.message();
        }
        return "AI service returned " + status.value();
    }
}
