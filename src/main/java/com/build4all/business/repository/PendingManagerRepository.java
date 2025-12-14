package com.build4all.business.repository;

import com.build4all.business.domain.PendingManager;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository // Marks this interface as a Spring bean (DAO layer) so it can be injected with @Autowired / constructor injection.
public interface PendingManagerRepository extends JpaRepository<PendingManager, Long> {
    /*
     * JpaRepository<PendingManager, Long> gives you ready-made CRUD operations like:
     * - save(entity)
     * - findById(id)
     * - findAll()
     * - delete(entity) / deleteById(id)
     * - existsById(id)
     *
     * It also enables Spring Data JPA "derived queries" (method-name queries).
     */

    /**
     * Find a PendingManager invitation by its unique token.
     *
     * Typical use case:
     * - Manager clicks an invitation link containing ?token=...
     * - Backend validates token exists and is still valid (if you implement expiration rules)
     * - Backend uses the business_id from the PendingManager to attach the new BusinessUser to that business
     *
     * Equivalent SQL (typical):
     *   SELECT *
     *   FROM pending_managers
     *   WHERE token = ?;
     *
     * Notes:
     * - Your entity table is "PendingManagers" (capital P/M) in @Table(name="PendingManagers").
     *   Hibernate will generate SQL using that exact table name unless you configured naming strategies.
     * - This method returns Optional so you can safely handle "not found" without null.
     */
    Optional<PendingManager> findByToken(String token);

    /**
     * Find a PendingManager by email.
     *
     * Typical use case:
     * - Prevent sending multiple invitations to the same email
     * - Check if an invitation already exists for that email
     *
     * Equivalent SQL (typical):
     *   SELECT *
     *   FROM pending_managers
     *   WHERE email = ?;
     *
     * Notes:
     * - In your PendingManager entity, email is NOT unique.
     *   If multiple rows can exist for same email, this method may throw
     *   IncorrectResultSizeDataAccessException at runtime.
     *   If you want to allow multiple invitations per email, change this to:
     *     List<PendingManager> findAllByEmail(String email);
     */
    Optional<PendingManager> findByEmail(String email);
}
