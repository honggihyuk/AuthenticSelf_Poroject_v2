package com.authenticself.repository;

import com.authenticself.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * JPA repository for the {@code users} table. This task uses it only for
 * the existence check behind the {@code X-User-Id} stub-auth (FR-4 / AC-9).
 */
public interface UserRepository extends JpaRepository<User, String> {
}
