package com.build4all.app.internaltesting.repository;

import com.build4all.app.internaltesting.domain.IosInternalTestingRequest;
import com.build4all.app.internaltesting.domain.IosInternalTestingRequestStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IosInternalTestingRequestRepository extends JpaRepository<IosInternalTestingRequest, Long> {

    Optional<IosInternalTestingRequest> findTopByOwnerProjectLinkIdAndAppleEmailIgnoreCaseOrderByCreatedAtDesc(
            Long ownerProjectLinkId,
            String appleEmail
    );

    List<IosInternalTestingRequest> findByOwnerProjectLinkId(Long ownerProjectLinkId, Sort sort);

    List<IosInternalTestingRequest> findByStatusInOrderByCreatedAtAsc(Collection<IosInternalTestingRequestStatus> statuses);

    long countByOwnerProjectLinkIdAndStatusIn(
            Long ownerProjectLinkId,
            Collection<IosInternalTestingRequestStatus> statuses
    );

    @Query("""
            select r
            from IosInternalTestingRequest r
            where r.appleTesterIdentity is not null
              and r.appleTesterIdentity.id = :identityId
            order by r.createdAt desc
            """)
    List<IosInternalTestingRequest> findByAppleTesterIdentityIdOrderByCreatedAtDesc(Long identityId);

    @Query("""
            select r
            from IosInternalTestingRequest r
            where lower(r.appleEmail) = lower(:appleEmail)
            order by r.createdAt desc
            """)
    List<IosInternalTestingRequest> findByAppleEmailOrderByCreatedAtDesc(String appleEmail);

    boolean existsByOwnerProjectLinkIdAndAppleEmailIgnoreCaseAndStatusIn(
            Long ownerProjectLinkId,
            String appleEmail,
            Collection<IosInternalTestingRequestStatus> statuses
    );
}