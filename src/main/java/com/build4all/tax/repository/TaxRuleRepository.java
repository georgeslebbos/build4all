package com.build4all.tax.repository;

import com.build4all.tax.domain.TaxRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaxRuleRepository extends JpaRepository<TaxRule, Long> {

    /**
     * All enabled tax rules for a given owner project.
     * Used by TaxServiceImpl.
     */
    @Query("""
           SELECT r
           FROM TaxRule r
           WHERE r.ownerProject.id = :ownerProjectId
             AND r.enabled = true
           """)
    List<TaxRule> findByOwnerProjectIdAndEnabledTrue(@Param("ownerProjectId") Long ownerProjectId);
}
