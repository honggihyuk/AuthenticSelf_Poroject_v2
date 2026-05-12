package com.authenticself.ai;

import com.authenticself.ai.dto.AiErrorResponse;
import com.authenticself.ai.dto.ObjectsAnalysisRequest;
import com.authenticself.ai.dto.ObjectsAnalysisResponse;
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
 * Typed HTTP client for the Python {@code POST /analyze/objects} endpoint
 * (YOLO furniture detection). Mirrors {@link StyleAnalysisClient} for transport
 * and error handling; the read timeout defaults to the longer recommendation
 * budget because YOLO inference can run a couple of seconds on CPU.
 */
@Component
public class ObjectsAnalysisClient {

    private static final Logger log = LoggerFactory.getLogger(ObjectsAnalysisClient.class);

    private static final String ANALYZE_OBJECTS_PATH = "/analyze/objects";

    private final RestClient   restClient;
    private final String       baseUrl;
    private final ObjectMapper objectMapper;

    public ObjectsAnalysisClient(
            @Value("${app.ai.objects.base-url:${app.ai.base-url:http://localhost:8001}}")
            String baseUrl,
            @Value("${app.ai.objects.connect-timeout-ms:${app.ai.connect-timeout-ms:2000}}")
            int connectTimeoutMs,
            @Value("${app.ai.objects.read-timeout-ms:${app.ai.recommend.read-timeout-ms:15000}}")
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

    public ObjectsAnalysisResponse callObjectsAnalysis(String roomId, String photoUrl) {
        ObjectsAnalysisRequest request = new ObjectsAnalysisRequest(roomId, photoUrl);
        try {
            return restClient.post()
                    .uri(ANALYZE_OBJECTS_PATH)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        String raw = new String(resp.getBody().readAllBytes());
                        try {
                            AiErrorResponse body = objectMapper.readValue(raw, AiErrorResponse.class);
                            AIErrorCode code = AIErrorCode.fromPythonCode(body.errorCode());
                            throw new AIException(code,
                                    body.message() != null && !body.message().isBlank()
                                            ? body.message()
                                            : "Objects AI returned " + resp.getStatusCode().value());
                        } catch (AIException ex) {
                            throw ex;
                        } catch (IOException parseEx) {
                            log.error("Un-parseable error from objects AI status={} body={}",
                                    resp.getStatusCode(), raw, parseEx);
                            throw new AIException(
                                    AIErrorCode.AI_SERVICE_UNAVAILABLE,
                                    "Un-parseable response from objects AI service",
                                    parseEx);
                        }
                    })
                    .body(ObjectsAnalysisResponse.class);
        } catch (AIException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            log.error("Objects AI transport failure url={} roomId={} cause={}",
                    baseUrl + ANALYZE_OBJECTS_PATH, roomId, ex.toString());
            throw new AIException(
                    AIErrorCode.AI_SERVICE_UNAVAILABLE,
                    "Objects AI unreachable: " + ex.getMostSpecificCause().getClass().getSimpleName(),
                    ex);
        } catch (RuntimeException ex) {
            log.error("Objects AI call failed roomId={}", roomId, ex);
            throw new AIException(
                    AIErrorCode.AI_SERVICE_UNAVAILABLE,
                    "Objects AI call failed: " + ex.getClass().getSimpleName(),
                    ex);
        }
    }
}
