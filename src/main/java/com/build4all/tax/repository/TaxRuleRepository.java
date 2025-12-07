package com.build4all.tax.repository;

import com.build4all.tax.domain.TaxRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for TaxRule.
 * Note: we are filtering by ownerProject.id because
 * ownerProject is a ManyToOne relation.
 */
public interface TaxRuleRepository extends JpaRepository<TaxRule, Long> {

    // Filter by FK of relation: ownerProject.id
    List<TaxRule> findByOwnerProject_Id(Long ownerProjectId);

    // Only enabled rules for a specific ownerProject
    List<TaxRule> findByOwnerProject_IdAndEnabledTrue(Long ownerProjectId);
}
