package com.build4all.publish.service;

import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.admin.repository.AdminUsersRepository;
import com.build4all.publish.domain.*;
import com.build4all.publish.dto.AppPublishAdminMapper;
import com.build4all.publish.dto.AppPublishRequestAdminDto;
import com.build4all.publish.dto.PublishDraftUpdateDto;
import com.build4all.publish.repository.AppPublishRequestRepository;
import com.build4all.publish.repository.StorePublisherProfileRepository;
import com.build4all.storage.FileStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AppPublishService {

    private final AppPublishRequestRepository publishRepo;
    private final StorePublisherProfileRepository profileRepo;
    private final AdminUserProjectRepository aupRepo;
    private final AdminUsersRepository adminRepo;
    private final FileStorageService storage;
    private final ObjectMapper mapper = new ObjectMapper();

    public AppPublishService(
            AppPublishRequestRepository publishRepo,
            StorePublisherProfileRepository profileRepo,
            AdminUserProjectRepository aupRepo,
            AdminUsersRepository adminRepo,
            FileStorageService storage
    ) {
        this.publishRepo = publishRepo;
        this.profileRepo = profileRepo;
        this.aupRepo = aupRepo;
        this.adminRepo = adminRepo;
        this.storage = storage;
    }
    // =========================
    // OWNER: get or create DRAFT
    // =========================
    @Transactional
    public AppPublishRequest getOrCreateDraft(Long aupId, PublishPlatform platform, PublishStore store, Long ownerAdminId) {

        AdminUserProject aup = aupRepo.findById(aupId)
                .orElseThrow(() -> new RuntimeException("AdminUserProject not found"));

        AdminUser owner = adminRepo.findByAdminId(ownerAdminId)
                .orElseThrow(() -> new RuntimeException("Owner admin not found"));


        // existing draft?
        var existing = publishRepo.findFirstByAdminUserProjectAndPlatformAndStoreAndStatus(
                aup, platform, store, PublishStatus.DRAFT
        );
        if (existing.isPresent()) return existing.get();

        // publisher profile must exist (super admin creates it once)
        StorePublisherProfile profile = profileRepo.findByStore(store)
                .orElseThrow(() -> new RuntimeException("Publisher profile missing for " + store));

        AppPublishRequest req = new AppPublishRequest();
        req.setAdminUserProject(aup);
        req.setPublisherProfile(profile);

        req.setPlatform(platform);
        req.setStore(store);
        req.setStatus(PublishStatus.DRAFT);

        req.setRequestedBy(owner);

        // defaults from AUP
        req.setApplicationName(nullSafe(aup.getAppName()));

        if (platform == PublishPlatform.ANDROID) {
            req.setPackageNameSnapshot(nullSafe(aup.getAndroidPackageName())); // read-only
        } else {
            req.setBundleIdSnapshot(nullSafe(aup.getIosBundleId())); // read-only
        }

        // satisfy NOT NULL cols early (so draft can be created)
        req.setShortDescription(" ");
        req.setFullDescription(" ");
        req.setCategory(" ");
        req.setScreenshotsUrlsJson("[]");

        return publishRepo.save(req);
    }

    // =========================
    // OWNER: PATCH draft (single DTO)
    // =========================
    @Transactional
    public AppPublishRequest patchDraft(Long requestId, PublishDraftUpdateDto dto, Long ownerAdminId) {
        AppPublishRequest req = mustBeOwnerDraft(requestId, ownerAdminId);

        // Step 1
        if (dto.getApplicationName() != null) req.setApplicationName(dto.getApplicationName().trim());
        if (dto.getShortDescription() != null) req.setShortDescription(dto.getShortDescription().trim());
        if (dto.getFullDescription() != null) req.setFullDescription(dto.getFullDescription().trim());

        // Step 2
        if (dto.getCategory() != null) req.setCategory(dto.getCategory().trim());
        if (dto.getCountryAvailability() != null) req.setCountryAvailability(dto.getCountryAvailability());

        if (dto.getPricing() != null) {
            req.setPricing(PricingType.valueOf(dto.getPricing().trim().toUpperCase()));
        }
        if (dto.getContentRatingConfirmed() != null) {
            req.setContentRatingConfirmed(dto.getContentRatingConfirmed());
        }

        // Step 4
        if (dto.getAppIconUrl() != null) req.setAppIconUrl(dto.getAppIconUrl());

        if (dto.getScreenshotsUrls() != null) {
            if (dto.getScreenshotsUrls().size() < 2 || dto.getScreenshotsUrls().size() > 8) {
                throw new RuntimeException("Screenshots must be between 2 and 8");
            }
            try {
                req.setScreenshotsUrlsJson(mapper.writeValueAsString(dto.getScreenshotsUrls()));
            } catch (Exception e) {
                throw new RuntimeException("Invalid screenshots list", e);
            }
        }

        return publishRepo.save(req);
    }

    // =========================
    // OWNER: submit for review (strict validation)
    // =========================
    @Transactional
    public AppPublishRequest submitForReview(Long requestId, Long ownerAdminId) {
        AppPublishRequest req = mustBeOwnerDraft(requestId, ownerAdminId);

        // Required fields (matches your wizard)
        if (isBlank(req.getApplicationName())) throw new RuntimeException("Application name is required");

        if (req.getPlatform() == PublishPlatform.ANDROID && isBlank(req.getPackageNameSnapshot()))
            throw new RuntimeException("Package name missing");

        if (req.getPlatform() == PublishPlatform.IOS && isBlank(req.getBundleIdSnapshot()))
            throw new RuntimeException("Bundle ID missing");

        if (isBlank(req.getShortDescription())) throw new RuntimeException("Short description is required");
        if (req.getShortDescription().trim().length() > 80) throw new RuntimeException("Short description max 80 chars");

        if (isBlank(req.getFullDescription())) throw new RuntimeException("Full description is required");
        if (isBlank(req.getCategory())) throw new RuntimeException("Category is required");

        if (!req.isContentRatingConfirmed()) throw new RuntimeException("Content rating confirmation is required");

        if (isBlank(req.getAppIconUrl())) throw new RuntimeException("App icon is required");

        List<String> shots = parseScreenshots(req.getScreenshotsUrlsJson());
        if (shots.size() < 2 || shots.size() > 8) throw new RuntimeException("Screenshots must be 2..8");

        req.setStatus(PublishStatus.SUBMITTED);
        req.setRequestedAt(LocalDateTime.now());
        return publishRepo.save(req);
    }

    // =========================
    // SUPER ADMIN: approve/reject
    // =========================
    @Transactional
    public AppPublishRequest approve(Long requestId, Long superAdminId, String notes) {
        AppPublishRequest req = publishRepo.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Publish request not found"));

        if (req.getStatus() != PublishStatus.SUBMITTED)
            throw new RuntimeException("Only SUBMITTED requests can be approved");

        AdminUser admin = adminRepo.findByAdminId(superAdminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        // ✅ 1) approve request
        req.setStatus(PublishStatus.APPROVED);
        req.setReviewedBy(admin);
        req.setReviewedAt(LocalDateTime.now());
        req.setAdminNotes(notes);

        // ✅ 2) flip app status to PRODUCTION
        AdminUserProject aup = req.getAdminUserProject();
        if (aup == null || aup.getId() == null) {
            throw new RuntimeException("AdminUserProject missing on publish request");
        }

        // optional: only flip if currently TEST
        String current = (aup.getStatus() == null) ? "" : aup.getStatus().trim().toUpperCase();
        if (!"PRODUCTION".equals(current)) {
            aup.setStatus("PRODUCTION");
            aupRepo.save(aup);
        }

        return publishRepo.save(req);
    }


    @Transactional
    public AppPublishRequest uploadAssets(
            Long requestId,
            MultipartFile appIcon,
            MultipartFile[] screenshots,
            Long ownerAdminId
    ) {
        AppPublishRequest req = mustBeOwnerDraft(requestId, ownerAdminId);

        // icon
        if (appIcon != null && !appIcon.isEmpty()) {
            validateImage(appIcon, "appIcon");
            String url = storage.save(appIcon, "publish/" + requestId + "/icon");
            req.setAppIconUrl(url);
        }

        // screenshots
        if (screenshots != null && screenshots.length > 0) {
            // allow client to send many, but we only accept 2..8 valid files
            List<String> urls = new ArrayList<>();

            for (MultipartFile f : screenshots) {
                if (f == null || f.isEmpty()) continue;
                validateImage(f, "screenshots");
                String url = storage.save(f, "publish/" + requestId + "/screenshots");
                urls.add(url);
            }

            if (urls.size() < 2 || urls.size() > 8) {
                throw new RuntimeException("Screenshots must be between 2 and 8");
            }

            try {
                req.setScreenshotsUrlsJson(mapper.writeValueAsString(urls));
            } catch (Exception e) {
                throw new RuntimeException("Invalid screenshots list", e);
            }
        }

        return publishRepo.save(req);
    }

    private void validateImage(MultipartFile file, String field) {
        String ct = file.getContentType();
        if (ct == null || !(ct.startsWith("image/"))) {
            throw new RuntimeException(field + " must be an image");
        }
        // optional: size limit (example: 5MB)
        long max = 5L * 1024 * 1024;
        if (file.getSize() > max) {
            throw new RuntimeException(field + " is too large (max 5MB)");
        }
    }

    @Transactional
    public AppPublishRequest reject(Long requestId, Long superAdminId, String notes) {
        AppPublishRequest req = publishRepo.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Publish request not found"));

        if (req.getStatus() != PublishStatus.SUBMITTED)
            throw new RuntimeException("Only SUBMITTED requests can be rejected");

        AdminUser admin = adminRepo.findById(superAdminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        req.setStatus(PublishStatus.REJECTED);
        req.setReviewedBy(admin);
        req.setReviewedAt(LocalDateTime.now());
        req.setAdminNotes(notes);

        return publishRepo.save(req);
    }

    // =========================
    // SUPER ADMIN: list by status
    // =========================
    @Transactional(readOnly = true)
    public List<AppPublishRequestAdminDto> listByStatusForAdmin(PublishStatus status) {
    	return publishRepo.findByStatusForAdminWithJoins(status)
    	        .stream()
    	        .map(AppPublishAdminMapper::toDto)
    	        .toList();

    }

    // =========================
    // Helpers
    // =========================
    private AppPublishRequest mustBeOwnerDraft(Long requestId, Long ownerAdminId) {
        AppPublishRequest req = publishRepo.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Publish request not found"));

        if (req.getStatus() != PublishStatus.DRAFT)
            throw new RuntimeException("Not in DRAFT");

        if (req.getRequestedBy() == null || req.getRequestedBy().getAdminId() == null ||
                !req.getRequestedBy().getAdminId().equals(ownerAdminId)) {
            throw new RuntimeException("Not allowed");
        }

        return req;
    }

    private List<String> parseScreenshots(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return mapper.readValue(json, List.class);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
