package com.build4all.user.repository;

import com.build4all.user.domain.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * UserStatusRepository
 *
 * Table: user_status
 * Entity: UserStatus (id, name)
 *
 * Purpose:
 * - Read user status rows like: ACTIVE, INACTIVE, DELETED, INACTIVEBYADMIN, ...
 * - Used by services/controllers when toggling or validating user state.
 */
public interface UserStatusRepository extends JpaRepository<UserStatus, Long> {

    /**
     * Find a status row by exact name (case-sensitive behavior depends on DB collation).
     *
     * Typical SQL:
     *   SELECT *
     *   FROM user_status
     *   WHERE name = :name
     *   LIMIT 1;
     *
     * Notes:
     * - If your DB column collation is case-insensitive (common in MySQL), this may behave like IgnoreCase.
     * - In PostgreSQL, this is case-sensitive unless you use ILIKE/LOWER.
     */
    Optional<UserStatus> findByName(String name);

    /**
     * Find a status row by name ignoring case.
     *
     * Typical SQL (portable approach):
     *   SELECT *
     *   FROM user_status
     *   WHERE LOWER(name) = LOWER(:name)
     *   LIMIT 1;
     *
     * Notes:
     * - Spring Data will generate a LOWER/UPPER based query depending on the dialect.
     * - Use this if you store values like "ACTIVE" but callers pass "active".
     */
    Optional<UserStatus> findByNameIgnoreCase(String name);

    /**
     * Check if a status exists by exact name.
     *
     * Typical SQL:
     *   SELECT EXISTS(
     *     SELECT 1
     *     FROM user_status
     *     WHERE name = :name
     *   );
     */
    boolean existsByName(String name);
}
