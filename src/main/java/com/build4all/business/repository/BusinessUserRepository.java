package com.build4all.business.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.build4all.business.domain.BusinessUser;

/**
 * BusinessUserRepository
 * ------------------------------------------------------------
 * Repository for BusinessUser accounts.
 *
 * A BusinessUser belongs to exactly ONE Businesses record (FK: business_id).
 * This repository gives you CRUD + one custom finder.
 */
public interface BusinessUserRepository extends JpaRepository<BusinessUser, Long> {

    /**
     * Returns all BusinessUser rows that belong to a given business (by business_id).
     *
     * Spring Data meaning:
     * - "findByBusiness_Id" means:
     *   follow the "business" relationship in BusinessUser,
     *   then filter by the "id" column of Businesses.
     *
     * Equivalent SQL (conceptual):
     * SELECT bu.*
     * FROM business_user bu
     * WHERE bu.business_id = :businessId;
     *
     * Notes:
     * - No JOIN is required because business_id is already stored in business_user.
     * - Result order is not guaranteed unless you add OrderBy in the query method
     *   (or add Sort/Pageable).
     */
    List<BusinessUser> findByBusiness_Id(Long businessId);
}
