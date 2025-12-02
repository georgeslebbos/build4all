package com.build4all.homebanner.service;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.homebanner.domain.HomeBanner;
import com.build4all.homebanner.dto.HomeBannerRequest;
import com.build4all.homebanner.dto.HomeBannerResponse;
import com.build4all.homebanner.repository.HomeBannerRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class HomeBannerService {

    private final HomeBannerRepository bannerRepo;
    private final AdminUserProjectRepository aupRepo;

    public HomeBannerService(HomeBannerRepository bannerRepo,
                             AdminUserProjectRepository aupRepo) {
        this.bannerRepo = bannerRepo;
        this.aupRepo = aupRepo;
    }

    // -------- PUBLIC (for app) --------

    @Transactional(Transactional.TxType.SUPPORTS)
    public List<HomeBannerResponse> listActivePublic(Long ownerProjectId) {
        LocalDateTime now = LocalDateTime.now();
        return bannerRepo.findActiveBannersForOwnerProject(ownerProjectId, now)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // -------- ADMIN (JWT) --------

    @Transactional(Transactional.TxType.SUPPORTS)
    public List<HomeBannerResponse> listByOwnerProjectForAdmin(Long ownerProjectId, Long adminId) {
        AdminUserProject app = requireOwnedProject(ownerProjectId, adminId);
        return bannerRepo.findByOwnerProject_IdOrderBySortOrderAscCreatedAtDesc(app.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public HomeBannerResponse create(Long adminId, HomeBannerRequest req) {
        if (req.getOwnerProjectId() == null) {
            throw new IllegalArgumentException("ownerProjectId is required");
        }
        if (req.getImageUrl() == null || req.getImageUrl().isBlank()) {
            throw new IllegalArgumentException("imageUrl is required");
        }

        AdminUserProject app = requireOwnedProject(req.getOwnerProjectId(), adminId);

        HomeBanner b = new HomeBanner();
        b.setOwnerProject(app);
        b.setImageUrl(req.getImageUrl());
        b.setTitle(req.getTitle());
        b.setSubtitle(req.getSubtitle());
        b.setTargetType(req.getTargetType());
        b.setTargetId(req.getTargetId());
        b.setTargetUrl(req.getTargetUrl());
        b.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        b.setActive(req.getActive() == null || req.getActive());
        b.setStartAt(req.getStartAt());
        b.setEndAt(req.getEndAt());

        b = bannerRepo.save(b);
        return toResponse(b);
    }

    public HomeBannerResponse update(Long adminId, Long id, HomeBannerRequest req) {
        HomeBanner b = requireOwnedBanner(id, adminId);

        if (req.getImageUrl() != null) b.setImageUrl(req.getImageUrl());
        if (req.getTitle() != null) b.setTitle(req.getTitle());
        if (req.getSubtitle() != null) b.setSubtitle(req.getSubtitle());
        if (req.getTargetType() != null) b.setTargetType(req.getTargetType());
        if (req.getTargetId() != null) b.setTargetId(req.getTargetId());
        if (req.getTargetUrl() != null) b.setTargetUrl(req.getTargetUrl());
        if (req.getSortOrder() != null) b.setSortOrder(req.getSortOrder());
        if (req.getActive() != null) b.setActive(req.getActive());
        if (req.getStartAt() != null) b.setStartAt(req.getStartAt());
        if (req.getEndAt() != null) b.setEndAt(req.getEndAt());

        b = bannerRepo.save(b);
        return toResponse(b);
    }

    public void delete(Long adminId, Long id) {
        HomeBanner b = requireOwnedBanner(id, adminId);
        bannerRepo.delete(b);
    }

    // -------- helpers --------

    private AdminUserProject requireOwnedProject(Long ownerProjectId, Long adminId) {
        return aupRepo.findById(ownerProjectId)
                .filter(aup -> aup.getAdminId() != null && aup.getAdminId().equals(adminId))
                .orElseThrow(() -> new IllegalArgumentException("App not found or not yours"));
    }

    private HomeBanner requireOwnedBanner(Long bannerId, Long adminId) {
        HomeBanner b = bannerRepo.findById(bannerId)
                .orElseThrow(() -> new IllegalArgumentException("Banner not found"));
        AdminUserProject app = b.getOwnerProject();
        if (app == null || app.getAdminId() == null || !app.getAdminId().equals(adminId)) {
            throw new IllegalArgumentException("Banner not found or not yours");
        }
        return b;
    }

    private HomeBannerResponse toResponse(HomeBanner b) {
        return new HomeBannerResponse(
                b.getId(),
                b.getImageUrl(),
                b.getTitle(),
                b.getSubtitle(),
                b.getTargetType(),
                b.getTargetId(),
                b.getTargetUrl(),
                b.getSortOrder(),
                b.getStartAt(),
                b.getEndAt()
        );
    }
}