package com.build4all.features.activity.web;

import com.build4all.features.activity.dto.ActivityDetailsDTO;
import com.build4all.business.domain.BusinessUser;
import com.build4all.business.domain.Businesses;
import com.build4all.order.domain.OrderItem;
import com.build4all.user.domain.Users;
import com.build4all.features.activity.domain.Activity;
import com.build4all.security.JwtUtil;
import com.build4all.business.service.BusinessUserService;
import com.build4all.order.service.OrderService;
import com.build4all.payment.service.StripeService;
import com.build4all.user.service.UserService;
import com.build4all.features.activity.service.ActivityService;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping({"/api/activities", "/api/items"})
public class ActivityController {

    private final ActivityService activityService;
    private final OrderService orderService;
    private final UserService userService;
    private final BusinessUserService businessUserService;
    private final StripeService stripeService;
    private final JwtUtil jwtUtil;

    public ActivityController(ActivityService activityService,
                              OrderService orderService,
                              UserService userService,
                              BusinessUserService businessUserService,
                              StripeService stripeService,
                              JwtUtil jwtUtil) {
        this.activityService = activityService;
        this.orderService = orderService;
        this.userService = userService;
        this.businessUserService = businessUserService;
        this.stripeService = stripeService;
        this.jwtUtil = jwtUtil;
    }

    /* ------------------------ helpers ------------------------ */

    private String strip(String auth) { return auth == null ? "" : auth.replace("Bearer ", "").trim(); }

    private boolean hasRole(String token, String... roles) {
        String role = jwtUtil.extractRole(token);
        for (String r : roles) if (r.equalsIgnoreCase(role)) return true;
        return false;
    }

    /** App-scoped user resolver (email or phone from JWT) using a single ownerProjectLinkId. */
    private Users getScopedUserFromToken(String authHeader, Long ownerProjectLinkId) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }
        String jwt = authHeader.substring(7).trim();
        String identity = jwtUtil.extractUsername(jwt); // may be email or phone
        // NOTE: service must provide owner-link overloads
        return userService.getUserByEmaill(identity, ownerProjectLinkId);
    }

    private ActivityDetailsDTO toDto(Activity a) {
        return new ActivityDetailsDTO(
                a.getId(),
                a.getItemName(),
                a.getDescription(),
                (a.getItemType() != null ? a.getItemType().getId() : null),
                (a.getItemType() != null ? a.getItemType().getName() : null),
                a.getLocation(),
                a.getLatitude(),
                a.getLongitude(),
                a.getStartDatetime(),
                a.getEndDatetime(),
                a.getPrice(),
                a.getMaxParticipants(),
                a.getStatus(),
                a.getImageUrl(),
                (a.getBusiness() != null ? a.getBusiness().getBusinessName() : null)
        );
    }

    /* ------------------------ create ------------------------ */

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create activity (multipart)")
    public ResponseEntity<?> create(
            @RequestHeader("Authorization") String auth,
            @RequestParam("name") String name,
            @RequestParam("itemTypeId") Long itemTypeId,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude,
            @RequestParam("maxParticipants") int maxParticipants,
            @RequestParam("price") BigDecimal price,
            @RequestParam("startDatetime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDatetime,
            @RequestParam("endDatetime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDatetime,
            @RequestParam(value = "status", defaultValue = "Upcoming") String status,
            @RequestParam("businessId") Long businessId,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) throws Exception {

        String token = strip(auth);
        if (!hasRole(token, "BUSINESS", "MANAGER","OWNER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Business or Manager role required."));
        }

        Activity saved = activityService.createActivityWithImage(
                name, itemTypeId, description, location, latitude, longitude,
                maxParticipants, price, startDatetime, endDatetime, status, businessId, image
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Item created successfully",
                "item", toDto(saved)
        ));
    }

    /* ------------------------ update ------------------------ */

    @PutMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Update activity (multipart)")
    public ResponseEntity<?> update(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id,
            @RequestParam("name") String name,
            @RequestParam("itemTypeId") Long itemTypeId,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude,
            @RequestParam("maxParticipants") int maxParticipants,
            @RequestParam("price") BigDecimal price,
            @RequestParam("startDatetime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDatetime,
            @RequestParam("endDatetime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDatetime,
            @RequestParam(value = "status", defaultValue = "Upcoming") String status,
            @RequestParam("businessId") Long businessId,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "imageRemoved", defaultValue = "false") boolean imageRemoved
    ) throws Exception {

        String token = strip(auth);
        if (!hasRole(token, "BUSINESS", "MANAGER","OWNER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Business or Manager role required."));
        }

        Activity saved = activityService.updateActivityWithImage(
                id, name, itemTypeId, description, location, latitude, longitude,
                maxParticipants, price, startDatetime, endDatetime, status, businessId, image, imageRemoved
        );
        return ResponseEntity.ok(Map.of(
                "message", "Item updated successfully",
                "item", toDto(saved)
        ));
    }

    /* ------------------------ delete ------------------------ */

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an item by ID")
    public ResponseEntity<?> delete(@RequestHeader("Authorization") String auth,
                                    @PathVariable Long id) {
        String token = strip(auth);
        if (!hasRole(token, "BUSINESS","OWNER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Only Business users can delete items."));
        }

        Activity a = activityService.findById(id);
        if (a == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Item not found"));

        orderService.deleteordersByItemId(id);
        activityService.deleteActivity(id);
        return ResponseEntity.noContent().build();
    }

    /* ------------------------ get one ------------------------ */

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        Activity a = activityService.findById(id);
        if (a == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Item not found"));

        Businesses b = a.getBusiness();
        Map<String, Object> businessInfo = new HashMap<>();
        if (b != null) {
            businessInfo.put("id", b.getId());
            businessInfo.put("businessName", b.getBusinessName());
            businessInfo.put("email", b.getEmail());
            businessInfo.put("phoneNumber", b.getPhoneNumber());
            businessInfo.put("businessLogoUrl", b.getBusinessLogoUrl());
            businessInfo.put("businessBannerUrl", b.getBusinessBannerUrl());
            businessInfo.put("description", b.getDescription());
            businessInfo.put("websiteUrl", b.getWebsiteUrl());
            businessInfo.put("status", b.getStatus() != null ? b.getStatus().getName() : null);
            businessInfo.put("isPublicProfile", b.getIsPublicProfile());
            businessInfo.put("stripeAccountId", b.getStripeAccountId());
            businessInfo.put("createdAt", b.getCreatedAt());
            businessInfo.put("updatedAt", b.getUpdatedAt());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", a.getId());
        response.put("itemName", a.getItemName());
        response.put("description", a.getDescription());
        response.put("itemTypeId", a.getItemType() != null ? a.getItemType().getId() : null);
        response.put("itemTypeName", a.getItemType() != null ? a.getItemType().getName() : null);
        response.put("location", a.getLocation());
        response.put("latitude", a.getLatitude());
        response.put("longitude", a.getLongitude());
        response.put("startDatetime", a.getStartDatetime());
        response.put("endDatetime", a.getEndDatetime());
        response.put("price", a.getPrice());
        response.put("maxParticipants", a.getMaxParticipants());
        response.put("status", a.getStatus());
        response.put("imageUrl", a.getImageUrl());
        response.put("business", businessInfo);
        return ResponseEntity.ok(response);
    }

    /* ------------------------ lists (public) ------------------------ */

    @GetMapping
    public ResponseEntity<?> getAll() {
        List<Activity> list = activityService.findAll();
        LocalDateTime now = LocalDateTime.now();
        for (Activity a : list) {
            if (a.getEndDatetime() != null && a.getEndDatetime().isBefore(now)
                    && !"Terminated".equalsIgnoreCase(a.getStatus())) {
                a.setStatus("Terminated");
                activityService.save(a);
            }
        }
        return ResponseEntity.ok(list.stream().map(this::toDto).toList());
    }

    @GetMapping("/upcoming")
    public ResponseEntity<?> getUpcoming(@RequestParam Long ownerProjectLinkId) {
        LocalDateTime now = LocalDateTime.now();

        List<Activity> upcoming = activityService.findByOwnerProject(ownerProjectLinkId).stream()
                .filter(a -> a.getEndDatetime().isAfter(now))
                .filter(a -> !"Terminated".equalsIgnoreCase(a.getStatus()))
                .toList();

        return ResponseEntity.ok(upcoming.stream().map(this::toDto).toList());
    }


    @GetMapping("/terminated")
    public ResponseEntity<?> getTerminated(@RequestHeader("Authorization") String auth) {
        if (auth == null || !auth.startsWith("Bearer "))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Missing token"));

        String token = auth.substring(7).trim();
        String role = jwtUtil.extractRole(token);
        if (!List.of("USER", "BUSINESS", "MANAGER", "SUPER_ADMIN").contains(role))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));

        LocalDateTime now = LocalDateTime.now();
        List<Activity> terminated = activityService.findAll().stream()
                .filter(a -> a.getEndDatetime() != null && a.getEndDatetime().isBefore(now))
                .peek(a -> {
                    if (!"Terminated".equalsIgnoreCase(a.getStatus())) {
                        a.setStatus("Terminated");
                        activityService.save(a);
                    }
                })
                .toList();
        return ResponseEntity.ok(terminated);
    }

    @GetMapping("/business/{businessId}")
    public ResponseEntity<?> getByBusiness(@PathVariable Long businessId,
                                           @RequestHeader("Authorization") String tokenHeader) {
        if (tokenHeader == null || !tokenHeader.startsWith("Bearer "))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Missing or invalid Authorization header"));

        String token = tokenHeader.substring(7).trim();
        boolean isBusiness = jwtUtil.isBusinessToken(token);
        boolean isAdmin = jwtUtil.isAdminToken(token);
        if (!isBusiness && !isAdmin)
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied."));

        if (isBusiness) {
            Long tokenBusinessId = jwtUtil.extractId(token);
            if (!tokenBusinessId.equals(businessId))
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Not your business."));
        }

        List<Activity> items = activityService.findByBusinessId(businessId);
        if (items.isEmpty())
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "No items found for this business"));

        LocalDateTime now = LocalDateTime.now();
        for (Activity a : items) {
            if (a.getEndDatetime() != null && a.getEndDatetime().isBefore(now)
                    && !"Terminated".equalsIgnoreCase(a.getStatus())) {
                a.setStatus("Terminated");
                activityService.save(a);
            }
        }
        return ResponseEntity.ok(items);
    }

    @GetMapping("/by-type/{typeId}")
    public ResponseEntity<?> getByType(
            @PathVariable Long typeId,
            @RequestParam Long ownerProjectLinkId) {

        List<Activity> items = activityService.findByOwnerAndType(ownerProjectLinkId, typeId);

        LocalDateTime now = LocalDateTime.now();
        List<Activity> upcoming = items.stream()
                .filter(a -> a.getEndDatetime().isAfter(now))
                .filter(a -> !"Terminated".equalsIgnoreCase(a.getStatus()))
                .toList();

        return ResponseEntity.ok(upcoming.stream().map(this::toDto).toList());
    }


    @GetMapping("/guest/upcoming")
    public ResponseEntity<?> guestUpcoming(
            @RequestParam Long ownerProjectLinkId,
            @RequestParam(required = false) Long typeId) {

        List<Activity> all = (typeId != null)
                ? activityService.findByOwnerAndType(ownerProjectLinkId, typeId)
                : activityService.findByOwnerProject(ownerProjectLinkId);

        LocalDateTime now = LocalDateTime.now();

        List<Activity> upcoming = all.stream()
                .filter(a -> a.getEndDatetime() != null && a.getEndDatetime().isAfter(now))
                .filter(a -> !"Terminated".equalsIgnoreCase(a.getStatus()))
                .filter(a -> a.getBusiness() != null && Boolean.TRUE.equals(a.getBusiness().getIsPublicProfile()))
                .toList();

        return ResponseEntity.ok(upcoming.stream().map(this::toDto).toList());
    }


    /* ---------------- personalized feed (owner-linked user) ---------------- */

    @GetMapping("/category-based/{userId}")
    public ResponseEntity<?> categoryBased(@RequestHeader("Authorization") String auth,
                                           @RequestParam Long ownerProjectLinkId,
                                           @PathVariable Long userId) {
        try {
            String token = strip(auth);
            if (!jwtUtil.isUserToken(token))
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "User token required."));

            Users u = getScopedUserFromToken(auth, ownerProjectLinkId);
            if (u == null || !u.getId().equals(userId))
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Token does not match requested user ID"));

            List<Activity> items = activityService.findItemsByUserCategories(userId);
            LocalDateTime now = LocalDateTime.now();
            List<Activity> pending = items.stream()
                    .filter(a -> a.getEndDatetime() != null && a.getEndDatetime().isAfter(now))
                    .filter(a -> !"Terminated".equalsIgnoreCase(a.getStatus()))
                    .toList();
            if (pending.isEmpty())
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "No pending items found for user's categories"));
            return ResponseEntity.ok(pending.stream().map(this::toDto).toList());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "message", "Invalid or missing token",
                    "error", e.getMessage()
            ));
        }
    }

    /* ---------------- availability (owner-linked user) ---------------- */

    @GetMapping("/{itemId}/check-availability")
    public ResponseEntity<?> checkAvailability(@PathVariable Long itemId,
                                               @RequestParam("participants") String participantsStr,
                                               @RequestParam Long ownerProjectLinkId,
                                               @RequestHeader("Authorization") String authHeader) {
        try {
            int requested = Integer.parseInt(participantsStr.replaceAll("[^\\d]", ""));
            Users user = getScopedUserFromToken(authHeader, ownerProjectLinkId);

            if (orderService.hasUserAlreadyBooked(itemId, user.getId()))
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("available", false, "error", "You already booked this item."));

            boolean ok = activityService.isAvailable(itemId, requested);
            if (ok) return ResponseEntity.ok(Map.of("available", true, "message", "Available to book"));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("available", false, "error", "Item is fully booked or not enough slots."));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid participants value"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Server error: " + e.getMessage()));
        }
    }

    /* ---------------- confirm order (owner-linked user) ---------------- */

    @PostMapping("/confirm-order")
    public ResponseEntity<?> confirmorder(@RequestHeader("Authorization") String auth,
                                            @RequestParam Long ownerProjectLinkId,
                                            @RequestBody Map<String, Object> data) {
        try {
            Users user = getScopedUserFromToken(auth, ownerProjectLinkId);
            Long itemId = Long.parseLong(data.get("itemId").toString());
            int participants = Integer.parseInt(data.get("participants").toString());
            String stripePaymentId = data.get("stripePaymentId").toString();

            Long currencyId = null;
            Object cid = data.get("currencyId");
            if (cid != null && !cid.toString().isBlank()) {
                currencyId = Long.parseLong(cid.toString());
            }

            OrderItem order = orderService.createBookItem(user.getId(), itemId, participants, stripePaymentId, currencyId);
            return ResponseEntity.ok(Map.of("message", "order confirmed", "orderId", order.getId()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    /* ---------------- cash order (business scope) ---------------- */

    @PostMapping("/book-cash")
    public ResponseEntity<?> bookCash(@RequestHeader("Authorization") String auth,
                                      @RequestBody BookCashRequest dto) {
        try {
            Long businessId = jwtUtil.extractBusinessId(auth);
            BusinessUser client = businessUserService.findBusinessUserById(dto.getBusinessUserId());
            if (client == null || client.getBusiness() == null || !client.getBusiness().getId().equals(businessId)) {
                return ResponseEntity.status(403).body("Unauthorized: Client does not belong to your business");
            }

            OrderItem order = activityService.createCashorderByBusiness(
                    dto.getItemId(), dto.getBusinessUserId(), dto.getParticipants(), dto.isWasPaid()
            );
            return ResponseEntity.ok(order);

        } catch (UnsupportedOperationException uoe) {
            return ResponseEntity.status(501).body(Map.of("error", "Not implemented: " + uoe.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    /* ---------------- DTO ---------------- */

    public static class BookCashRequest {
        private Long itemId;
        private Long businessUserId;
        private int participants;
        private boolean wasPaid;
        private Long currencyId;

        public Long getItemId() { return itemId; }
        public void setItemId(Long itemId) { this.itemId = itemId; }
        public Long getBusinessUserId() { return businessUserId; }
        public void setBusinessUserId(Long businessUserId) { this.businessUserId = businessUserId; }
        public int getParticipants() { return participants; }
        public void setParticipants(int participants) { this.participants = participants; }
        public boolean isWasPaid() { return wasPaid; }
        public void setWasPaid(boolean wasPaid) { this.wasPaid = wasPaid; }
        public Long getCurrencyId() { return currencyId; }
        public void setCurrencyId(Long currencyId) { this.currencyId = currencyId; }
    }
    
    
    @GetMapping("/owner/app-items")
    @Operation(summary = "List all items for an app (by ownerProjectLinkId) – OWNER / SUPER_ADMIN only")
    public ResponseEntity<?> getByOwnerProject(
            @RequestHeader("Authorization") String auth,
            @RequestParam Long ownerProjectLinkId
    ) {
        if (auth == null || !auth.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing or invalid Authorization header"));
        }

        String token = strip(auth);

        // OWNER or SUPER_ADMIN only
        if (!hasRole(token, "OWNER", "SUPER_ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied."));
        }

        List<Activity> items = activityService.findByOwnerProject(ownerProjectLinkId);
        if (items.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No items found for this app"));
        }

        LocalDateTime now = LocalDateTime.now();
        List<Activity> normalized = new ArrayList<>();
        for (Activity a : items) {
            if (a.getEndDatetime() != null && a.getEndDatetime().isBefore(now)
                    && !"Terminated".equalsIgnoreCase(a.getStatus())) {
                a.setStatus("Terminated");
                activityService.save(a);
            }
            normalized.add(a);
        }

        // Better to send DTOs not entities
        return ResponseEntity.ok(
                normalized.stream()
                        .map(this::toDto)
                        .toList()
        );
    }

}
