package com.build4all.home.banner.service;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.home.banner.domain.HomeBanner;
import com.build4all.home.banner.dto.HomeBannerRequest;
import com.build4all.home.banner.dto.HomeBannerResponse;
import com.build4all.home.banner.repository.HomeBannerRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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

    @Transactional(Transactional.TxType.SUPPORTS)
    public List<HomeBannerResponse> listActivePublic(Long ownerProjectId) {
        LocalDateTime now = LocalDateTime.now();
        return bannerRepo.findActiveBannersForOwnerProject(ownerProjectId, now)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public List<HomeBannerResponse> listByOwnerProjectForAdmin(Long ownerProjectId, Long adminId) {
        AdminUserProject app = requireOwnedProject(ownerProjectId, adminId);
        return bannerRepo.findByOwnerProject_IdOrderBySortOrderAscCreatedAtDesc(app.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public HomeBannerResponse create(Long adminId, HomeBannerRequest req) {
        validateForCreate(req, null);

        AdminUserProject app = requireOwnedProject(req.getOwnerProjectId(), adminId);

        HomeBanner b = new HomeBanner();
        b.setOwnerProject(app);
        b.setImageUrl(req.getImageUrl());
        b.setTitle(req.getTitle());
        b.setSubtitle(req.getSubtitle());
        b.setTargetType(req.getTargetType());
        b.setTargetId(req.getTargetId());
        b.setTargetUrl(req.getTargetUrl());
        b.setSortOrder(req.getSortOrder());
        b.setActive(req.getActive() == null || req.getActive());
        b.setStartAt(req.getStartAt());
        b.setEndAt(req.getEndAt());

        b = bannerRepo.save(b);
        return toResponse(b);
    }

    public HomeBannerResponse createWithImage(Long adminId, HomeBannerRequest req, MultipartFile image) throws IOException {
        validateForCreate(req, image);

        AdminUserProject app = requireOwnedProject(req.getOwnerProjectId(), adminId);

        String url = saveBannerImage(image);

        HomeBanner b = new HomeBanner();
        b.setOwnerProject(app);
        b.setImageUrl(url);
        b.setTitle(req.getTitle());
        b.setSubtitle(req.getSubtitle());
        b.setTargetType(req.getTargetType());
        b.setTargetId(req.getTargetId());
        b.setTargetUrl(req.getTargetUrl());
        b.setSortOrder(req.getSortOrder());
        b.setActive(req.getActive() == null || req.getActive());
        b.setStartAt(req.getStartAt());
        b.setEndAt(req.getEndAt());

        b = bannerRepo.save(b);
        return toResponse(b);
    }

    public HomeBannerResponse update(Long adminId, Long id, HomeBannerRequest req) {
        validateForUpdate(req);

        HomeBanner b = requireOwnedBanner(id, adminId);

        b.setTitle(req.getTitle());
        b.setSubtitle(req.getSubtitle());
        b.setTargetType(req.getTargetType());
        b.setTargetId(req.getTargetId());
        b.setTargetUrl(req.getTargetUrl());
        b.setSortOrder(req.getSortOrder());
        b.setActive(req.getActive() == null || req.getActive());
        b.setStartAt(req.getStartAt());
        b.setEndAt(req.getEndAt());

        if (req.getImageUrl() != null && !req.getImageUrl().isBlank()) {
            b.setImageUrl(req.getImageUrl().trim());
        }

        b = bannerRepo.save(b);
        return toResponse(b);
    }

    public HomeBannerResponse updateWithImage(Long adminId, Long id, HomeBannerRequest req, MultipartFile image) throws IOException {
        validateForUpdate(req);

        HomeBanner b = requireOwnedBanner(id, adminId);

        b.setTitle(req.getTitle());
        b.setSubtitle(req.getSubtitle());
        b.setTargetType(req.getTargetType());
        b.setTargetId(req.getTargetId());
        b.setTargetUrl(req.getTargetUrl());
        b.setSortOrder(req.getSortOrder());
        b.setActive(req.getActive() == null || req.getActive());
        b.setStartAt(req.getStartAt());
        b.setEndAt(req.getEndAt());

        if (image != null && !image.isEmpty()) {
            deleteLocalImageIfManaged(b.getImageUrl());
            b.setImageUrl(saveBannerImage(image));
        } else if (req.getImageUrl() != null && !req.getImageUrl().isBlank()) {
            b.setImageUrl(req.getImageUrl().trim());
        }

        b = bannerRepo.save(b);
        return toResponse(b);
    }

    public void delete(Long adminId, Long id) {
        HomeBanner b = requireOwnedBanner(id, adminId);
        deleteLocalImageIfManaged(b.getImageUrl());
        bannerRepo.delete(b);
    }

    private void validateForCreate(HomeBannerRequest req, MultipartFile image) {
        if (req.getOwnerProjectId() == null) {
            throw new IllegalArgumentException("ownerProjectId is required");
        }

        validateCommonFields(req);

        if ((req.getImageUrl() == null || req.getImageUrl().isBlank()) &&
                (image == null || image.isEmpty())) {
            throw new IllegalArgumentException("image is required");
        }
    }

    private void validateForUpdate(HomeBannerRequest req) {
        validateCommonFields(req);
    }

    private void validateCommonFields(HomeBannerRequest req) {
        req.setTitle(trimToNull(req.getTitle()));
        req.setSubtitle(trimToNull(req.getSubtitle())); // optional
        req.setTargetUrl(trimToNull(req.getTargetUrl()));
        req.setTargetType(normalizeTargetType(req.getTargetType()));

        if (req.getTitle() == null) {
            throw new IllegalArgumentException("title is required");
        }

        if (req.getSortOrder() == null) {
            throw new IllegalArgumentException("sortOrder is required");
        }

        if (req.getSortOrder() < 0) {
            throw new IllegalArgumentException("sortOrder must be 0 or more");
        }

        if (req.getStartAt() != null && req.getEndAt() != null
                && req.getEndAt().isBefore(req.getStartAt())) {
            throw new IllegalArgumentException("endAt must be after startAt");
        }

        // target صار optional
        if (req.getTargetType() == null) {
            req.setTargetId(null);
            req.setTargetUrl(null);
            return;
        }

        switch (req.getTargetType()) {
            case "URL" -> {
                if (req.getTargetUrl() == null || req.getTargetUrl().isBlank()) {
                    throw new IllegalArgumentException("targetUrl is required when targetType is URL");
                }
                req.setTargetId(null);
            }
            case "CATEGORY", "PRODUCT" -> {
                if (req.getTargetId() == null) {
                    throw new IllegalArgumentException(
                            "targetId is required when targetType is " + req.getTargetType()
                    );
                }
                req.setTargetUrl(null);
            }
            default -> throw new IllegalArgumentException("Invalid targetType");
        }
    }

    private String normalizeTargetType(String targetType) {
        String t = trimToNull(targetType);
        if (t == null) return null;
        t = t.toUpperCase();
        if ("NONE".equals(t)) return null;
        return t;
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String saveBannerImage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("image is required");
        }

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