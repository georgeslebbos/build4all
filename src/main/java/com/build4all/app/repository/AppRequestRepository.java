package com.build4all.app.repository;

import com.build4all.app.domain.AppRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * Simple repository to list/lookup requests by owner or status.
 */
public interface AppRequestRepository extends JpaRepository<AppRequest, Long> {
    List<AppRequest> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    List<AppRequest> findByStatusOrderByCreatedAtAsc(String status);
}
