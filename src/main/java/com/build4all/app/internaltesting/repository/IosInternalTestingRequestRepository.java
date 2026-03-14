package com.build4all.app.internaltesting.repository;

import com.build4all.app.internaltesting.domain.IosInternalTestingRequest;
import com.build4all.app.internaltesting.domain.IosInternalTestingRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IosInternalTestingRequestRepository extends JpaRepository<IosInternalTestingRequest, Long> {

    Optional<IosInternalTestingRequest> findTopByOwnerProjectLinkIdOrderByCreatedAtDesc(Long ownerProjectLinkId);

    Optional<IosInternalTestingRequest> findTopByOwnerProjectLinkIdAndAppleEmailIgnoreCaseOrderByCreatedAtDesc(
            Long ownerProjectLinkId,
            String appleEmail
    );

    List<IosInternalTestingRequest> findByStatusOrderByCreatedAtAsc(IosInternalTestingRequestStatus status);

    List<IosInternalTestingRequest> findByStatusInOrderByCreatedAtAsc(
            Collection<IosInternalTestingRequestStatus> statuses
    );
}