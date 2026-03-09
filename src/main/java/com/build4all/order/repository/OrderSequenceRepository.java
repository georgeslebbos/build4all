package com.build4all.order.repository;

import com.build4all.order.domain.OrderSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderSequenceRepository extends JpaRepository<OrderSequence, Long> {

    /**
     * Atomic allocator:
     * - if row does not exist: create it with next_seq = 2 and return 1
     * - if row exists: increment next_seq and return previous value
     */
    @Query(value = """
        INSERT INTO order_sequences(owner_project_id, next_seq)
        VALUES (:opId, 2)
        ON CONFLICT (owner_project_id)
        DO UPDATE SET next_seq = order_sequences.next_seq + 1
        RETURNING next_seq - 1
    """, nativeQuery = true)
    Long allocateNext(@Param("opId") Long ownerProjectId);
}