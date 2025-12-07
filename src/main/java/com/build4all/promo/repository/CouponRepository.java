package com.build4all.promo.repository;

import com.build4all.promo.domain.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    List<Coupon> findByOwnerProjectId(Long ownerProjectId);

    Optional<Coupon> findByOwnerProjectIdAndCodeIgnoreCase(Long ownerProjectId, String code);
}
