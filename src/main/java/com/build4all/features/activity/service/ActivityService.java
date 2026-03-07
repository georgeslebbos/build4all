package com.build4all.features.activity.service;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.business.domain.Businesses;
import com.build4all.order.domain.OrderItem;
import com.build4all.catalog.domain.ItemStatus;
import com.build4all.catalog.domain.ItemType;
import com.build4all.features.activity.domain.Activity;
import com.build4all.business.repository.BusinessesRepository;
import com.build4all.order.repository.OrderItemRepository;
import com.build4all.catalog.repository.ItemStatusRepository;
import com.build4all.catalog.repository.ItemTypeRepository;
import com.build4all.features.activity.repository.ActivitiesRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class ActivityService {

    private static final String STATUS_DRAFT = "DRAFT";

    private final ActivitiesRepository activityRepository;
    private final ItemTypeRepository itemTypeRepository;
    private final BusinessesRepository businessesRepository;
    private final OrderItemRepository orderItemRepository;
    private final ItemStatusRepository itemStatusRepository;

    private final Path uploadRoot = Paths.get("uploads");

    public ActivityService(ActivitiesRepository activityRepository,
                           ItemTypeRepository itemTypeRepository,
                           BusinessesRepository businessesRepository,
                           OrderItemRepository orderItemRepository,
                           ItemStatusRepository itemStatusRepository) {
        this.activityRepository = activityRepository;
        this.itemTypeRepository = itemTypeRepository;
        this.businessesRepository = businessesRepository;
        this.orderItemRepository = orderItemRepository;
        this.itemStatusRepository = itemStatusRepository;

        try {
            if (!Files.exists(uploadRoot)) Files.createDirectories(uploadRoot);
        } catch (IOException ignored) {
        }
    }

    /* =========================================================
       STATUS HELPERS
       ========================================================= */

    private String normalizeStatusCode(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isBlank()) return null;
        return s.toUpperCase(Locale.ROOT);
    }

    private ItemStatus resolveStatusOrDefault(String rawStatusCode) {
        String normalized = normalizeStatusCode(rawStatusCode);
        String finalCode = (normalized == null) ? STATUS_DRAFT : normalized;

        return itemStatusRepository.findByCode(finalCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid statusCode: " + finalCode));
    }

    /* =========================================================
       CREATE
       ========================================================= */

    public Activity createActivityWithImage(String name,
                                            Long itemTypeId,
                                            String description,
                                            String location,
                                            Double latitude,
                                            Double longitude,
                                            int maxParticipants,
                                            BigDecimal price,
                                            LocalDateTime startDatetime,
                                            LocalDateTime endDatetime,
                                            String statusCode,
                                            Long businessId,
                                            MultipartFile image) throws IOException {

        ItemType type = itemTypeRepository.findById(itemTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid itemTypeId"));

        Businesses business = businessesRepository.findById(businessId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid businessId"));

        AdminUserProject aup = business.getOwnerProjectLink();
        if (aup == null) {
            throw new IllegalStateException("Business not linked to any Owner Project");
        }

        ItemStatus status = resolveStatusOrDefault(statusCode);

        Activity a = new Activity();
        a.setItemName(name);
        a.setItemType(type);
        a.setDescription(description);
        a.setPrice(price);
        a.setStatus(status);
        a.setBusiness(business);
        a.setOwnerProject(aup);

        a.setLocation(location);
        a.setLatitude(latitude);
        a.setLongitude(longitude);
        a.setMaxParticipants(maxParticipants);
        a.setStartDatetime(startDatetime);
        a.setEndDatetime(endDatetime);

        if (image != null && !image.isEmpty()) {
            a.setImageUrl(storeImage(image));
        }

        return activityRepository.save(a);
    }

    /* =========================================================
       UPDATE
       ========================================================= */

    public Activity updateActivityWithImage(Long id,
                                            String name,
                                            Long itemTypeId,
                                            String description,
                                            String location,
                                            Double latitude,
                                            Double longitude,
                                            int maxParticipants,
                                            BigDecimal price,
                                            LocalDateTime startDatetime,
                                            LocalDateTime endDatetime,
                                            String statusCode,
                                            Long businessId,
                                            MultipartFile image,
                                            boolean imageRemoved) throws IOException {

        Activity a = activityRepository.findByIdWithJoins(id)
                .orElseThrow(() -> new IllegalArgumentException("Activity not found"));

        ItemType type = itemTypeRepository.findById(itemTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid itemTypeId"));

        Businesses business = businessesRepository.findById(businessId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid businessId"));

        a.setItemName(name);
        a.setItemType(type);
        a.setDescription(description);
        a.setPrice(price);

        if (StringUtils.hasText(statusCode)) {
            a.setStatus(resolveStatusOrDefault(statusCode));
        }

        a.setBusiness(business);

        a.setLocation(location);
        a.setLatitude(latitude);
        a.setLongitude(longitude);
        a.setMaxParticipants(maxParticipants);
        a.setStartDatetime(startDatetime);
        a.setEndDatetime(endDatetime);

        if (imageRemoved) {
            a.setImageUrl(null);
        }

        if (image != null && !image.isEmpty()) {
            a.setImageUrl(storeImage(image));
        }

        return activityRepository.save(a);
    }

    /* =========================================================
       READS
       ========================================================= */

    public Activity findById(Long id) {
        return activityRepository.findByIdWithJoins(id).orElse(null);
    }

    public List<Activity> findAll() {
        return activityRepository.findAllWithItemTypeAndBusiness();
    }

    public Activity save(Activity a) {
        return activityRepository.save(a);
    }

    public void deleteActivity(Long id) {
        activityRepository.deleteById(id);
    }

    public List<Activity> findByBusinessId(Long businessId) {
        return activityRepository.findByBusinessIdWithJoins(businessId);
    }

    public List<Activity> findByItemTypeId(Long typeId) {
        return activityRepository.findByItemTypeIdWithJoins(typeId);
    }

    public boolean isAvailable(Long itemId, int requestedParticipants) {
        Activity a = activityRepository.findByIdWithJoins(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));

        List<String> active = Arrays.asList("Pending", "Completed", "Paid", "Confirmed");
        int alreadyBooked = orderItemRepository.sumQuantityByItemIdAndStatusNames(itemId, active);

        int capacityLeft = Math.max(0, a.getMaxParticipants() - alreadyBooked);

        return requestedParticipants <= capacityLeft;
    }

    public List<Activity> findItemsByUserCategories(Long userId) {
        return activityRepository.findUpcomingByUserCategoriesWithJoins(userId, LocalDateTime.now());
    }

    public OrderItem createCashorderByBusiness(Long itemId, Long businessUserId, int participants, boolean wasPaid) {
        throw new UnsupportedOperationException("Cash order flow is not implemented in ActivityService");
    }

    /* =========================================================
       STORAGE
       ========================================================= */

    private String storeImage(MultipartFile image) throws IOException {
        String original = org.springframework.util.StringUtils.cleanPath(image.getOriginalFilename());
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) ext = original.substring(dot);

        String fileName = UUID.randomUUID() + ext;
        Path dest = uploadRoot.resolve(fileName);
        Files.copy(image.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);

        return "/uploads/" + fileName;
    }

    /* =========================================================
       OWNER SCOPED LISTS
       ========================================================= */

    public List<Activity> findByOwnerProject(Long aupId) {
        return activityRepository.findByOwnerProject_Id(aupId);
    }

    public List<Activity> findByOwnerAndType(Long aupId, Long typeId) {
        return activityRepository.findByOwnerProject_IdAndItemType_Id(aupId, typeId);
    }

    public List<Activity> findAllByOwner(Long aupId) {
        return activityRepository.findAllByAupWithJoins(aupId);
    }
}