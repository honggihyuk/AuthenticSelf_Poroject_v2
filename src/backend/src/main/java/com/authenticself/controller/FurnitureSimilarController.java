package com.authenticself.controller;

import com.authenticself.batch.SimilarProductsBatch;
import com.authenticself.domain.FurnitureSimilarCache;
import com.authenticself.repository.FurnitureSimilarCacheRepository;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Phase B public read + admin endpoints.
 *
 * <ul>
 *   <li>{@code GET  /api/v1/furniture/{furnitureId}/similar} — read cache.</li>
 *   <li>{@code POST /api/v1/admin/similar-products/refresh} — manual batch
 *       trigger (handy when Naver creds were just supplied or the catalog
 *       changed). Returns a small JSON summary.</li>
 * </ul>
 *
 * <p>The read endpoint is public (no X-User-Id needed) because the data is
 * derived from public Naver Shopping listings.</p>
 */
@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*",
        methods = { RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS })
public class FurnitureSimilarController {

    private final FurnitureSimilarCacheRepository cacheRepository;
    private final SimilarProductsBatch batch;

    public FurnitureSimilarController(
            FurnitureSimilarCacheRepository cacheRepository,
            SimilarProductsBatch batch
    ) {
        this.cacheRepository = cacheRepository;
        this.batch = batch;
    }

    @GetMapping("/api/v1/furniture/{furnitureId}/similar")
    public ResponseEntity<SimilarProductsResponse> get(@PathVariable("furnitureId") String furnitureId) {
        List<FurnitureSimilarCache> rows = cacheRepository.findByFurnitureIdRanked(furnitureId);
        List<SimilarProduct> items = rows.stream().map(SimilarProductsController::toDto).toList();
        return ResponseEntity.ok(new SimilarProductsResponse(furnitureId, items));
    }

    @PostMapping("/api/v1/admin/similar-products/refresh")
    public ResponseEntity<SimilarProductsBatch.BatchResult> refresh() {
        return ResponseEntity.ok(batch.runOnce());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SimilarProduct(
            String source,
            String externalId,
            int rankOrder,
            String title,
            String externalUrl,
            String imageUrl,
            Integer price,
            String mallName,
            BigDecimal similarityScore,
            LocalDateTime fetchedAt
    ) {}

    public record SimilarProductsResponse(String furnitureId, List<SimilarProduct> items) {}

    /** Mapper helper kept package-private so the controller stays a thin shell. */
    static final class SimilarProductsController {
        private SimilarProductsController() {}
        static SimilarProduct toDto(FurnitureSimilarCache r) {
            return new SimilarProduct(
                    r.getSource(),
                    r.getExternalId(),
                    r.getRankOrder() == null ? 0 : r.getRankOrder(),
                    r.getTitle(),
                    r.getExternalUrl(),
                    r.getImageUrl(),
                    r.getPrice(),
                    r.getMallName(),
                    r.getSimilarityScore(),
                    r.getFetchedAt()
            );
        }
    }
}
