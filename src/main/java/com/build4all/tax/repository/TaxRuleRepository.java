package com.build4all.tax.repository;

import com.build4all.tax.domain.TaxRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for TaxRule.
 *
 * Purpose:
 * - Provides database access (CRUD + simple queries) for {@link TaxRule}.
 * - Uses Spring Data JPA derived query methods, so we don't need to write JPQL manually.
 *
 * Note: we are filtering by ownerProject.id because
 * ownerProject is a ManyToOne relation.
 *
 * In multi-tenant Build4All logic:
 * - ownerProjectId = the app/tenant scope (AdminUserProject).
 * - Every tax rule belongs to exactly one ownerProject (tenant).
 */
public interface TaxRuleRepository extends JpaRepository<TaxRule, Long> {

    /**
     * List all rules for a given owner project (tenant/app).
     *
     * This is typically used by admin/owner screens to manage tax rules,
     * so it returns both enabled and disabled rules.
     *
     * Filter by FK of relation: ownerProject.id
     */
    List<TaxRule> findByOwnerProject_Id(Long ownerProjectId);

    /**
     * List only enabled rules for a given owner project.
     *
     * This is typically used by checkout calculations (Order/CheckoutPricing),
     * so disabled rules are ignored.
     */
    List<TaxRule> findByOwnerProject_IdAndEnabledTrue(Long ownerProjectId);

    // secure scoping by tenant (prevents cross-tenant update/read/delete)
    Optional<TaxRule> findByIdAndOwnerProject_Id(Long id, Long ownerProjectId);
}
