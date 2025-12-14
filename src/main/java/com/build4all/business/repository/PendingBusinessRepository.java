package com.build4all.business.repository;

import com.build4all.business.domain.PendingBusiness;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * PendingBusinessRepository
 * ------------------------------------------------------------
 * Repository for "PendingBusiness" records (pre-registration / verification stage).
 *
 * Typical flow:
 * 1) User starts business registration (email or phone).
 * 2) You create/update a PendingBusiness row and store a verification code.
 * 3) After verification, you create the real Businesses entity and delete the pending row.
 */
@Repository
public interface PendingBusinessRepository extends JpaRepository<PendingBusiness, Long> {

    /**
     * Checks whether a PendingBusiness already exists with the given email.
     *
     * Equivalent SQL (conceptual):
     * SELECT CASE WHEN COUNT(*) > 0 THEN TRUE ELSE FALSE END
     * FROM pending_businesses pb
     * WHERE pb.email = :email;
     *
     * Notes:
     * - If email can be NULL in your table, passing null here may behave differently
     *   depending on DB + Spring Data. In practice, always pass a non-null email.
     */
    boolean existsByEmail(String email);

    /**
     * Fetches the pending record by email (or returns null if not found).
     *
     * Equivalent SQL (conceptual):
     * SELECT pb.*
     * FROM pending_businesses pb
     * WHERE pb.email = :email
     * LIMIT 1;
     *
     * Notes:
     * - You used "PendingBusiness" (not Optional). So "not found" returns null.
     * - If the DB allows duplicates (it shouldn't), this may throw a non-unique result error.
     */
    PendingBusiness findByEmail(String email);

    /**
     * Checks whether a PendingBusiness already exists with the given phone number.
     *
     * Equivalent SQL (conceptual):
     * SELECT CASE WHEN COUNT(*) > 0 THEN TRUE ELSE FALSE END
     * FROM pending_businesses pb
     * WHERE pb.phone_number = :phoneNumber;
     *
     * Notes:
     * - Your entity has @Column(name="phone_number", unique=true) so ideally only 0 or 1 row exists.
     */
    boolean existsByPhoneNumber(String phoneNumber);

    /**
     * Fetches the pending record by phone number (or returns null if not found).
     *
     * Equivalent SQL (conceptual):
     * SELECT pb.*
     * FROM pending_businesses pb
     * WHERE pb.phone_number = :phoneNumber
     * LIMIT 1;
     *
     * Notes:
     * - Same null-return behavior as findByEmail(...).
     */
    PendingBusiness findByPhoneNumber(String phoneNumber);

}
