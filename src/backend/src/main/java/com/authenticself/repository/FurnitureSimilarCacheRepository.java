package com.authenticself.repository;

import com.authenticself.domain.FurnitureSimilarCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Read + write access for the Phase B cache.
 *
 * <p>The read path is the public {@code GET /api/v1/furniture/{id}/similar}.
 * Writes happen in the nightly batch — typically replace-on-rerun via the
 * {@link #deleteByFurnitureIdAndSource} helper before re-inserting the new
 * top-N. Keeping the delete + insert as two simple ops avoids an UPSERT
 * dialect dependency.</p>
 */
public interface FurnitureSimilarCacheRepository
        extends JpaRepository<FurnitureSimilarCache, FurnitureSimilarCache.PK> {

    @Query("""
        SELECT c FROM FurnitureSimilarCache c
        WHERE c.furnitureId = :furnitureId
        ORDER BY c.similarityScore DESC, c.rankOrder ASC
    """)
    List<FurnitureSimilarCache> findByFurnitureIdRanked(@Param("furnitureId") String furnitureId);

    @Modifying
    @Query("DELETE FROM FurnitureSimilarCache c WHERE c.furnitureId = :furnitureId AND c.source = :source")
    int deleteByFurnitureIdAndSource(@Param("furnitureId") String furnitureId, @Param("source") String source);
}
