package com.build4all.security.repository;

import com.build4all.security.domain.AuthTokenRevocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthTokenRevocationRepository extends JpaRepository<AuthTokenRevocation, Long> {
    Optional<AuthTokenRevocation> findBySubjectTypeAndSubjectId(String subjectType, Long subjectId);
}