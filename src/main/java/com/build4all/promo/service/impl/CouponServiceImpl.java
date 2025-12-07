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

        // ✅ Use value from REQUEST
        BigDecimal value = req.getDiscountValue();
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Coupon value must be > 0");
        }

        CouponDiscountType type =
                CouponDiscountType.valueOf(req.getDiscountType().toUpperCase());

        Coupon coupon = new Coupon();
        coupon.setOwnerProjectId(req.getOwnerProjectId());
        coupon.setCode(req.getCode());
        coupon.setDescription(req.getDescription());
        coupon.setType(type);
        coupon.setValue(value);
        coupon.setGlobalUsageLimit(req.getMaxUses());
        coupon.setMinOrderAmount(req.getMinOrderAmount());
        coupon.setMaxDiscountAmount(req.getMaxDiscountAmount());
        coupon.setValidFrom(req.getStartsAt());
        coupon.setValidTo(req.getExpiresAt());
        coupon.setActive(req.isActive());
        coupon.setUsedCount(0);

        coupon = couponRepository.save(coupon);

        return toResponse(coupon);
    }

    /* ============================
       UPDATE / DELETE / GET / LIST
       ============================ */

    @Override
    public Coupon update(Long id, Coupon updates) {
        Coupon existing = couponRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Coupon not found"));

        if (updates.getCode() != null) existing.setCode(updates.getCode());
        if (updates.getDescription() != null) existing.setDescription(updates.getDescription());
        if (updates.getType() != null) existing.setType(updates.getType());
        if (updates.getValue() != null) existing.setValue(updates.getValue());
        if (updates.getGlobalUsageLimit() != null) existing.setGlobalUsageLimit(updates.getGlobalUsageLimit());
        if (updates.getMinOrderAmount() != null) existing.setMinOrderAmount(updates.getMinOrderAmount());
        if (updates.getMaxDiscountAmount() != null) existing.setMaxDiscountAmount(updates.getMaxDiscountAmount());
        if (updates.getValidFrom() != null) existing.setValidFrom(updates.getValidFrom());
        if (updates.getValidTo() != null) existing.setValidTo(updates.getValidTo());

        existing.setActive(updates.isActive());

        return couponRepository.save(existing);
    }

    @Override
    public void delete(Long id) {
        if (!couponRepository.existsById(id)) {
            throw new IllegalArgumentException("Coupon not found");
        }
        couponRepository.deleteById(id);
    }

    @Override
    public Coupon get(Long id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Coupon not found"));
    }

    @Override
    public List<Coupon> listByOwnerProject(Long ownerProjectId) {
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
        } else if (coupon.getType() == CouponDiscountType.FREE_SHIPPING) {
            // handled on shipping side; here we do nothing or return 0
            discount = BigDecimal.ZERO;
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

    private CouponResponse toResponse(Coupon c) {
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
