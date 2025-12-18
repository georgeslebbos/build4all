package com.build4all.home.sections.repository;

import com.build4all.home.sections.domain.HomeSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * HomeSectionRepository
 *
 * Purpose:
 * - Spring Data JPA repository for HomeSection (home page sections).
 * - Provides CRUD operations + derived query methods for:
 *   1) listing sections per tenant/app (AdminUserProject / aup_id)
 *   2) listing only active sections (public home page)
 *   3) finding a section by its stable code (per tenant)
 *
 * Multi-tenant rules:
 * - We always filter by ownerProject.id which maps to AdminUserProject.aup_id.
 * - Section code is unique per tenant due to the DB constraint (aup_id, code).
 *
 * Sorting:
 * - sortOrder controls the order of sections on the home page (ascending).
 */
public interface HomeSectionRepository extends JpaRepository<HomeSection, Long> {

    /**
     * List ALL sections (active + inactive) for a tenant/app, ordered by sortOrder.
     * Useful for OWNER/admin management screens.
     */
    List<HomeSection> findByOwnerProject_IdOrderBySortOrderAsc(Long ownerProjectId);

    /**
     * List ONLY active sections for a tenant/app, ordered by sortOrder.
     * Used by the public Home endpoint to show sections visible to users.
     */
    List<HomeSection> findByOwnerProject_IdAndActiveTrueOrderBySortOrderAsc(Long ownerProjectId);

    /**
     * Find a section by its code within the same tenant/app.
     * Used when adding/removing products by sectionCode.
     */
    Optional<HomeSection> findByOwnerProject_IdAndCode(Long ownerProjectId, String code);
}
