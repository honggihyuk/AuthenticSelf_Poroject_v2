package com.authenticself.space;

import com.authenticself.ai.RecommendationOrchestrator;
import com.authenticself.ai.SpaceAnalysisPersistence;
import com.authenticself.ai.StyleConfidenceCache;
import com.authenticself.controller.dto.RecommendationsApiResponse;
import com.authenticself.controller.dto.SetPreferredStyleRequest;
import com.authenticself.controller.dto.SpaceResponse;
import com.authenticself.domain.Space;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoints for {@code /api/v1/spaces/{roomId}} (Task-4 FR-14 / FR-15 +
 * Task-5 UC-01-recommendation FR-19 / FR-20).
 *
 * <ul>
 *   <li>{@code GET  /api/v1/spaces/{roomId}} — polling endpoint for the RN
 *       client; returns the current analysis state.</li>
 *   <li>{@code PUT  /api/v1/spaces/{roomId}/preferred-style} — writes the
 *       user's chosen target style into {@code spaces.preferred_style}
 *       AND invalidates the recommendation cache for the room (FR-20).</li>
 *   <li>{@code GET  /api/v1/spaces/{roomId}/recommendations} — ranked
 *       furniture recommendations (FR-19). Requires the row to be
 *       {@code ANALYZED} AND {@code preferred_style} populated.</li>
 * </ul>
 *
 * <p>Every endpoint requires an {@code X-User-Id} header matching the
 * owning user; 403 on mismatch.
 */
@RestController
@RequestMapping("/api/v1/spaces")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {
        org.springframework.web.bind.annotation.RequestMethod.GET,
        org.springframework.web.bind.annotation.RequestMethod.PUT,
        org.springframework.web.bind.annotation.RequestMethod.OPTIONS
})
public class SpaceController {

    private final SpaceAnalysisPersistence persistence;
    private final RecommendationOrchestrator recommendationOrchestrator;
    private final int defaultTopN;

    public SpaceController(
            SpaceAnalysisPersistence persistence,
            RecommendationOrchestrator recommendationOrchestrator,
            @Value("${app.recommendation.default-top-n:3}") int defaultTopN
    ) {
        this.persistence = persistence;
        this.recommendationOrchestrator = recommendationOrchestrator;
        this.defaultTopN = defaultTopN;
    }

    // -----------------------------------------------------------------
    // GET — polling endpoint
    // -----------------------------------------------------------------
    @GetMapping("/{roomId}")
    public ResponseEntity<SpaceResponse> getSpace(
            @PathVariable("roomId") String roomId,
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        requireUserId(userId);
        Space space = loadAndAuthorize(roomId, userId);
        return ResponseEntity.ok(toResponse(space));
    }

    // -----------------------------------------------------------------
    // PUT — preferred-style update
    // -----------------------------------------------------------------
    @PutMapping("/{roomId}/preferred-style")
    public ResponseEntity<SpaceResponse> setPreferredStyle(
            @PathVariable("roomId") String roomId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody(required = false) SetPreferredStyleRequest body
    ) {
        requireUserId(userId);

        // Parse + validate enum value (AC-24).
        String raw = body != null ? body.preferredStyle() : null;
        PreferredStyle target = PreferredStyle.fromNullable(raw);
        if (target == null) {
            throw new SpaceException(SpaceErrorCode.INVALID_PREFERRED_STYLE);
        }

        Space space = loadAndAuthorize(roomId, userId);

        // AC-25 — can only set preferred style when status is ANALYZED.
        if (space.getStatus() != Space.Status.ANALYZED) {
            throw new SpaceException(SpaceErrorCode.ANALYSIS_NOT_READY);
        }

        Space updated = persistence.setPreferredStyle(roomId, target);

        // UC-01-recommendation FR-20 / AC-30 — evict stale (roomId, *)
        // recommendation cache entries so the next GET .../recommendations
        // round-trips to Python with the new style.
        recommendationOrchestrator.invalidate(roomId);

        return ResponseEntity.ok(toResponse(updated));
    }

    // -----------------------------------------------------------------
    // GET — recommendations (UC-01-recommendation FR-19).
    // -----------------------------------------------------------------
    @GetMapping("/{roomId}/recommendations")
    public ResponseEntity<RecommendationsApiResponse> getRecommendations(
            @PathVariable("roomId") String roomId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(value = "topNPerCategory", required = false) Integer topNPerCategory
    ) {
        requireUserId(userId);

        int topN = (topNPerCategory == null) ? defaultTopN : topNPerCategory;
        if (topN < 1 || topN > 10) {
            // AC-35 — 1..10 inclusive.
            throw new SpaceException(SpaceErrorCode.INVALID_TOP_N);
        }

        RecommendationOrchestrator.OrchestratorResult result =
                recommendationOrchestrator.recommend(roomId, userId, topN);

        RecommendationsApiResponse body = RecommendationsApiResponse.from(
                result.response(), result.preferredStyle(), result.cacheHit());
        return ResponseEntity.ok(body);
    }

    // -----------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------

    private void requireUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new SpaceException(SpaceErrorCode.MISSING_USER_HEADER);
        }
    }

    private Space loadAndAuthorize(String roomId, String userId) {
        Space space;
        try {
            space = persistence.loadForRead(roomId);
        } catch (com.authenticself.ai.SpaceNotFoundException ex) {
            throw new SpaceException(SpaceErrorCode.SPACE_NOT_FOUND);
        }
        if (!userId.equals(space.getUserId())) {
            // AC-20 — do not leak row data to unauthorized users.
            throw new SpaceException(SpaceErrorCode.SPACE_ACCESS_DENIED);
        }
        return space;
    }

    private SpaceResponse toResponse(Space space) {
        Double styleConfidence = StyleConfidenceCache.get(space.getRoomId());
        return new SpaceResponse(
                space.getRoomId(),
                space.getStatus() != null ? space.getStatus().name() : null,
                space.getDimensions(),
                space.getMainColor(),
                space.getStyle(),
                styleConfidence,
                space.getPreferredStyle(),
                space.getAnalysisDate(),
                space.getUploadedAt()
        );
    }
}
