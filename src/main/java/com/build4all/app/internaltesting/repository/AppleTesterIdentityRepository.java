package com.build4all.app.internaltesting.repository;

import com.build4all.app.internaltesting.domain.AppleTesterIdentity;
import com.build4all.app.internaltesting.domain.AppleTesterIdentityStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppleTesterIdentityRepository extends JpaRepository<AppleTesterIdentity, Long> {

    Optional<AppleTesterIdentity> findByNormalizedEmail(String normalizedEmail);

    boolean existsByNormalizedEmail(String normalizedEmail);

    List<AppleTesterIdentity> findByStatusInOrderByCreatedAtAsc(List<AppleTesterIdentityStatus> statuses);
}