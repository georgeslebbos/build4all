package com.build4all.security.repository;

import com.build4all.security.domain.AuthTokenRevocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthTokenRevocationRepository extends JpaRepository<AuthTokenRevocation, Long> {

    Optional<AuthTokenRevocation> findBySubjectTypeAndSubjectIdAndOwnerProjectId(
            String subjectType,
            Long subjectId,
            Long ownerProjectId
    );

    Optional<AuthTokenRevocation> findBySubjectTypeAndSubjectIdAndOwnerProjectIdIsNull(
            String subjectType,
            Long subjectId
    );
}