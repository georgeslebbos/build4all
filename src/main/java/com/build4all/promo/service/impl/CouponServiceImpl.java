package com.build4all.promo.service.impl;

import com.build4all.promo.domain.Coupon;
import com.build4all.promo.domain.CouponDiscountType;
import com.build4all.promo.dto.CouponRequest;
import com.build4all.promo.dto.CouponResponse;
import com.build4all.promo.repository.CouponRepository;
import com.build4all.promo.service.CouponService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class CouponServiceImpl implements CouponService {

    private final CouponRepository couponRepository;

    public CouponServiceImpl(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    /* ============================
       CREATE
       ============================ */

    @Override
    public CouponResponse create(CouponRequest req) {

        if (req.getOwnerProjectId() == null) {
            throw new IllegalArgumentException("ownerProjectId is required");
        }
        if (req.getCode() == null || req.getCode().isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        if (req.getDiscountType() == null || req.getDiscountType().isBlank()) {
            throw new IllegalArgumentException("discountType is required");
        }

        CouponDiscountType type =
                CouponDiscountType.valueOf(req.getDiscountType().toUpperCase());

        // ✅ Validation depends on type:
        // - FREE_SHIPPING: allow value null/0
        // - others: must be > 0
        BigDecimal value = req.getDiscountValue();
        if (type != CouponDiscountType.FREE_SHIPPING) {
            if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("discountValue must be > 0");
            }
        } else {
            value = BigDecimal.ZERO;
        }

        // ✅ Per-tenant uniqueness check (better error than DB constraint)
        couponRepository.findByOwnerProjectIdAndCodeIgnoreCase(req.getOwnerProjectId(), req.getCode().trim())
                .ifPresent(c -> {
                    throw new IllegalArgumentException("Coupon code already exists for this ownerProjectId");
                });

        Coupon coupon = new Coupon();
        coupon.setOwnerProjectId(req.getOwnerProjectId());
        coupon.setCode(req.getCode().trim());
        coupon.setDescription(req.getDescription());
        coupon.setType(type);
        coupon.setValue(value);
        coupon.setGlobalUsageLimit(req.getMaxUses());
        coupon.setMinOrderAmount(req.getMinOrderAmount());
        coupon.setMaxDiscountAmount(req.getMaxDiscountAmount());
        coupon.setValidFrom(req.getStartsAt());
        coupon.setValidTo(req.getExpiresAt());

        // ✅ Default active=true if not provided
        coupon.setActive(req.getActive() == null ? true : req.getActive());

        coupon.setUsedCount(0);

        coupon = couponRepository.save(coupon);

        return toResponse(coupon);
    }

    /* ============================
       UPDATE / DELETE / GET / LIST
       ============================ */

    @Override
    public Coupon update(Long ownerProjectId, Long id, CouponRequest req) {
        if (ownerProjectId == null) {
            throw new IllegalArgumentException("ownerProjectId is required");
        }

        // ✅ tenant-scoped load
        Coupon existing = couponRepository.findByIdAndOwnerProjectId(id, ownerProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Coupon not found for this ownerProjectId"));

        if (req.getCode() != null && !req.getCode().isBlank()) {
            String newCode = req.getCode().trim();

            // prevent duplicates in same tenant
            couponRepository.findByOwnerProjectIdAndCodeIgnoreCase(ownerProjectId, newCode)
                    .ifPresent(other -> {
                        if (!other.getId().equals(existing.getId())) {
                            throw new IllegalArgumentException("Coupon code already exists for this ownerProjectId");
                        }
                    });

            existing.setCode(newCode);
        }

        if (req.getDescription() != null) existing.setDescription(req.getDescription());

        if (req.getDiscountType() != null && !req.getDiscountType().isBlank()) {
            CouponDiscountType type = CouponDiscountType.valueOf(req.getDiscountType().toUpperCase());
            existing.setType(type);

            // If switching to FREE_SHIPPING, force value to 0
            if (type == CouponDiscountType.FREE_SHIPPING) {
                existing.setValue(BigDecimal.ZERO);
            }
        }

        if (req.getDiscountValue() != null) {
            // For FREE_SHIPPING, ignore any value and force 0
            if (existing.getType() == CouponDiscountType.FREE_SHIPPING) {
                existing.setValue(BigDecimal.ZERO);
            } else {
                if (req.getDiscountValue().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("discountValue must be > 0");
                }
                existing.setValue(req.getDiscountValue());
            }
        }

        if (req.getMaxUses() != null) existing.setGlobalUsageLimit(req.getMaxUses());
        if (req.getMinOrderAmount() != null) existing.setMinOrderAmount(req.getMinOrderAmount());
        if (req.getMaxDiscountAmount() != null) existing.setMaxDiscountAmount(req.getMaxDiscountAmount());
        if (req.getStartsAt() != null) existing.setValidFrom(req.getStartsAt());
        if (req.getExpiresAt() != null) existing.setValidTo(req.getExpiresAt());

        // ✅ Only update active if client sent it
        if (req.getActive() != null) {
            existing.setActive(req.getActive());
        }

        return couponRepository.save(existing);
    }

    @Override
    public void delete(Long ownerProjectId, Long id) {
        if (ownerProjectId == null) {
            throw new IllegalArgumentException("ownerProjectId is required");
        }

        Coupon existing = couponRepository.findByIdAndOwnerProjectId(id, ownerProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Coupon not found for this ownerProjectId"));

        couponRepository.delete(existing);
    }

    @Override
    public Coupon get(Long ownerProjectId, Long id) {
        if (ownerProjectId == null) {
            throw new IllegalArgumentException("ownerProjectId is required");
        }

        return couponRepository.findByIdAndOwnerProjectId(id, ownerProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Coupon not found for this ownerProjectId"));
    }

    @Override
    public List<Coupon> listByOwnerProject(Long ownerProjectId) {
        if (ownerProjectId == null) {
            throw new IllegalArgumentException("ownerProjectId is required");
        }
        return couponRepository.findByOwnerProjectId(ownerProjectId);
    }

    /* ============================
       VALIDATION / DISCOUNT LOGIC
       ============================ */

    @Override
    public Coupon validateForOrder(Long ownerProjectId,
                                   String code,
                                   BigDecimal itemsSubtotal) {

        if (ownerProjectId == null) {
            throw new IllegalArgumentException("ownerProjectId is required");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("coupon code is required");
        }

        Coupon coupon = couponRepository
                .findByOwnerProjectIdAndCodeIgnoreCase(ownerProjectId, code.trim())
                .orElseThrow(() -> new IllegalArgumentException("Coupon not found"));

        if (!coupon.isActive()) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        if (coupon.getValidFrom() != null && now.isBefore(coupon.getValidFrom())) {
            return null;
        }
        if (coupon.getValidTo() != null && now.isAfter(coupon.getValidTo())) {
            return null;
        }

        if (coupon.getMinOrderAmount() != null
                && itemsSubtotal != null
                && itemsSubtotal.compareTo(coupon.getMinOrderAmount()) < 0) {
            return null;
        }

        if (coupon.getGlobalUsageLimit() != null
                && coupon.getUsedCount() != null
                && coupon.getUsedCount() >= coupon.getGlobalUsageLimit()) {
            return null;
        }

        return coupon;
    }

    @Override
    public BigDecimal computeDiscount(Coupon coupon, BigDecimal itemsSubtotal) {
        if (coupon == null) return BigDecimal.ZERO;
        if (itemsSubtotal == null || itemsSubtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal base = itemsSubtotal;

        // FREE_SHIPPING is handled in shipping logic
        if (coupon.getType() == CouponDiscountType.FREE_SHIPPING) {
            return BigDecimal.ZERO;
        }

        BigDecimal value = coupon.getValue();
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal discount = BigDecimal.ZERO;

        if (coupon.getType() == CouponDiscountType.PERCENT) {
            discount = base.multiply(value)
                    .divide(BigDecimal.valueOf(100));
        } else if (coupon.getType() == CouponDiscountType.FIXED) {
            discount = value;
        }

        if (coupon.getMaxDiscountAmount() != null
                && discount.compareTo(coupon.getMaxDiscountAmount()) > 0) {
            discount = coupon.getMaxDiscountAmount();
        }

        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            discount = BigDecimal.ZERO;
        }
        if (discount.compareTo(base) > 0) {
            discount = base;
        }

        return discount;
    }

    /* ============================
       MAPPER
       ============================ */

    @Override
    public CouponResponse toResponse(Coupon c) {
        CouponResponse r = new CouponResponse();
        r.setId(c.getId());
        r.setOwnerProjectId(c.getOwnerProjectId());
        r.setCode(c.getCode());
        r.setDescription(c.getDescription());
        r.setDiscountType(c.getType() != null ? c.getType().name() : null);
        r.setDiscountValue(c.getValue());
        r.setMaxUses(c.getGlobalUsageLimit());
        r.setMinOrderAmount(c.getMinOrderAmount());
        r.setMaxDiscountAmount(c.getMaxDiscountAmount());
        r.setStartsAt(c.getValidFrom());
        r.setExpiresAt(c.getValidTo());
        r.setActive(c.isActive());
        return r;
    }
}
