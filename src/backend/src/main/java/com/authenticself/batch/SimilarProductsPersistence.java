package com.authenticself.batch;

import com.authenticself.domain.FurnitureSimilarCache;
import com.authenticself.repository.FurnitureSimilarCacheRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Phase B — persistence collaborator for {@link SimilarProductsBatch}.
 *
 * <p>Lives in a SEPARATE bean because @Transactional via Spring AOP only fires
 * for cross-bean invocations. The batch's outer {@code runOnce()} loop calls
 * a {@code @Transactional} method on THIS bean per furniture row, so each row
 * gets its own short transaction (delete + insert top-N).
 *
 * <p>The {@code REQUIRES_NEW} propagation guarantees the batch's enclosing
 * call site (if any — e.g. an admin HTTP request) cannot accidentally hold
 * a long connection across 28 Naver round-trips.
 */
@Component
public class SimilarProductsPersistence {

    private final FurnitureSimilarCacheRepository cacheRepository;

    public SimilarProductsPersistence(FurnitureSimilarCacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
    }

    /** Replace this furniture's NAVER cache rows with the supplied list. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int replace(String furnitureId, String source, List<FurnitureSimilarCache> rows) {
        cacheRepository.deleteByFurnitureIdAndSource(furnitureId, source);
        if (rows.isEmpty()) return 0;
        cacheRepository.saveAll(rows);
        return rows.size();
    }
}
