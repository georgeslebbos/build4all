package com.build4all.publish.service;

import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.admin.repository.AdminUsersRepository;
import com.build4all.common.errors.ApiException;
import com.build4all.notifications.service.NotificationsService;
import com.build4all.publish.domain.AppPublishRequest;
import com.build4all.publish.domain.PricingType;
import com.build4all.publish.domain.PublishPlatform;
import com.build4all.publish.domain.PublishStatus;
import com.build4all.publish.domain.PublishStore;
import com.build4all.publish.domain.StorePublisherProfile;
import com.build4all.publish.dto.AppPublishAdminMapper;
import com.build4all.publish.dto.AppPublishRequestAdminDto;
import com.build4all.publish.dto.PublishDraftUpdateDto;
import com.build4all.publish.repository.AppPublishRequestRepository;
import com.build4all.publish.repository.StorePublisherProfileRepository;
import com.build4all.storage.FileStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class AppPublishService {

    private final AppPublishRequestRepository publishRepo;
    private final StorePublisherProfileRepository profileRepo;
    private final AdminUserProjectRepository aupRepo;
    private final AdminUsersRepository adminRepo;
    private final FileStorageService storage;
    private final NotificationsService notificationsService;
    private final ObjectMapper mapper = new ObjectMapper();

    public AppPublishService(
            AppPublishRequestRepository publishRepo,
            StorePublisherProfileRepository profileRepo,
            AdminUserProjectRepository aupRepo,
            AdminUsersRepository adminRepo,
            FileStorageService storage,
            NotificationsService notificationsService
    ) {
        this.publishRepo = publishRepo;
        this.profileRepo = profileRepo;
        this.aupRepo = aupRepo;
        this.adminRepo = adminRepo;
        this.storage = storage;
        this.notificationsService = notificationsService;
    }

    // =========================
    // OWNER: get or create DRAFT
    // =========================
    @Transactional
    public AppPublishRequest getOrCreateDraft(
            Long aupId,
            PublishPlatform platform,
            PublishStore store,
            Long ownerAdminId
    ) {
        AdminUserProject aup = aupRepo.findByIdAndAdmin_AdminId(aupId, ownerAdminId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "APP_NOT_FOUND",
                        "App not found"
                ));

        AdminUser owner = adminRepo.findByAdminId(ownerAdminId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "OWNER_NOT_FOUND",
                        "Owner admin not found"
                ));

        var existing = publishRepo.findFirstByAdminUserProjectAndPlatformAndStoreAndStatus(
                aup, platform, store, PublishStatus.DRAFT
        );

        if (existing.isPresent()) {
            AppPublishRequest req = existing.get();

            Long aupOwnerId = extractAupOwnerId(req);
            if (aupOwnerId == null || !aupOwnerId.equals(ownerAdminId)) {
                throw new ApiException(
                        HttpStatus.NOT_FOUND,
                        "DRAFT_NOT_FOUND",
                        "Draft not found"
                );
            }

            if (platform == PublishPlatform.ANDROID) {
                if (isBlank(req.getPackageNameSnapshot()) && !isBlank(aup.getAndroidPackageName())) {
                    req.setPackageNameSnapshot(aup.getAndroidPackageName().trim());
                }
            } else if (platform == PublishPlatform.IOS) {
                if (isBlank(req.getBundleIdSnapshot()) && !isBlank(aup.getIosBundleId())) {
                    req.setBundleIdSnapshot(aup.getIosBundleId().trim());
                }
            }

            if (isBlank(req.getApplicationName()) && !isBlank(aup.getAppName())) {
                req.setApplicationName(aup.getAppName().trim());
            }

            if (req.getRequestedBy() == null) {
                req.setRequestedBy(owner);
            }

            if (isBlank(req.getShortDescription())) req.setShortDescription(" ");
            if (isBlank(req.getFullDescription())) req.setFullDescription(" ");
            if (isBlank(req.getCategory())) req.setCategory(" ");
            if (isBlank(req.getScreenshotsUrlsJson())) req.setScreenshotsUrlsJson("[]");

            return publishRepo.save(req);
        }

        StorePublisherProfile profile = profileRepo.findByStore(store)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "PUBLISHER_PROFILE_MISSING",
                        "Publisher profile missing for " + store
                ));

        AppPublishRequest req = new AppPublishRequest();
        req.setAdminUserProject(aup);
        req.setPublisherProfile(profile);
        req.setPlatform(platform);
        req.setStore(store);
        req.setStatus(PublishStatus.DRAFT);
        req.setRequestedBy(owner);

        req.setApplicationName(nullSafe(aup.getAppName()));

        if (platform == PublishPlatform.ANDROID) {
            req.setPackageNameSnapshot(nullSafe(aup.getAndroidPackageName()));
        } else {
            req.setBundleIdSnapshot(nullSafe(aup.getIosBundleId()));
        }

        req.setShortDescription(" ");
        req.setFullDescription(" ");
        req.setCategory(" ");
        req.setScreenshotsUrlsJson("[]");

        return publishRepo.save(req);
    }
    // =========================
    // OWNER: PATCH draft
    // =========================
    @Transactional
    public AppPublishRequest patchDraft(Long requestId, PublishDraftUpdateDto dto, Long ownerAdminId) {
        AppPublishRequest req = mustBeOwnerDraft(requestId, ownerAdminId);

        if (dto.getApplicationName() != null) {
            req.setApplicationName(dto.getApplicationName().trim());
        }
        if (dto.getShortDescription() != null) {
            req.setShortDescription(dto.getShortDescription().trim());
        }
        if (dto.getFullDescription() != null) {
            req.setFullDescription(dto.getFullDescription().trim());
        }

        if (dto.getCategory() != null) {
            req.setCategory(dto.getCategory().trim());
        }
        if (dto.getCountryAvailability() != null) {
            req.setCountryAvailability(dto.getCountryAvailability());
        }

        if (dto.getPricing() != null) {
            req.setPricing(PricingType.valueOf(dto.getPricing().trim().toUpperCase()));
        }

        if (dto.getContentRatingConfirmed() != null) {
            req.setContentRatingConfirmed(dto.getContentRatingConfirmed());
        }

        if (dto.getAppIconUrl() != null) {
            req.setAppIconUrl(dto.getAppIconUrl());
        }

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
    // OWNER: upload assets
    // =========================
    @Transactional
    public AppPublishRequest uploadAssets(
            Long requestId,
            MultipartFile appIcon,
            MultipartFile[] screenshots,
            Long ownerAdminId
    ) {
        AppPublishRequest req = mustBeOwnerDraft(requestId, ownerAdminId);

        if (appIcon != null && !appIcon.isEmpty()) {
            validateImage(appIcon, "appIcon");
            String url = storage.save(appIcon, "publish/" + requestId + "/icon");
            req.setAppIconUrl(url);
        }

        if (screenshots != null && screenshots.length > 0) {
            List<String> urls = new ArrayList<>();

            for (MultipartFile f : screenshots) {
                if (f == null || f.isEmpty()) {
                    continue;
                }
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

    // =========================
    // OWNER: submit for review
    // =========================
    @Transactional
    public AppPublishRequest submitForReview(Long requestId, Long ownerAdminId) {
        AppPublishRequest req = mustBeOwnerDraft(requestId, ownerAdminId);

        AdminUserProject aup = req.getAdminUserProject();
        if (aup == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "AUP_MISSING",
                    "AdminUserProject missing"
            );
        }

        if (req.getPlatform() == PublishPlatform.ANDROID) {
            if (isBlank(req.getPackageNameSnapshot()) && !isBlank(aup.getAndroidPackageName())) {
                req.setPackageNameSnapshot(aup.getAndroidPackageName().trim());
            }
        } else if (req.getPlatform() == PublishPlatform.IOS) {
            if (isBlank(req.getBundleIdSnapshot()) && !isBlank(aup.getIosBundleId())) {
                req.setBundleIdSnapshot(aup.getIosBundleId().trim());
            }
        }

        if (isBlank(req.getApplicationName()) && !isBlank(aup.getAppName())) {
            req.setApplicationName(aup.getAppName().trim());
        }

        if (isBlank(req.getApplicationName())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "APPLICATION_NAME_REQUIRED", "Application name is required");
        }

        if (req.getPlatform() == PublishPlatform.ANDROID && isBlank(req.getPackageNameSnapshot())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PACKAGE_NAME_MISSING", "Package name missing");
        }

        if (req.getPlatform() == PublishPlatform.IOS && isBlank(req.getBundleIdSnapshot())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BUNDLE_ID_MISSING", "Bundle ID missing");
        }

        if (isBlank(req.getShortDescription())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SHORT_DESCRIPTION_REQUIRED", "Short description is required");
        }

        if (req.getShortDescription().trim().length() > 80) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SHORT_DESCRIPTION_TOO_LONG", "Short description max 80 chars");
        }

        if (isBlank(req.getFullDescription())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FULL_DESCRIPTION_REQUIRED", "Full description is required");
        }

        if (isBlank(req.getCategory())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_REQUIRED", "Category is required");
        }

        if (!req.isContentRatingConfirmed()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_RATING_REQUIRED", "Content rating confirmation is required");
        }

        if (isBlank(req.getAppIconUrl())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "APP_ICON_REQUIRED", "App icon is required");
        }

        List<String> shots = parseScreenshots(req.getScreenshotsUrlsJson());
        if (shots.size() < 2 || shots.size() > 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SCREENSHOTS_COUNT", "Screenshots must be 2..8");
        }

        req.setStatus(PublishStatus.SUBMITTED);
        req.setRequestedAt(LocalDateTime.now());

        AppPublishRequest saved = publishRepo.save(req);

        List<AdminUser> superAdmins = adminRepo.findByRole_NameIgnoreCase("SUPER_ADMIN");

        String appName = saved.getAdminUserProject() != null
                && saved.getAdminUserProject().getAppName() != null
                ? saved.getAdminUserProject().getAppName()
                : saved.getApplicationName();

        String message = "New publish request submitted for " + appName
                + " (" + saved.getPlatform() + " / " + saved.getStore() + ").";

        for (AdminUser superAdmin : superAdmins) {
            notificationsService.notifyAdmin(
                    superAdmin,
                    message,
                    "OWNER_PUBLISH_REQUEST_SUBMITTED"
            );
        }

        return saved;
    }

    // =========================
    // SUPER ADMIN: approve
    // =========================
    @Transactional
    public AppPublishRequest approve(Long requestId, Long superAdminId, String notes) {
        AppPublishRequest req = publishRepo.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Publish request not found"));

        if (req.getStatus() != PublishStatus.SUBMITTED) {
            throw new RuntimeException("Only SUBMITTED requests can be approved");
        }

        AdminUser admin = adminRepo.findByAdminId(superAdminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        req.setStatus(PublishStatus.APPROVED);
        req.setReviewedBy(admin);
        req.setReviewedAt(LocalDateTime.now());
        req.setAdminNotes(notes);

        AdminUserProject aup = req.getAdminUserProject();
        if (aup == null || aup.getId() == null) {
            throw new RuntimeException("AdminUserProject missing on publish request");
        }

        String current = aup.getStatus() == null ? "" : aup.getStatus().trim().toUpperCase();
        if (!"PRODUCTION".equals(current)) {
            aup.setStatus("PRODUCTION");
            aupRepo.save(aup);
        }

        AppPublishRequest saved = publishRepo.save(req);

        AdminUser owner = saved.getRequestedBy();
        String appName = saved.getAdminUserProject() != null
                && saved.getAdminUserProject().getAppName() != null
                ? saved.getAdminUserProject().getAppName()
                : saved.getApplicationName();

        String message = "Your publish request for " + appName
                + " (" + saved.getPlatform() + " / " + saved.getStore() + ") has been approved.";

        if (notes != null && !notes.isBlank()) {
            message += " Notes: " + notes;
        }

        notificationsService.notifyAdmin(
                owner,
                message,
                "SUPER_ADMIN_PUBLISH_REQUEST_APPROVED"
        );

        return saved;
    }

    // =========================
    // SUPER ADMIN: reject
    // =========================
    @Transactional
    public AppPublishRequest reject(Long requestId, Long superAdminId, String notes) {
        AppPublishRequest req = publishRepo.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Publish request not found"));

        if (req.getStatus() != PublishStatus.SUBMITTED) {
            throw new RuntimeException("Only SUBMITTED requests can be rejected");
        }

        AdminUser admin = adminRepo.findByAdminId(superAdminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        req.setStatus(PublishStatus.REJECTED);
        req.setReviewedBy(admin);
        req.setReviewedAt(LocalDateTime.now());
        req.setAdminNotes(notes);

        AppPublishRequest saved = publishRepo.save(req);

        AdminUser owner = saved.getRequestedBy();
        String appName = saved.getAdminUserProject() != null
                && saved.getAdminUserProject().getAppName() != null
                ? saved.getAdminUserProject().getAppName()
                : saved.getApplicationName();

        String message = "Your publish request for " + appName
                + " (" + saved.getPlatform() + " / " + saved.getStore() + ") has been rejected.";

        if (notes != null && !notes.isBlank()) {
            message += " Notes: " + notes;
        }

        notificationsService.notifyAdmin(
                owner,
                message,
                "SUPER_ADMIN_PUBLISH_REQUEST_REJECTED"
        );

        return saved;
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
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "PUBLISH_REQUEST_NOT_FOUND",
                        "Publish request not found"
                ));

        if (req.getStatus() != PublishStatus.DRAFT) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "NOT_IN_DRAFT",
                    "Request is not in DRAFT status"
            );
        }

        Long aupOwnerId = extractAupOwnerId(req);
        if (aupOwnerId == null || !aupOwnerId.equals(ownerAdminId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "NOT_ALLOWED",
                    "You are not allowed to access this draft"
            );
        }

        Long requestedById = req.getRequestedBy() != null ? req.getRequestedBy().getAdminId() : null;
        if (requestedById != null && !requestedById.equals(ownerAdminId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "NOT_ALLOWED",
                    "You are not allowed to access this draft"
            );
        }

        return req;
    }
    private Long extractAupOwnerId(AppPublishRequest req) {
        if (req == null || req.getAdminUserProject() == null || req.getAdminUserProject().getAdmin() == null) {
            return null;
        }
        return req.getAdminUserProject().getAdmin().getAdminId();
    }

    private void validateImage(MultipartFile file, String field) {
        String ct = file.getContentType();
        if (ct == null || !ct.startsWith("image/")) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_IMAGE_TYPE",
                    field + " must be an image"
            );
        }

        long max = 5L * 1024 * 1024;
        if (file.getSize() > max) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "IMAGE_TOO_LARGE",
                    field + " is too large (max 5MB)"
            );
        }
    }

    private List<String> parseScreenshots(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return mapper.readValue(
                    json,
                    mapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
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