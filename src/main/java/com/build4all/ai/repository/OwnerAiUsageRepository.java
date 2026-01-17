package com.build4all.ai.repository;

import com.build4all.ai.domain.OwnerAiUsage;
import com.build4all.ai.domain.OwnerAiUsageId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface OwnerAiUsageRepository extends JpaRepository<OwnerAiUsage, OwnerAiUsageId> {
    Optional<OwnerAiUsage> findByOwnerIdAndUsageDate(Long ownerId, LocalDate usageDate);
}
