package com.authenticself.batch;

import com.authenticself.domain.Furniture;
import com.authenticself.domain.FurnitureSimilarCache;
import com.authenticself.external.NaverShoppingClient;
import com.authenticself.external.NaverShoppingClient.ShopSearchResponse;
import com.authenticself.repository.FurnitureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase B — nightly batch that, for each curated furniture row, queries
 * Naver Shopping for similar products and stores the top-N in
 * {@code furniture_similar_cache}.
 *
 * <p>Behaviour matrix:
 * <ul>
 *   <li>{@code NaverShoppingClient.isConfigured() == false} → log + no-op
 *       (typical for CI / fresh dev env without keys).</li>
 *   <li>Otherwise: replace cache for each row, ranking by Naver's own "sim"
 *       order (similarityScore is a constant 1.0 in this step; CLIP-based
 *       scoring lands in a later iteration).</li>
 * </ul>
 *
 * <p>Schedule defaults to "0 0 3 * * *" (every day at 03:00). Override via
 * {@code app.batch.similar-products.cron}. Use {@code -} to disable entirely.
 * The {@link #runOnce()} method is also callable manually (admin tool / test).
 */
@Component
public class SimilarProductsBatch {

    private static final Logger log = LoggerFactory.getLogger(SimilarProductsBatch.class);
    private static final String SOURCE_NAVER = "NAVER";

    private final FurnitureRepository furnitureRepository;
    private final SimilarProductsPersistence persistence;
    private final NaverShoppingClient naver;
    private final int topN;
    private final int displayPerQuery;
    private final int cacheTtlHours;
    private final long perRowDelayMs;
    private final boolean enabled;

    public SimilarProductsBatch(
            FurnitureRepository furnitureRepository,
            SimilarProductsPersistence persistence,
            NaverShoppingClient naver,
            @Value("${app.batch.similar-products.enabled:true}") boolean enabled,
            @Value("${app.batch.similar-products.top-n:10}") int topN,
            @Value("${app.batch.similar-products.display:20}") int displayPerQuery,
            @Value("${app.batch.similar-products.cache-ttl-hours:24}") int cacheTtlHours,
            @Value("${app.batch.similar-products.per-row-delay-ms:250}") long perRowDelayMs
    ) {
        this.furnitureRepository = furnitureRepository;
        this.persistence = persistence;
        this.naver = naver;
        this.enabled = enabled;
        this.topN = topN;
        this.displayPerQuery = displayPerQuery;
        this.cacheTtlHours = cacheTtlHours;
        this.perRowDelayMs = perRowDelayMs;
    }

    @Scheduled(cron = "${app.batch.similar-products.cron:0 0 3 * * *}")
    public void scheduled() {
        if (!enabled) {
            log.info("similar-products batch disabled by config — skipping");
            return;
        }
        runOnce();
    }

    /** Manual / test entry point. Safe to call repeatedly. */
    public BatchResult runOnce() {
        if (!naver.isConfigured()) {
            log.warn("similar-products batch skipped — Naver credentials absent");
            return new BatchResult(0, 0, 0, 0);
        }

        List<Furniture> rows = furnitureRepository.findAll();
        int updated = 0;
        int failed = 0;
        int totalInserted = 0;

        for (Furniture f : rows) {
            try {
                int inserted = refreshOne(f);
                totalInserted += inserted;
                updated++;
            } catch (RuntimeException ex) {
                failed++;
                log.warn("similar-products refresh failed furnitureId={} err={}",
                        f.getFurnitureId(), ex.toString());
            }
            if (perRowDelayMs > 0) {
                try { Thread.sleep(perRowDelayMs); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); return new BatchResult(rows.size(), updated, failed, totalInserted); }
            }
        }
        log.info("similar-products batch done rows={} updated={} failed={} inserted={}",
                rows.size(), updated, failed, totalInserted);
        return new BatchResult(rows.size(), updated, failed, totalInserted);
    }

    protected int refreshOne(Furniture f) {
        String query = buildQuery(f);
        ShopSearchResponse resp = naver.search(query, displayPerQuery);
        int returned = (resp == null || resp.items() == null) ? 0 : resp.items().size();
        log.info("similar-products fetched furnitureId={} query='{}' returned={}",
                f.getFurnitureId(), query, returned);
        if (returned == 0) return 0;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expires = now.plus(Duration.ofHours(cacheTtlHours));
        int limit = Math.min(topN, returned);
        List<FurnitureSimilarCache> rows = new ArrayList<>(limit);
        int rank = 0;
        for (ShopSearchResponse.Item item : resp.items()) {
            if (rank >= limit) break;
            if (item.productId() == null || item.productId().isBlank()) continue;
            FurnitureSimilarCache row = new FurnitureSimilarCache();
            row.setFurnitureId(f.getFurnitureId());
            row.setSource(SOURCE_NAVER);
            row.setExternalId(item.productId());
            row.setRankOrder(rank + 1);
            row.setTitle(truncate(item.cleanTitle(), 512));
            row.setExternalUrl(truncate(item.link(), 1024));
            row.setImageUrl(truncate(item.image(), 1024));
            row.setPrice(item.priceAsInt());
            row.setMallName(truncate(item.mallName(), 255));
            row.setSimilarityScore(BigDecimal.ONE);    // CLIP scoring lands later.
            row.setFetchedAt(now);
            row.setExpiresAt(expires);
            rows.add(row);
            rank++;
        }
        // Delegate the delete+insert pair to a separate bean so Spring's AOP
        // proxy actually wraps it in @Transactional (self-invocation would
        // bypass the proxy and trigger an InvalidDataAccessApiUsageException
        // on the @Modifying delete).
        return persistence.replace(f.getFurnitureId(), SOURCE_NAVER, rows);
    }

    /** Build the Naver query string from the curated row's type + style + name. */
    private String buildQuery(Furniture f) {
        StringBuilder sb = new StringBuilder();
        if (f.getStyle() != null && !f.getStyle().isBlank()) {
            sb.append(styleKorean(f.getStyle())).append(' ');
        }
        sb.append(typeKorean(f.getType()));
        return sb.toString().trim();
    }

    private static String typeKorean(String type) {
        if (type == null) return "";
        return switch (type) {
            case "bed" -> "침대";
            case "chair" -> "의자";
            case "desk" -> "책상";
            case "lighting" -> "조명";
            default -> type;
        };
    }

    private static String styleKorean(String style) {
        if (style == null) return "";
        return switch (style) {
            case "MODERN" -> "모던";
            case "SCANDINAVIAN" -> "북유럽";
            case "CLASSIC" -> "클래식";
            case "INDUSTRIAL" -> "인더스트리얼";
            case "SIMPLE" -> "심플";
            default -> "";
        };
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    public record BatchResult(int scanned, int updated, int failed, int inserted) {}
}
