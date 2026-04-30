package com.authenticself.ai;

import com.authenticself.ai.dto.AiErrorResponse;
import com.authenticself.ai.dto.StyleAnalysisRequest;
import com.authenticself.ai.dto.StyleAnalysisResponse;
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
 * Typed HTTP client for the Python style-analysis endpoint (Task-4 FR-8 / AC-11).
 * <p>
 * Sibling of {@link SpaceAnalysisClient}. Same transport ({@link RestClient}),
 * same timeouts (connect 2s, read 10s, both env-overridable via
 * {@code app.ai.style.*}) and the same error-translation table — so the
 * orchestrator can treat both downstreams interchangeably.
 */
@Component
public class StyleAnalysisClient {

    private static final Logger log = LoggerFactory.getLogger(StyleAnalysisClient.class);

    private static final String ANALYZE_STYLE_PATH = "/analyze/style";

    private final RestClient   restClient;
    private final String       baseUrl;
    private final ObjectMapper objectMapper;

    @Autowired
    public StyleAnalysisClient(
            @Value("${app.ai.style.base-url:${app.ai.base-url:http://localhost:8001}}")
            String baseUrl,
            @Value("${app.ai.style.connect-timeout-ms:${app.ai.connect-timeout-ms:2000}}")
            int connectTimeoutMs,
            @Value("${app.ai.style.read-timeout-ms:${app.ai.read-timeout-ms:10000}}")
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
    StyleAnalysisClient(RestClient restClient, String baseUrl, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
    }

    /**
     * Call the Python {@code POST /analyze/style} endpoint (AC-11).
     * <p>
     * Signature is intentionally {@code (String, String) -> DTO} — no
     * Spring-web types — so {@link AIOrchestrator} can invoke this in
     * parallel via {@code CompletableFuture.supplyAsync(() -> ...)}.
     *
     * @throws AIException on any non-2xx response or transport failure.
     */
    public StyleAnalysisResponse callStyleAnalysis(String roomId, String photoUrl) {
        StyleAnalysisRequest request = new StyleAnalysisRequest(roomId, photoUrl);
        try {
            return restClient.post()
                    .uri(ANALYZE_STYLE_PATH)
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
                            log.error("Un-parseable error body from style AI service status={} body={}",
                                    resp.getStatusCode(), raw, parseEx);
                            throw new AIException(
                                    AIErrorCode.AI_SERVICE_UNAVAILABLE,
                                    "Un-parseable response from style AI service",
                                    parseEx);
                        }
                    })
                    .body(StyleAnalysisResponse.class);
        } catch (AIException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            log.error("Style AI transport failure url={} roomId={} cause={}",
                    baseUrl + ANALYZE_STYLE_PATH, roomId, ex.toString());
            throw new AIException(
                    AIErrorCode.AI_SERVICE_UNAVAILABLE,
                    "Style AI service unreachable: " + ex.getMostSpecificCause().getClass().getSimpleName(),
                    ex);
        } catch (RuntimeException ex) {
            log.error("Style AI call unexpected failure roomId={}", roomId, ex);
            throw new AIException(
                    AIErrorCode.AI_SERVICE_UNAVAILABLE,
                    "Style AI service call failed: " + ex.getClass().getSimpleName(),
                    ex);
        }
    }

    private String safeMessage(AiErrorResponse body, String raw, HttpStatusCode status) {
        if (body != null && body.message() != null && !body.message().isBlank()) {
            return body.message();
        }
        return "Style AI service returned " + status.value();
    }
}
