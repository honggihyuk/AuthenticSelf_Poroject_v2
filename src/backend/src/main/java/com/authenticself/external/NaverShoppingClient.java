package com.authenticself.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

/**
 * Phase B — typed client for the Naver Shopping search API
 * ({@code https://openapi.naver.com/v1/search/shop.json}).
 *
 * <p>Both credentials are read from env-overridable Spring properties.
 * When either is blank the client is in <b>unconfigured</b> mode:
 * {@link #isConfigured()} returns {@code false} and {@link #search} throws
 * {@link NaverNotConfiguredException}. {@link com.authenticself.batch.SimilarProductsBatch}
 * uses this to skip the nightly batch silently in environments without keys
 * (e.g. CI, fresh developer laptop), without failing the application context.
 *
 * <p>Set credentials via:
 * <pre>
 *   export NAVER_CLIENT_ID=...
 *   export NAVER_CLIENT_SECRET=...
 * </pre>
 * or the equivalent Spring property keys {@code app.naver.client-id} /
 * {@code app.naver.client-secret}.
 */
@Component
public class NaverShoppingClient {

    private static final Logger log = LoggerFactory.getLogger(NaverShoppingClient.class);

    private static final String SHOP_PATH = "/v1/search/shop.json";

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;

    public NaverShoppingClient(
            @Value("${app.naver.base-url:https://openapi.naver.com}") String baseUrl,
            @Value("${app.naver.client-id:${NAVER_CLIENT_ID:}}") String clientId,
            @Value("${app.naver.client-secret:${NAVER_CLIENT_SECRET:}}") String clientSecret,
            @Value("${app.naver.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${app.naver.read-timeout-ms:10000}") int readTimeoutMs
    ) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofMillis(connectTimeoutMs).toMillis());
        factory.setReadTimeout((int) Duration.ofMillis(readTimeoutMs).toMillis());

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();

        if (isConfigured()) {
            log.info("NaverShoppingClient initialised (creds present).");
        } else {
            log.warn("NaverShoppingClient initialised WITHOUT credentials — batch will skip.");
        }
    }

    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    /**
     * Search Naver Shopping for {@code query}, returning up to {@code display}
     * items (1..100, default 10) sorted by relevance (Naver "sim").
     */
    public ShopSearchResponse search(String query, int display) {
        if (!isConfigured()) {
            throw new NaverNotConfiguredException();
        }
        int safeDisplay = Math.max(1, Math.min(100, display));
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(SHOP_PATH)
                            .queryParam("query", query)
                            .queryParam("display", safeDisplay)
                            .queryParam("start", 1)
                            .queryParam("sort", "sim")
                            .build())
                    .header("X-Naver-Client-Id", clientId)
                    .header("X-Naver-Client-Secret", clientSecret)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        String body = new String(resp.getBody().readAllBytes());
                        throw new NaverApiException(
                                "Naver shop search failed: " + resp.getStatusCode().value() + " " + body);
                    })
                    .body(ShopSearchResponse.class);
        } catch (NaverApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new NaverApiException("Naver shop search transport error: " + ex.getClass().getSimpleName(), ex);
        }
    }

    // ---------------------------------------------------------------------
    // Response shapes (only the fields we use)
    // ---------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ShopSearchResponse(int total, int start, int display, List<Item> items) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Item(
                String title,
                String link,
                String image,
                String lprice,
                String hprice,
                String mallName,
                String productId,
                String productType,
                String brand,
                String maker,
                String category1,
                String category2,
                String category3,
                String category4
        ) {
            /** Naver wraps query matches in <b>...</b> — strip for display. */
            public String cleanTitle() {
                return title == null ? "" : title.replaceAll("</?b>", "");
            }

            /** Parse lprice (lowest price) as int; null on missing/malformed. */
            public Integer priceAsInt() {
                if (lprice == null || lprice.isBlank()) return null;
                try {
                    return Integer.parseInt(lprice.trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
    }

    /** Thrown when the client is invoked but credentials are missing. */
    public static class NaverNotConfiguredException extends RuntimeException {
        public NaverNotConfiguredException() {
            super("Naver Shopping credentials are not configured (NAVER_CLIENT_ID / NAVER_CLIENT_SECRET).");
        }
    }

    /** Thrown on any non-2xx or transport failure. */
    public static class NaverApiException extends RuntimeException {
        public NaverApiException(String message) { super(message); }
        public NaverApiException(String message, Throwable cause) { super(message, cause); }
    }
}
