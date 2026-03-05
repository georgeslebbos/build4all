package com.build4all.order.repository;

import com.build4all.order.domain.OrderSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderSequenceRepository extends JpaRepository<OrderSequence, Long> {

    /**
     * ✅ Atomic allocator (safe under concurrency + multi-instances)
     * Ensures row exists, increments next_seq, returns allocated seq.
     */
    @Query(value = """
        WITH ins AS (
            INSERT INTO order_sequences(owner_project_id, next_seq)
            VALUES (:opId, 1)
            ON CONFLICT (owner_project_id) DO NOTHING
        ),
        upd AS (
            UPDATE order_sequences
            SET next_seq = next_seq + 1
            WHERE owner_project_id = :opId
            RETURNING next_seq - 1 AS allocated
        )
        SELECT allocated FROM upd
    """, nativeQuery = true)
    Long allocateNext(@Param("opId") Long ownerProjectId);
}