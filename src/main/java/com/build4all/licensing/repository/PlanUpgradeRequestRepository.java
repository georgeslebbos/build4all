package com.build4all.licensing.repository;

import com.build4all.licensing.domain.PlanUpgradeRequest;
import com.build4all.licensing.domain.PlanUpgradeRequestStatus;
import com.build4all.licensing.dto.PendingUpgradeRequestRow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlanUpgradeRequestRepository extends JpaRepository<PlanUpgradeRequest, Long> {

    Optional<PlanUpgradeRequest> findTopByAupIdAndStatusOrderByRequestedAtDesc(Long aupId, PlanUpgradeRequestStatus status);

    List<PlanUpgradeRequest> findByStatusOrderByRequestedAtAsc(PlanUpgradeRequestStatus status);

    List<PlanUpgradeRequest> findByAupIdOrderByRequestedAtDesc(Long aupId);
    
    Optional<PlanUpgradeRequest> findTopByAupIdOrderByRequestedAtDesc(Long aupId);
    
    @Query("""
            select new com.build4all.licensing.dto.PendingUpgradeRequestRow(
                r.id,
                r.aupId,
                a.appName,
                a.slug,
                r.requestedPlanCode,
                r.usersAllowedOverride,
                r.requestedAt
            )
            from PlanUpgradeRequest r
            join com.build4all.admin.domain.AdminUserProject a
                on a.id = r.aupId
            where r.status = :status
            order by r.requestedAt asc
        """)
        List<PendingUpgradeRequestRow> findPendingRows(@Param("status") PlanUpgradeRequestStatus status);
}
