package com.build4all.order.web;

import com.build4all.order.domain.Order;
import com.build4all.order.domain.OrderItem;
import com.build4all.order.domain.OrderStatus;
import com.build4all.order.dto.CheckoutRequest;
import com.build4all.order.dto.CheckoutSummaryResponse;
import com.build4all.order.repository.OrderItemRepository;
import com.build4all.order.repository.OrderRepository;
import com.build4all.order.repository.OrderStatusRepository;
import com.build4all.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.*;

/**
 * OrderController
 *
 * ✅ Goal of this updated controller:
 * - Keep all your existing endpoints working.
 * - Re-organize endpoints into clear sections by actor type:
 *      1) USER (end-user)
 *      2) BUSINESS (merchant)
 *      3) OWNER (application admin / tenant owner)
 *      4) SUPER_ADMIN (engine admin)
 *
 * ✅ Why this is needed:
 * - USER sees only their own orders
 * - BUSINESS sees orders for items they own (item.business.id)
 * - OWNER sees all orders in their application (item.ownerProject.id)
 * - SUPER_ADMIN sees all orders grouped/listed by applications
 *
 * ⚠️ Security reminder:
 * - We assume JwtUtil provides:
 *      - extractId(token) -> userId
 *      - extractBusinessId(token) -> businessId
 *      - extractOwnerProjectId(token) -> ownerProjectId (from JWT claim)
 * - In your current code you are not enforcing role checks here.
 *   It’s fine if you already enforce role access via Spring Security config.
 *
 *  The checkout endpoint:
 *  - POST /api/orders/checkout
 *  - Calls orderService.checkout(userId, request)
 *  - Returns CheckoutSummaryResponse
 *
 *  If you integrated Payment Orchestrator:
 *  - CheckoutSummaryResponse should carry payment fields (clientSecret, redirectUrl, etc.)
 *  - Flutter uses these fields to complete payment and/or show status.
 *
 * ✅ IMPORTANT MODEL NOTE:
 * - Order.status is NOT a string; it is a FK to order_status table.
 * - Therefore, updating status means:
 *      statusRepo.findByNameIgnoreCase(code) -> order.setStatus(entity) -> save order
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    /** Main entry point for all order-related business logic */
    private final com.build4all.order.service.OrderService orderService;

    /** JWT parser used to extract userId/businessId/ownerProjectId from Authorization Bearer token */
    private final JwtUtil jwt;

    /** Used only for read-model queries (order cards, business list, insights, owner/super_admin views) */
    private final OrderItemRepository orderItemRepo;

    /** Header repository (orders table) used for OWNER order drill-down and FK status updates */
    private final OrderRepository orderRepo;

    /** FK lookup repository (order_status table) used to load OrderStatus entities by name */
    private final OrderStatusRepository statusRepo;

    public OrderController(com.build4all.order.service.OrderService orderService,
                           JwtUtil jwt,
                           OrderItemRepository orderItemRepo,
                           OrderRepository orderRepo,
                           OrderStatusRepository statusRepo) {
        this.orderService = orderService;
        this.jwt = jwt;
        this.orderItemRepo = orderItemRepo;
        this.orderRepo = orderRepo;
        this.statusRepo = statusRepo;
    }

    /**
     * Strip "Bearer " prefix (if present) from Authorization header.
     * Your JwtUtil expects the raw token string, not the full header.
     */
    private String strip(String auth) {
        if (auth == null) return "";
        return auth.replaceFirst("(?i)^Bearer\\s+", "").trim();
    }

    /* ---------------------------- helpers (safe getters + shaping) ---------------------------- */

    /**
     * Reflection helper:
     * - Your domain model has variations (Item may be Activity/Product/etc.)
     * - Not all classes share the same getter names for the same concept.
     * This tries multiple getters safely (returns null if nothing works).
     *
     * Example:
     * tryGet(item, "getName", "getItemName", "getTitle")
     */
    private Object tryGet(Object target, String... candidates) {
        if (target == null) return null;
        Class<?> c = target.getClass();
        for (String name : candidates) {
            try {
                Method m = c.getMethod(name);
                return m.invoke(target);
            } catch (Exception ignored) { }
        }
        return null;
    }

    /**
     * UI convenience:
     * - Convert raw status codes stored in DB (PENDING, COMPLETED, ...)
     *   into the style you want to show in Flutter cards.
     */
    private static String titleCaseStatus(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toUpperCase();
        return switch (s) {
            case "PENDING" -> "Pending";
            case "COMPLETED" -> "Completed";
            case "CANCELED" -> "Canceled";
            case "CANCEL_REQUESTED" -> "CancelRequested";
            case "REJECTED" -> "Rejected";
            case "REFUNDED" -> "Refunded";
            default -> s.isEmpty() ? "" : s.charAt(0) + s.substring(1).toLowerCase();
        };
    }

    /**
     * Shapes a DB projection row (Map) into the exact structure the Flutter “order card” expects.
     *
     * Input row keys come from OrderItemRepository.findUserOrderCards() projection.
     * Output shape:
     * {
     *   id, quantity, wasPaid, orderStatus,
     *   item: { itemName, location, startDatetime, imageUrl },
     *   order: { status }
     * }
     */
    private Map<String, Object> toUserCardShape(Map<String, Object> row) {
        Map<String, Object> out = new HashMap<>(row);

        // item{}
        Map<String, Object> item = new HashMap<>();
        item.put("itemName", out.remove("itemName"));
        item.put("location", out.remove("location"));
        item.put("startDatetime", out.remove("startDatetime"));
        item.put("imageUrl", out.remove("imageUrl"));
        out.put("item", item);

        // order{status}
        Map<String, Object> order = new HashMap<>();
        order.put("status", out.get("orderStatus"));
        out.put("order", order);

        Object os = out.get("orderStatus");
        out.put("orderStatus", titleCaseStatus(os == null ? null : os.toString()));

        return out;
    }

    /**
     * Shapes a rich OrderItem entity into a Business/Owner/SuperAdmin dashboard row.
     * Includes:
     * - item minimal info
     * - user minimal info
     * - computed totalPrice
     * - status/wasPaid
     * - paymentMethod (if present on order header)
     */
    private Map<String, Object> toDashboardOrderShape(OrderItem oi) {
        var o = oi.getOrder();
        var i = oi.getItem();
        var u = oi.getUser();
        var cur = oi.getCurrency();

        Map<String, Object> item = new HashMap<>();
        item.put("id", i != null ? tryGet(i, "getId") : null);
        item.put("itemName", i != null ? tryGet(i, "getName", "getItemName", "getTitle") : null);
        item.put("location", i != null ? tryGet(i, "getLocation", "getAddress", "getPlace") : null);
        item.put("startDatetime", i != null ? tryGet(i, "getStartDatetime", "getStartDateTime", "getStartAt", "getStart") : null);
        item.put("imageUrl", i != null ? tryGet(i, "getImageUrl", "getImage", "getImagePath") : null);

        // Currency might come from order header or from line currency
        Map<String, Object> currency = null;
        Object oc = (o != null) ? tryGet(o, "getCurrency") : null;
        if (oc != null) {
            Object code = tryGet(oc, "getCode");
            Object symbol = tryGet(oc, "getSymbol");
            if (code != null || symbol != null) {
                currency = new HashMap<>();
                currency.put("code", code);
                currency.put("symbol", symbol);
            }
        } else if (cur != null) {
            currency = new HashMap<>();
            currency.put("code", tryGet(cur, "getCode"));
            currency.put("symbol", tryGet(cur, "getSymbol"));
        }

        // Minimal user info
        Map<String, Object> user = null;
        if (u != null) {
            user = new HashMap<>();
            user.put("id", tryGet(u, "getId"));
            user.put("username", tryGet(u, "getUsername"));
            user.put("firstName", tryGet(u, "getFirstName"));
            user.put("lastName", tryGet(u, "getLastName"));
            user.put("profilePictureUrl", tryGet(u, "getProfilePictureUrl", "getAvatarUrl", "getPhotoUrl"));
        }

        // Total price display:
        // Prefer order header totalPrice; fallback to line calculation (price * qty).
        double totalPrice = 0.0;
        Object oTot = (o != null) ? tryGet(o, "getTotalPrice") : null;
        if (oTot instanceof BigDecimal bd) {
            totalPrice = bd.doubleValue();
        } else if (oi.getPrice() != null) {
            totalPrice = oi.getPrice().multiply(BigDecimal.valueOf(oi.getQuantity())).doubleValue();
        }

        // ✅ Order status name from FK entity (OrderStatus)
        String statusName = (o != null && o.getStatus() != null) ? o.getStatus().getName() : null;

        String orderStatus = titleCaseStatus(statusName);
        boolean wasPaid = "COMPLETED".equalsIgnoreCase(statusName);

        // Payment method (FK entity: PaymentMethod) - we return either code/name/id (best effort)
        Object paymentMethod = null;
        if (o != null && o.getPaymentMethod() != null) {
            Object codeOrName = tryGet(o.getPaymentMethod(), "getCode", "getName");
            paymentMethod = (codeOrName != null) ? codeOrName : tryGet(o.getPaymentMethod(), "getId");
        }

        // Application scope info (ownerProjectId) - for OWNER/SUPER_ADMIN dashboards
        Object ownerProjectId = (i != null) ? tryGet(tryGet(i, "getOwnerProject"), "getId") : null;

        Map<String, Object> out = new HashMap<>();
        out.put("id", oi.getId());
        out.put("orderId", (o != null) ? o.getId() : null); // important for “open order details”
        out.put("orderStatus", orderStatus);
        out.put("rawStatus", statusName); // useful for filters
        out.put("wasPaid", wasPaid);
        out.put("quantity", oi.getQuantity());
        out.put("totalPrice", totalPrice);
        out.put("paymentMethod", paymentMethod);
        out.put("orderDatetime", (o != null) ? tryGet(o, "getOrderDate", "getCreatedAt") : oi.getCreatedAt());
        out.put("currency", currency);
        out.put("item", item);
        out.put("user", user);
        out.put("ownerProjectId", ownerProjectId); // useful for SUPER_ADMIN / cross-app view
        return out;
    }

    /**
     * Shapes OrderItem into a lightweight “insights” row:
     * - who (clientName)
     * - what (itemName)
     * - paid or not (wasPaid)
     */
    private Map<String, Object> toInsightShape(OrderItem oi) {
        var u = oi.getUser();
        String clientName = null;
        if (u != null) {
            Object uname = tryGet(u, "getUsername");
            if (uname != null && !String.valueOf(uname).isBlank()) {
                clientName = String.valueOf(uname);
            } else {
                String fn = String.valueOf(tryGet(u, "getFirstName"));
                String ln = String.valueOf(tryGet(u, "getLastName"));
                String full = ((fn == null ? "" : fn) + " " + (ln == null ? "" : ln)).trim();
                clientName = full.isBlank() ? null : full;
            }
        }

        String itemName = (oi.getItem() != null)
                ? String.valueOf(tryGet(oi.getItem(), "getName", "getItemName", "getTitle"))
                : null;

        var o = oi.getOrder();
        String statusName = (o != null && o.getStatus() != null) ? o.getStatus().getName() : null;
        boolean wasPaid = "COMPLETED".equalsIgnoreCase(statusName);

        Map<String, Object> out = new HashMap<>();
        out.put("id", oi.getId());
        out.put("businessUserId", null);
        out.put("clientName", clientName);
        out.put("itemName", itemName);
        out.put("wasPaid", wasPaid);
        return out;
    }

    /**
     * OWNER tenant validation:
     * Ensure the selected order belongs to this application (ownerProjectId).
     *
     * Why we validate this way:
     * - Order header does not store ownerProjectId directly.
     * - Scope is derived from OrderItem -> Item -> ownerProject.id
     *
     * Security best practice:
     * - If it's not in this tenant, return "not found" to avoid leaking information.
     */
    private void assertOwnerCanAccessOrder(Long orderId, Long ownerProjectId) {
        boolean ok = orderItemRepo.existsByOrder_IdAndItem_OwnerProject_Id(orderId, ownerProjectId);
        if (!ok) {
            throw new NoSuchElementException("Order not found");
        }
    }

    /**
     * Loads an OrderStatus by name (FK lookup) or throws 400.
     *
     * Because status is FK, we never set strings directly on Order.
     */
    private OrderStatus requireStatusByName(String statusCode) {
        if (statusCode == null || statusCode.isBlank()) {
            throw new IllegalArgumentException("status is required");
        }
        return statusRepo.findByNameIgnoreCase(statusCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("Unknown status: " + statusCode));
    }

    /**
     * Builds a safe response for:
     * - order header
     * - all order items
     *
     * Why we shape it:
     * - avoid lazy-loading JSON issues
     * - provide a stable response to the frontend
     */
    private Map<String, Object> toOwnerOrderDetailsResponse(Order order) {
        Map<String, Object> out = new HashMap<>();

        // --- header ---
        Map<String, Object> header = new HashMap<>();
        header.put("id", order.getId());
        header.put("orderDate", order.getOrderDate());
        header.put("totalPrice", order.getTotalPrice());

        // status (FK entity)
        String statusName = (order.getStatus() != null) ? order.getStatus().getName() : null;
        header.put("status", statusName);
        header.put("statusUi", titleCaseStatus(statusName));

        // payment method (FK entity)
        Object paymentMethod = null;
        if (order.getPaymentMethod() != null) {
            Object codeOrName = tryGet(order.getPaymentMethod(), "getCode", "getName");
            paymentMethod = (codeOrName != null) ? codeOrName : tryGet(order.getPaymentMethod(), "getId");
        }
        header.put("paymentMethod", paymentMethod);

        // currency minimal (optional)
        if (order.getCurrency() != null) {
            Map<String, Object> currency = new HashMap<>();
            currency.put("code", tryGet(order.getCurrency(), "getCode"));
            currency.put("symbol", tryGet(order.getCurrency(), "getSymbol"));
            header.put("currency", currency);
        } else {
            header.put("currency", null);
        }

        // shipping/tax/coupon (optional)
        header.put("shippingCity", order.getShippingCity());
        header.put("shippingPostalCode", order.getShippingPostalCode());
        header.put("shippingMethodId", order.getShippingMethodId());
        header.put("shippingMethodName", order.getShippingMethodName());
        header.put("shippingTotal", order.getShippingTotal());
        header.put("itemTaxTotal", order.getItemTaxTotal());
        header.put("shippingTaxTotal", order.getShippingTaxTotal());
        header.put("couponCode", order.getCouponCode());
        header.put("couponDiscount", order.getCouponDiscount());

        out.put("order", header);

        // --- items ---
        List<Map<String, Object>> items = new ArrayList<>();
        if (order.getOrderItems() != null) {
            for (OrderItem oi : order.getOrderItems()) {
                Map<String, Object> row = new HashMap<>();
                row.put("orderItemId", oi.getId());
                row.put("quantity", oi.getQuantity());
                row.put("price", oi.getPrice());

                Object itemEntity = oi.getItem();
                Map<String, Object> item = new HashMap<>();
                item.put("id", itemEntity != null ? tryGet(itemEntity, "getId") : null);
                item.put("itemName", itemEntity != null ? tryGet(itemEntity, "getName", "getItemName", "getTitle") : null);
                item.put("imageUrl", itemEntity != null ? tryGet(itemEntity, "getImageUrl", "getImage", "getImagePath") : null);
                item.put("location", itemEntity != null ? tryGet(itemEntity, "getLocation", "getAddress", "getPlace") : null);
                item.put("startDatetime", itemEntity != null ? tryGet(itemEntity, "getStartDatetime", "getStartDateTime", "getStartAt", "getStart") : null);

                row.put("item", item);

                // Minimal user info (owner sees who ordered)
                if (oi.getUser() != null) {
                    row.put("user", Map.of(
                            "id", tryGet(oi.getUser(), "getId"),
                            "username", tryGet(oi.getUser(), "getUsername"),
                            "firstName", tryGet(oi.getUser(), "getFirstName"),
                            "lastName", tryGet(oi.getUser(), "getLastName")
                    ));
                } else {
                    row.put("user", null);
                }

                items.add(row);
            }
        }

        out.put("items", items);
        out.put("itemsCount", items.size());

        return out;
    }

    /* =========================================================================================
       USER APIs (End-user)
       ========================================================================================= */

    /**
     * GET /api/orders/myorders
     * Returns the user's orders in the Flutter “card” structure.
     */
    @GetMapping("/myorders")
    @Operation(summary = "USER: List all my orders (card model)")
    public ResponseEntity<?> myOrders(@RequestHeader("Authorization") String auth) {
        Long userId = jwt.extractId(strip(auth));
        var rows = orderItemRepo.findUserOrderCards(userId);
        var shaped = rows.stream().map(this::toUserCardShape).toList();
        return ResponseEntity.ok(shaped);
    }

    /**
     * GET /api/orders/myorders/pending
     * Pending includes:
     * - PENDING
     * - CANCEL_REQUESTED (because user still sees it as “pending resolution”)
     */
    @GetMapping("/myorders/pending")
    @Operation(summary = "USER: List my pending orders (PENDING + CANCEL_REQUESTED)")
    public ResponseEntity<?> myPending(@RequestHeader("Authorization") String auth) {
        Long userId = jwt.extractId(strip(auth));
        var rows = orderItemRepo.findUserOrderCardsByStatuses(userId, List.of("PENDING", "CANCEL_REQUESTED"));
        return ResponseEntity.ok(rows.stream().map(this::toUserCardShape).toList());
    }

    /** GET /api/orders/myorders/completed */
    @GetMapping("/myorders/completed")
    @Operation(summary = "USER: List my completed orders")
    public ResponseEntity<?> myCompleted(@RequestHeader("Authorization") String auth) {
        Long userId = jwt.extractId(strip(auth));
        var rows = orderItemRepo.findUserOrderCardsByStatuses(userId, List.of("COMPLETED"));
        return ResponseEntity.ok(rows.stream().map(this::toUserCardShape).toList());
    }

    /** GET /api/orders/myorders/canceled */
    @GetMapping("/myorders/canceled")
    @Operation(summary = "USER: List my canceled orders")
    public ResponseEntity<?> myCanceled(@RequestHeader("Authorization") String auth) {
        Long userId = jwt.extractId(strip(auth));
        var rows = orderItemRepo.findUserOrderCardsByStatuses(userId, List.of("CANCELED"));
        return ResponseEntity.ok(rows.stream().map(this::toUserCardShape).toList());
    }

    /* ----------------------------------- checkout (NEW) ------------------------------------ */

    /**
     * POST /api/orders/checkout
     *
     * Main checkout endpoint for both:
     * - Activities booking
     * - Ecommerce products purchase
     *
     * Expected:
     * - Authorization: Bearer <jwt>
     * - Body: CheckoutRequest (lines, currencyId, paymentMethod, shipping address, coupon...)
     *
     * Returns:
     * - CheckoutSummaryResponse (totals + orderId + payment fields if you integrated payment orchestrator)
     */
    @PostMapping("/checkout")
    @Operation(summary = "USER: Create order from cart (activities + ecommerce)")
    public ResponseEntity<?> checkout(@RequestHeader("Authorization") String auth,
                                      @Valid @RequestBody CheckoutRequest request) {
        Long userId = jwt.extractId(strip(auth));
        CheckoutSummaryResponse summary = orderService.checkout(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(summary);
    }

    /* ----------------------------------- USER actions ------------------------------------ */

    /**
     * PUT /api/orders/cancel/{orderItemId}
     * User cancels their own order item (service enforces ownership rules).
     */
    @PutMapping("/cancel/{orderItemId}")
    @Operation(summary = "USER: Cancel my order item")
    public ResponseEntity<?> cancel(@RequestHeader("Authorization") String auth,
                                    @PathVariable Long orderItemId) {
        Long actorId = jwt.extractId(strip(auth));
        orderService.cancelorder(orderItemId, actorId);
        return ResponseEntity.ok().build();
    }

    /**
     * PUT /api/orders/pending/{orderItemId}
     * Reset order status back to PENDING (allowed only if not completed).
     */
    @PutMapping("/pending/{orderItemId}")
    @Operation(summary = "USER: Reset my order item to PENDING")
    public ResponseEntity<?> resetToPending(@RequestHeader("Authorization") String auth,
                                            @PathVariable Long orderItemId) {
        Long actorId = jwt.extractId(strip(auth));
        orderService.resetToPending(orderItemId, actorId);
        return ResponseEntity.ok().build();
    }

    /**
     * DELETE /api/orders/delete/{orderItemId}
     * Deletes an order item (service validates ownership/permissions).
     */
    @DeleteMapping("/delete/{orderItemId}")
    @Operation(summary = "USER: Delete my order item")
    public ResponseEntity<?> delete(@RequestHeader("Authorization") String auth,
                                    @PathVariable Long orderItemId) {
        Long actorId = jwt.extractId(strip(auth));
        orderService.deleteorder(orderItemId, actorId);
        return ResponseEntity.noContent().build();
    }

    /**
     * PUT /api/orders/refund/{orderItemId}
     * Requests or performs refund logic (service decides eligibility and status transitions).
     */
    @PutMapping("/refund/{orderItemId}")
    @Operation(summary = "USER: Refund my order item (if eligible)")
    public ResponseEntity<?> refund(@RequestHeader("Authorization") String auth,
                                    @PathVariable Long orderItemId) {
        Long actorId = jwt.extractId(strip(auth));
        orderService.refundIfEligible(orderItemId, actorId);
        return ResponseEntity.ok().build();
    }

    /**
     * PUT /api/orders/cancel/request/{orderItemId}
     * User requests cancellation (business will approve/reject).
     */
    @PutMapping("/cancel/request/{orderItemId}")
    @Operation(summary = "USER: Request cancellation (status => CANCEL_REQUESTED)")
    public ResponseEntity<?> requestCancel(@RequestHeader("Authorization") String auth,
                                           @PathVariable Long orderItemId) {
        Long userId = jwt.extractId(strip(auth));
        orderService.requestCancel(orderItemId, userId);
        return ResponseEntity.ok().build();
    }

    /* =========================================================================================
       BUSINESS APIs (Merchant dashboards)
       ========================================================================================= */

    /**
     * GET /api/orders/mybusinessorders
     * Returns business orders list (shaped for dashboard).
     *
     * Scope:
     * - items owned by this businessId
     */
    @GetMapping("/mybusinessorders")
    @Operation(summary = "BUSINESS: List orders for my business items")
    public ResponseEntity<?> myBusinessOrders(@RequestHeader("Authorization") String auth) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        var list = orderItemRepo.findRichByBusinessId(businessId)
                .stream().map(this::toDashboardOrderShape).toList();
        return ResponseEntity.ok(list);
    }

    /**
     * GET /api/orders/insights/orders
     * Returns lightweight insights rows (clientName, itemName, wasPaid).
     *
     * Scope:
     * - items owned by this businessId
     */
    @GetMapping("/insights/orders")
    @Operation(summary = "BUSINESS: Lightweight insights rows")
    public ResponseEntity<?> insights(@RequestHeader("Authorization") String auth) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        var list = orderItemRepo.findRichByBusinessId(businessId)
                .stream().map(this::toInsightShape).toList();
        return ResponseEntity.ok(list);
    }

    /**
     * PUT /api/orders/cancel/approve/{orderItemId}
     * Business approves the cancel request (ownership enforced by service).
     */
    @PutMapping("/cancel/approve/{orderItemId}")
    @Operation(summary = "BUSINESS: Approve cancellation request (CANCEL_REQUESTED => CANCELED)")
    public ResponseEntity<?> approveCancel(@RequestHeader("Authorization") String auth,
                                           @PathVariable Long orderItemId) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        orderService.approveCancel(orderItemId, businessId);
        return ResponseEntity.ok().build();
    }

    /**
     * PUT /api/orders/cancel/reject/{orderItemId}
     * Business rejects cancel request (back to PENDING).
     */
    @PutMapping("/cancel/reject/{orderItemId}")
    @Operation(summary = "BUSINESS: Reject cancellation request (CANCEL_REQUESTED => PENDING)")
    public ResponseEntity<?> rejectCancel(@RequestHeader("Authorization") String auth,
                                          @PathVariable Long orderItemId) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        orderService.rejectCancel(orderItemId, businessId);
        return ResponseEntity.ok().build();
    }

    /**
     * PUT /api/orders/cancel/mark-refunded/{orderItemId}
     * Business marks canceled order as refunded (status => REFUNDED).
     */
    @PutMapping("/cancel/mark-refunded/{orderItemId}")
    @Operation(summary = "BUSINESS: Mark refunded (CANCELED => REFUNDED)")
    public ResponseEntity<?> markRefunded(@RequestHeader("Authorization") String auth,
                                          @PathVariable Long orderItemId) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        orderService.markRefunded(orderItemId, businessId);
        return ResponseEntity.ok().build();
    }

    /**
     * PUT /api/orders/mark-paid/{orderItemId}
     * Business manually marks an order as paid/completed.
     * (Useful for cash payments, bank transfer, etc.)
     */
    @PutMapping("/mark-paid/{orderItemId}")
    @Operation(summary = "BUSINESS: Mark order paid (manual; useful for CASH)")
    public ResponseEntity<?> markPaid(@RequestHeader("Authorization") String auth,
                                      @PathVariable Long orderItemId) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        orderService.markPaid(orderItemId, businessId);
        return ResponseEntity.ok().build();
    }

    /**
     * PUT /api/orders/order/reject/{orderItemId}
     * Business rejects an order (status => REJECTED).
     */
    @PutMapping("/order/reject/{orderItemId}")
    @Operation(summary = "BUSINESS: Reject order (=> REJECTED)")
    public ResponseEntity<?> rejectOrder(@RequestHeader("Authorization") String auth,
                                         @PathVariable Long orderItemId) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        orderService.rejectorder(orderItemId, businessId);
        return ResponseEntity.ok().build();
    }

    /**
     * PUT /api/orders/order/unreject/{orderItemId}
     * Business restores rejected order back to PENDING.
     */
    @PutMapping("/order/unreject/{orderItemId}")
    @Operation(summary = "BUSINESS: Unreject order (REJECTED => PENDING)")
    public ResponseEntity<?> unrejectOrder(@RequestHeader("Authorization") String auth,
                                           @PathVariable Long orderItemId) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        orderService.unrejectorder(orderItemId, businessId);
        return ResponseEntity.ok().build();
    }

    /* =========================================================================================
       OWNER APIs (Application Admin / Tenant owner)
       =========================================================================================
       Owner should see:
       - all orders in their application (tenant/app)
       - filter by status
       - open one specific order and see ALL items
       - change order header status (FK) (delivered workflow etc.)
       Scope is ownerProjectId, derived from Item.ownerProject.id
       ========================================================================================= */

    /**
     * GET /api/orders/owner/orders
     * OWNER: list all orders (headers) in my application (tenant) with items loaded.
     *
     * NOTE:
     * - Uses OrderRepository because this is header-level listing.
     * - Query scopes by OrderItem -> Item -> ownerProjectId.
     */
    @GetMapping("/owner/orders")
    @Operation(summary = "OWNER: List all orders (headers) in my application with items")
    public ResponseEntity<?> ownerAllOrders(@RequestHeader("Authorization") String auth) {
        Long ownerProjectId = jwt.extractOwnerProjectId(strip(auth));

        var list = orderRepo.findAllByOwnerProjectIdWithItems(ownerProjectId);

        // For consistency with dashboards, we shape each order into a lightweight map:
        // - header fields
        // - itemsCount
        // - status name + UI case
        var out = list.stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId());
            m.put("orderDate", o.getOrderDate());
            m.put("totalPrice", o.getTotalPrice());
            String st = (o.getStatus() != null) ? o.getStatus().getName() : null;
            m.put("status", st);
            m.put("statusUi", titleCaseStatus(st));
            m.put("itemsCount", (o.getOrderItems() == null) ? 0 : o.getOrderItems().size());
            return m;
        }).toList();

        return ResponseEntity.ok(out);
    }

    /**
     * GET /api/orders/owner/orders/status/{status}
     * OWNER: list all orders in my application filtered by status.
     *
     * Examples:
     * - PENDING
     * - COMPLETED
     * - CANCEL_REQUESTED
     * - CANCELED
     * - REFUNDED
     * - REJECTED
     */
    @GetMapping("/owner/orders/status/{status}")
    @Operation(summary = "OWNER: List orders in my application filtered by status (header list)")
    public ResponseEntity<?> ownerOrdersByStatus(@RequestHeader("Authorization") String auth,
                                                 @PathVariable String status) {
        Long ownerProjectId = jwt.extractOwnerProjectId(strip(auth));
        String normalized = (status == null) ? "" : status.trim().toUpperCase();

        var list = orderRepo.findAllByOwnerProjectIdWithItemsAndStatuses(ownerProjectId, List.of(normalized));

        var out = list.stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId());
            m.put("orderDate", o.getOrderDate());
            m.put("totalPrice", o.getTotalPrice());
            String st = (o.getStatus() != null) ? o.getStatus().getName() : null;
            m.put("status", st);
            m.put("statusUi", titleCaseStatus(st));
            m.put("itemsCount", (o.getOrderItems() == null) ? 0 : o.getOrderItems().size());
            return m;
        }).toList();

        return ResponseEntity.ok(out);
    }

    /**
     * GET /api/orders/owner/orders/status
     * OWNER: list all orders filtered by multiple statuses.
     * Example:
     *   /api/orders/owner/orders/status?statuses=PENDING,COMPLETED
     */
    @GetMapping("/owner/orders/status")
    @Operation(summary = "OWNER: List orders in my application filtered by multiple statuses (header list)")
    public ResponseEntity<?> ownerOrdersByStatuses(@RequestHeader("Authorization") String auth,
                                                   @RequestParam(name = "statuses") List<String> statuses) {
        Long ownerProjectId = jwt.extractOwnerProjectId(strip(auth));

        List<String> normalized = (statuses == null) ? List.of() :
                statuses.stream()
                        .filter(s -> s != null && !s.isBlank())
                        .map(s -> s.trim().toUpperCase())
                        .toList();

        var list = orderRepo.findAllByOwnerProjectIdWithItemsAndStatuses(ownerProjectId, normalized);

        var out = list.stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId());
            m.put("orderDate", o.getOrderDate());
            m.put("totalPrice", o.getTotalPrice());
            String st = (o.getStatus() != null) ? o.getStatus().getName() : null;
            m.put("status", st);
            m.put("statusUi", titleCaseStatus(st));
            m.put("itemsCount", (o.getOrderItems() == null) ? 0 : o.getOrderItems().size());
            return m;
        }).toList();

        return ResponseEntity.ok(out);
    }

    /**
     * ✅ NEW
     * GET /api/orders/owner/orders/{orderId}
     * OWNER: open one specific order and see header + ALL items.
     *
     * Security:
     * - We first validate that the order is inside the ownerProject scope
     *   using OrderItemRepository.existsByOrder_IdAndItem_OwnerProject_Id(...)
     * - Then we load the order with items using OrderRepository.findByIdWithItems(...)
     */
    @GetMapping("/owner/orders/{orderId}")
    @Operation(summary = "OWNER: Get order details (header + all items)")
    public ResponseEntity<?> ownerOrderDetails(@RequestHeader("Authorization") String auth,
                                               @PathVariable Long orderId) {
        Long ownerProjectId = jwt.extractOwnerProjectId(strip(auth));

        // Tenant isolation check (do NOT leak other tenant orders)
        assertOwnerCanAccessOrder(orderId, ownerProjectId);

        Order order = orderRepo.findByIdWithItems(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));

        return ResponseEntity.ok(toOwnerOrderDetailsResponse(order));
    }

    /**
     * ✅ NEW
     * PUT /api/orders/owner/orders/{orderId}/status
     * OWNER: Change order HEADER status (FK to order_status table).
     *
     * Body example:
     * { "status": "COMPLETED" }
     * { "status": "CANCELED" }
     * { "status": "REFUNDED" }
     *
     * Notes:
     * - This updates the Order header (orders.status_id).
     * - Because it is FK, we load OrderStatus entity and assign it.
     * - For WooCommerce-like lifecycle, you can allow:
     *      PENDING -> COMPLETED (delivered)
     *      PENDING -> CANCELED
     *      CANCELED -> REFUNDED
     *   and later add rules (cannot go backwards, etc.) in service layer.
     *
     * Best practice:
     * - Put advanced transition rules into OrderServiceImpl (not controller),
     *   but we keep it here minimal and safe.
     */
    @PutMapping("/owner/orders/{orderId}/status")
    @Operation(summary = "OWNER: Update order header status (FK)")
    public ResponseEntity<?> ownerUpdateOrderStatus(@RequestHeader("Authorization") String auth,
                                                    @PathVariable Long orderId,
                                                    @RequestBody Map<String, Object> body) {
        Long ownerProjectId = jwt.extractOwnerProjectId(strip(auth));

        // Tenant isolation check
        assertOwnerCanAccessOrder(orderId, ownerProjectId);

        String statusCode = (body == null) ? null : String.valueOf(body.get("status"));
        OrderStatus newStatus = requireStatusByName(statusCode);

        // Load order with items (optional, but safe)
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));

        // FK update
        order.setStatus(newStatus);

        // Optional: bump timestamp if you treat orderDate as "updated at"
        // (Your entity comment says you use orderDate also in status flips)
        order.setOrderDate(java.time.LocalDateTime.now());

        orderRepo.save(order);

        // Return new state to frontend
        return ResponseEntity.ok(Map.of(
                "orderId", order.getId(),
                "status", newStatus.getName(),
                "statusUi", titleCaseStatus(newStatus.getName())
        ));
    }

    /* =========================================================================================
       SUPER_ADMIN APIs (Engine-level)
       =========================================================================================
       Super admin should see:
       - all orders grouped by applications (ownerProjectId)
       - ability to drill-down: view orders for a specific application
       ========================================================================================= */

    /**
     * GET /api/orders/superadmin/applications
     * SUPER_ADMIN: returns list of applications with order counts.
     *
     * Output example:
     * [
     *   { "ownerProjectId": 10, "ordersCount": 52 },
     *   { "ownerProjectId": 12, "ordersCount": 31 }
     * ]
     */
    @GetMapping("/superadmin/applications")
    @Operation(summary = "SUPER_ADMIN: Count orders grouped by application (ownerProjectId)")
    public ResponseEntity<?> superAdminApplicationsOrdersCount(@RequestHeader("Authorization") String auth) {

        var rows = orderRepo.countOrdersGroupedByOwnerProject();
        var out = rows.stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("ownerProjectId", r[0]);
            m.put("ordersCount", r[1]);
            return m;
        }).toList();

        return ResponseEntity.ok(out);
    }

    /**
     * GET /api/orders/superadmin/applications/{ownerProjectId}/orders
     * SUPER_ADMIN: drill-down view for a specific application.
     *
     * Note:
     * - We use OrderRepository here because it's header + items in one tenant scope.
     */
    @GetMapping("/superadmin/applications/{ownerProjectId}/orders")
    @Operation(summary = "SUPER_ADMIN: List all orders for a specific application")
    public ResponseEntity<?> superAdminOrdersByApplication(@RequestHeader("Authorization") String auth,
                                                           @PathVariable Long ownerProjectId) {

        var list = orderRepo.findAllByOwnerProjectIdWithItems(ownerProjectId);

        var out = list.stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId());
            m.put("orderDate", o.getOrderDate());
            m.put("totalPrice", o.getTotalPrice());
            String st = (o.getStatus() != null) ? o.getStatus().getName() : null;
            m.put("status", st);
            m.put("statusUi", titleCaseStatus(st));
            m.put("itemsCount", (o.getOrderItems() == null) ? 0 : o.getOrderItems().size());
            return m;
        }).toList();

        return ResponseEntity.ok(out);
    }

    /**
     * GET /api/orders/superadmin/applications/{ownerProjectId}/orders/status/{status}
     * SUPER_ADMIN: drill-down by application and status.
     */
    @GetMapping("/superadmin/applications/{ownerProjectId}/orders/status/{status}")
    @Operation(summary = "SUPER_ADMIN: List orders for a specific application filtered by status")
    public ResponseEntity<?> superAdminOrdersByApplicationAndStatus(@RequestHeader("Authorization") String auth,
                                                                    @PathVariable Long ownerProjectId,
                                                                    @PathVariable String status) {
        String normalized = (status == null) ? "" : status.trim().toUpperCase();

        var list = orderRepo.findAllByOwnerProjectIdWithItemsAndStatuses(ownerProjectId, List.of(normalized));

        var out = list.stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId());
            m.put("orderDate", o.getOrderDate());
            m.put("totalPrice", o.getTotalPrice());
            String st = (o.getStatus() != null) ? o.getStatus().getName() : null;
            m.put("status", st);
            m.put("statusUi", titleCaseStatus(st));
            m.put("itemsCount", (o.getOrderItems() == null) ? 0 : o.getOrderItems().size());
            return m;
        }).toList();

        return ResponseEntity.ok(out);
    }

    /* =========================================================================================
       ERROR HANDLERS (keep same behavior)
       ========================================================================================= */

    /**
     * Converts IllegalArgumentException into HTTP 400 with JSON: { "error": "..." }
     * Helpful for Flutter forms and validation errors.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * Converts NoSuchElementException into HTTP 404 with JSON: { "error": "..." }
     * Useful for owner tenant-scope "not found" behavior.
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<?> notFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * Catch-all exception handler: HTTP 500 with JSON error message.
     * (In production, consider logging ex + hiding internal error details.)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> serverError(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", ex.getMessage()));
    }
}
