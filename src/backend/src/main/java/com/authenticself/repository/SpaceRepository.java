package com.authenticself.repository;

import com.authenticself.domain.Space;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * JPA repository for the {@code spaces} table. Parameterised queries only —
 * any future custom SQL must use JPQL or {@code @Query} with bind params.
 */
public interface SpaceRepository extends JpaRepository<Space, String> {

    /**
     * Used by {@link com.authenticself.ai.AnalysisPoller} to grab the next
     * batch of rows waiting for AI analysis. Relies on {@code idx_spaces_status}
     * from the V2 migration and orders by {@code uploaded_at ASC} so oldest
     * rows get processed first (FIFO-ish).
     * <p>
     * Derived-query name = {@code findTop<N>By<Prop>OrderBy<Prop>Asc}. We keep
     * the hard-coded "Top10" to avoid pulling in {@link org.springframework.data.domain.Pageable}
     * for a single caller; if the batch size ever needs to be configurable,
     * switch to a {@code @Query} with {@code LIMIT :size}.
     */
    List<Space> findTop10ByStatusOrderByUploadedAtAsc(Space.Status status);
}
