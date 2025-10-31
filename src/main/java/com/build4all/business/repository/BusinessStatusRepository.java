package com.build4all.business.repository;

import com.build4all.business.domain.BusinessStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BusinessStatusRepository extends JpaRepository<BusinessStatus, Long> {
    Optional<BusinessStatus> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
}
