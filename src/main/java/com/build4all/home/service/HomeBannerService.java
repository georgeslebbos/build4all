package com.build4all.home.service;

import com.build4all.admin.domain.AdminUserProject;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.home.domain.HomeBanner;
import com.build4all.home.dto.HomeBannerRequest;
import com.build4all.home.dto.HomeBannerResponse;
import com.build4all.home.repository.HomeBannerRepository;
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
    
    public HomeBannerResponse createWithImage(Long adminId, HomeBannerRequest req, MultipartFile image) throws IOException {
        if (req.getOwnerProjectId() == null) {
            throw new IllegalArgumentException("ownerProjectId is required");
        }

        AdminUserProject app = requireOwnedProject(req.getOwnerProjectId(), adminId);

        String url = saveBannerImage(image);
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("image file is required");
        }

        HomeBanner b = new HomeBanner();
        b.setOwnerProject(app);
        b.setImageUrl(url);

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
    
    public HomeBannerResponse updateWithImage(Long adminId, Long id, HomeBannerRequest req, MultipartFile image) throws IOException {
        HomeBanner b = requireOwnedBanner(id, adminId);

        if (req.getTitle() != null) b.setTitle(req.getTitle());
        if (req.getSubtitle() != null) b.setSubtitle(req.getSubtitle());
        if (req.getTargetType() != null) b.setTargetType(req.getTargetType());
        if (req.getTargetId() != null) b.setTargetId(req.getTargetId());
        if (req.getTargetUrl() != null) b.setTargetUrl(req.getTargetUrl());
        if (req.getSortOrder() != null) b.setSortOrder(req.getSortOrder());
        if (req.getActive() != null) b.setActive(req.getActive());
        if (req.getStartAt() != null) b.setStartAt(req.getStartAt());
        if (req.getEndAt() != null) b.setEndAt(req.getEndAt());

        // ✅ image rules:
        // 1) if image provided => replace local image
        // 2) else if req.imageUrl provided => allow manual override (optional)
        if (image != null && !image.isEmpty()) {
            deleteLocalImageIfManaged(b.getImageUrl());
            b.setImageUrl(saveBannerImage(image));
        } else if (req.getImageUrl() != null) {
            b.setImageUrl(req.getImageUrl());
        }

        b = bannerRepo.save(b);
        return toResponse(b);
    }


    public void delete(Long adminId, Long id) {
        HomeBanner b = requireOwnedBanner(id, adminId);
        bannerRepo.delete(b);
    }

    // -------- helpers --------
    
    private String saveBannerImage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) return null;

        String original = file.getOriginalFilename() == null ? "banner" : file.getOriginalFilename();
        String filename = UUID.randomUUID() + "_" + original.replaceAll("\\s+", "_");

        Path baseDir = Paths.get("uploads", "home-banners");
        if (!Files.exists(baseDir)) Files.createDirectories(baseDir);

        Files.copy(file.getInputStream(), baseDir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);

        return "/uploads/home-banners/" + filename;
    }

    private void deleteLocalImageIfManaged(String url) {
        if (url == null || url.isBlank()) return;
        if (!url.startsWith("/uploads/")) return;

        try {
            String fileName = url.substring("/uploads/".length()).replace("\\", "/");
            if (fileName.isBlank()) return;

            Path uploads = Paths.get("uploads");
            Path filePath = uploads.resolve(fileName).normalize();

            Files.deleteIfExists(filePath);
        } catch (Exception ignored) {
            // don't fail request
        }
    }


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