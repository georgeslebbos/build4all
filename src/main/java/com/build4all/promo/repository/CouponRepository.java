package com.build4all.promo.repository;

import com.build4all.promo.domain.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    List<Coupon> findByOwnerProjectId(Long ownerProjectId);

    Optional<Coupon> findByOwnerProjectIdAndCodeIgnoreCase(Long ownerProjectId, String code);

    /**
     * ✅ Multi-tenant safety:
     * Prevent cross-tenant access by guessing coupon IDs.
     * All update/get/delete should use this method.
     */
    Optional<Coupon> findByIdAndOwnerProjectId(Long id, Long ownerProjectId);
    
    
    @Modifying
    @Query(value = """
    update coupons
    set used_count = coalesce(used_count,0) + 1
    where owner_project_id = :ownerProjectId
      and lower(code) = lower(:code)
      and active = true
      and (global_usage_limit is null or coalesce(used_count,0) < global_usage_limit)
    """, nativeQuery = true)
    int consumeIfAvailable(@Param("ownerProjectId") Long ownerProjectId,
                           @Param("code") String code);
    
    
    @Modifying
    @Query(value = """
    update coupons
    set used_count = greatest(coalesce(used_count,0) - 1, 0)
    where owner_project_id = :ownerProjectId
      and lower(code) = lower(:code)
      and coalesce(used_count,0) > 0
    """, nativeQuery = true)
    int releaseOne(@Param("ownerProjectId") Long ownerProjectId,
                   @Param("code") String code);
}
