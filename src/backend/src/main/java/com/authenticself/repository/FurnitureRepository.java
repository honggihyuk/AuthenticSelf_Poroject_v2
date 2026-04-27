package com.authenticself.repository;

import com.authenticself.domain.Furniture;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * JPA repository for the {@code furniture} catalog
 * (UC-01-recommendation FR-17).
 * <p>
 * Task 5 does not add any custom queries — the seeded catalog is ~24
 * rows, so {@link JpaRepository#findAll()} is sufficient. Pagination is
 * listed as out-of-scope in the spec; revisit when the catalog grows.
 */
public interface FurnitureRepository extends JpaRepository<Furniture, String> {
}
