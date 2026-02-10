package com.build4all.licensing.repository;

import com.build4all.licensing.domain.PlanUpgradeRequest;
import com.build4all.licensing.domain.PlanUpgradeRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanUpgradeRequestRepository extends JpaRepository<PlanUpgradeRequest, Long> {

    Optional<PlanUpgradeRequest> findTopByAupIdAndStatusOrderByRequestedAtDesc(Long aupId, PlanUpgradeRequestStatus status);

    List<PlanUpgradeRequest> findByStatusOrderByRequestedAtAsc(PlanUpgradeRequestStatus status);

    List<PlanUpgradeRequest> findByAupIdOrderByRequestedAtDesc(Long aupId);
}
