package com.authenticself.ai;

import com.authenticself.domain.Space;
import com.authenticself.repository.SpaceRepository;
import com.authenticself.space.PreferredStyle;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Narrow persistence collaborator for {@link AIOrchestrator} and
 * {@link com.authenticself.space.SpaceController} (FR-12, FR-14).
 * <p>
 * Lives in a SEPARATE bean so each {@code @Transactional} call goes through
 * the Spring AOP proxy — self-invocation within {@code AIOrchestrator.analyze}
 * would bypass the proxy and defeat the declarative transaction boundary
 * (and FR-12 step 6's "don't hold a DB connection during the HTTP call").
 *
 * <p>Every write method here opens its OWN transaction
 * ({@link Propagation#REQUIRES_NEW}); callers are guaranteed that no JDBC
 * connection is checked out between calls.
 */
@Component
public class SpaceAnalysisPersistence {

    private final SpaceRepository spaces;

    public SpaceAnalysisPersistence(SpaceRepository spaces) {
        this.spaces = spaces;
    }

    /** Load just the fields needed for the HTTP call — read-only tx. */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Snapshot loadForAnalysis(String roomId) {
        Space space = spaces.findById(roomId)
                .orElseThrow(() -> new SpaceNotFoundException(roomId));
        return new Snapshot(space.getStatus(), space.getPhotoUrl());
    }

    /**
     * Happy-path write (Task-4 FR-12 "both succeeded"): flips the row to
     * ANALYZED and records all outputs. Nullable {@code style} supports the
     * Task-4 FR-12 "space succeeded, style failed" partial-success path —
     * callers pass {@code null} to leave {@code spaces.style} unchanged.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAnalyzed(String roomId, String dimensions, String mainColor, String style) {
        Space space = spaces.findById(roomId)
                .orElseThrow(() -> new SpaceNotFoundException(roomId));
        space.setDimensions(dimensions);
        space.setMainColor(mainColor);
        if (style != null) {
            space.setStyle(style);
        }
        space.setAnalysisDate(LocalDateTime.now(ZoneOffset.UTC));
        space.setStatus(Space.Status.ANALYZED);
        spaces.save(space);
    }

    /**
     * @deprecated Kept for backwards compatibility with UC-01-space-analysis
     *     call sites that don't know about Task-4's style field. Delegates
     *     to {@link #markAnalyzed(String, String, String, String)} with a
     *     {@code null} style.
     */
    @Deprecated
    public void markAnalyzed(String roomId, String dimensions, String mainColor) {
        markAnalyzed(roomId, dimensions, mainColor, null);
    }

    /** Analyzer-failure write — flip to FAILED, leave analysis columns NULL. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String roomId) {
        Space space = spaces.findById(roomId)
                .orElseThrow(() -> new SpaceNotFoundException(roomId));
        space.setAnalysisDate(LocalDateTime.now(ZoneOffset.UTC));
        space.setStatus(Space.Status.FAILED);
        spaces.save(space);
    }

    /** Load the full row for the public {@code GET /api/v1/spaces/{roomId}}. */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Space loadForRead(String roomId) {
        return spaces.findById(roomId)
                .orElseThrow(() -> new SpaceNotFoundException(roomId));
    }

    /**
     * Task-4 FR-14 public PUT — set the user's preferred style. Caller has
     * already validated the enum value and the status transition.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Space setPreferredStyle(String roomId, PreferredStyle preferredStyle) {
        Space space = spaces.findById(roomId)
                .orElseThrow(() -> new SpaceNotFoundException(roomId));
        space.setPreferredStyle(preferredStyle);
        return spaces.save(space);
    }

    /** Result of {@link #loadForAnalysis}. */
    public record Snapshot(Space.Status status, String photoUrl) { }
}
