package com.build4all.order.web;

import com.build4all.licensing.guard.OwnerSubscriptionGuard;
import com.build4all.order.domain.Order;
import com.build4all.order.domain.OrderItem;
import com.build4all.order.domain.OrderStatus;
import com.build4all.order.dto.CheckoutRequest;
import com.build4all.order.dto.CheckoutSummaryResponse;
import com.build4all.order.repository.OrderItemRepository;
import com.build4all.order.repository.OrderRepository;
import com.build4all.order.repository.OrderStatusRepository;
import com.build4all.payment.service.OrderPaymentReadService;
import com.build4all.payment.service.OrderPaymentWriteService;
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
 *
 * ✅ NEW PAYMENT NOTE (important):
 * - "Was paid" (or fully paid) should not be inferred from order.status.
 * - Source of truth is payment_transactions ledger:
 *      paidAmount = SUM(tx.amount) where tx.status == 'PAID'
 *      fullyPaid  = paidAmount >= order.totalPrice
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

    private final OrderPaymentWriteService paymentWrite;
    private final OwnerSubscriptionGuard ownerSubscriptionGuard;
    /**
     * ✅ NEW:
     * Payment read helper that computes:
     * - paidAmount (sum of PAID transactions)
     * - remainingAmount
     * - fullyPaid
     * from payment_transactions table.
     *
     * IMPORTANT:
     * - This is "read model", no changes to DB.
     * - We use batch mode in list endpoints to avoid N+1 queries.
     */
    private final OrderPaymentReadService paymentRead;

    public OrderController(com.build4all.order.service.OrderService orderService,
            JwtUtil jwt,
            OrderItemRepository orderItemRepo,
            OrderRepository orderRepo,
            OrderStatusRepository statusRepo,
            OrderPaymentReadService paymentRead,
            OrderPaymentWriteService paymentWrite,
            OwnerSubscriptionGuard ownerSubscriptionGuard) {
this.orderService = orderService;
this.jwt = jwt;
this.orderItemRepo = orderItemRepo;
this.orderRepo = orderRepo;
this.statusRepo = statusRepo;
this.paymentRead = paymentRead;
this.paymentWrite = paymentWrite;
this.ownerSubscriptionGuard = ownerSubscriptionGuard;
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
     * ✅ NEW helper:
     * Parses statuses from query params safely.
     *
     * Supports:
     * - ?statuses=PENDING,COMPLETED
     * - ?statuses=PENDING&statuses=COMPLETED
     * - spaces are trimmed automatically
     */
    private static List<String> normalizeStatuses(List<String> statuses) {
        if (statuses == null) return List.of();

        List<String> out = new ArrayList<>();
        for (String s : statuses) {
            if (s == null) continue;
            String[] parts = s.split(",");
            for (String p : parts) {
                if (p == null) continue;
                String x = p.trim();
                if (!x.isBlank()) out.add(x.toUpperCase());
            }
        }
        return out;
    }

    /**
     * ✅ NEW helper:
     * Shapes payment summary into safe JSON map (no provider secrets).
     */
    private static Map<String, Object> paymentToMap(OrderPaymentReadService.PaymentSummary ps) {
        if (ps == null) {
            return Map.of(
                    "orderTotal", BigDecimal.ZERO,
                    "paidAmount", BigDecimal.ZERO,
                    "remainingAmount", BigDecimal.ZERO,
                    "fullyPaid", false,
                    "paymentState", "UNPAID"
            );
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderTotal", ps.getOrderTotal());
        m.put("paidAmount", ps.getPaidAmount());
        m.put("remainingAmount", ps.getRemainingAmount());
        m.put("fullyPaid", ps.isFullyPaid());
        m.put("paymentState", ps.getPaymentState());
        return m;
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
     *
     * ✅ NEW PAYMENT RULE:
     * - wasPaid should be derived from payment_transactions, not from status.
     *
     * Notes:
     * - This method shapes ONE OrderItem row. If you want "wasPaid" accurate here,
     *   you can:
     *   (A) Keep legacy behavior (status == COMPLETED) OR
     *   (B) Enhance by calling paymentRead.summaryForOrder(orderId,...)
     *
     * For performance, the best practice is to batch compute payment info in list endpoints.
     * This method is used for BUSINESS endpoints that list OrderItems, not Orders.
     * We keep legacy "COMPLETED" logic here to avoid DB calls per row.
     *
     * If you want, I can provide a batch payment-aware version for BUSINESS dashboards too.
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

        // Legacy behavior (kept):
        // wasPaid == COMPLETED (status workflow)
        // If you want strict money-based "paid", apply paymentRead in batch in the endpoint.
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
     *
     * NOTE:
     * - This keeps legacy "COMPLETED" logic for wasPaid.
     * - If you want ledger-accurate payments here too, we can batch compute by orderId.
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
     *
     * ✅ NEW:
     * - Add payment summary (paidAmount/remaining/fullyPaid) computed from payment_transactions.
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
        header.put("shippingAddress", order.getShippingAddress());
        header.put("shippingPhone", order.getShippingPhone());
        header.put("shippingTotal", order.getShippingTotal());
        header.put("itemTaxTotal", order.getItemTaxTotal());
        header.put("shippingTaxTotal", order.getShippingTaxTotal());
        header.put("couponCode", order.getCouponCode());
        header.put("couponDiscount", order.getCouponDiscount());
        header.put("orderCode", order.getOrderCode());
        header.put("orderSeq", order.getOrderSeq());

        // ✅ NEW: payment summary (ledger-based)
        OrderPaymentReadService.PaymentSummary ps =
                paymentRead.summaryForOrder(order.getId(), order.getTotalPrice());
        header.put("fullyPaid", ps.isFullyPaid());
        header.put("payment", paymentToMap(ps));

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
                    Map<String, Object> u = new HashMap<>();
                    u.put("id", tryGet(oi.getUser(), "getId"));
                    u.put("username", tryGet(oi.getUser(), "getUsername"));
                    u.put("firstName", tryGet(oi.getUser(), "getFirstName"));
                    u.put("lastName", tryGet(oi.getUser(), "getLastName"));
                    row.put("user", u);
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
    public ResponseEntity<?> myOrders(@RequestHeader("Authorization") String auth) {
        Long userId = jwt.extractId(strip(auth));

        var cards = orderRepo.findUserOrderCardsGrouped(userId);

        // ✅ attach payment info (UNPAID / PARTIAL / PAID) using your ledger
        Map<Long, BigDecimal> totals = new HashMap<>();
        for (var c : cards) {
            Long orderId = ((Number)c.get("orderId")).longValue();
            BigDecimal total = (BigDecimal) c.getOrDefault("totalPrice", BigDecimal.ZERO);
            totals.put(orderId, total);
        }

        var payByOrderId = paymentRead.summariesForOrders(totals);

        for (var c : cards) {
            Long orderId = ((Number)c.get("orderId")).longValue();
            var ps = payByOrderId.get(orderId);
            c.put("payment", paymentToMap(ps));          // {paymentState, paidAmount, ...}
            c.put("fullyPaid", ps != null && ps.isFullyPaid());
            c.put("orderStatusUi", titleCaseStatus(String.valueOf(c.get("orderStatus"))));
        }

        return ResponseEntity.ok(cards);
    }

    /**
     * GET /api/orders/myorders/pending
     * Pending includes:
     * - PENDING
     * - CANCEL_REQUESTED (because user still sees it as “pending resolution”)
     */
    @GetMapping("/myorders/pending")
    public ResponseEntity<?> myPending(@RequestHeader("Authorization") String auth) {
        Long userId = jwt.extractId(strip(auth));
        var cards = orderRepo.findUserOrderCardsGroupedByStatuses(userId, List.of("PENDING", "CANCEL_REQUESTED"));
        return ResponseEntity.ok(cards);
    }

    /** GET /api/orders/myorders/completed */
    @GetMapping("/myorders/completed")
    @Operation(summary = "USER: List my completed orders")
    public ResponseEntity<?> myCompleted(@RequestHeader("Authorization") String auth) {
        Long userId = jwt.extractId(strip(auth));
        var rows = orderItemRepo.findUserOrderCardsByStatuses(userId, List.of("COMPLETED"));
        return ResponseEntity.ok(rows.stream().map(this::toUserCardShape).toList());
    }

    
    @GetMapping("/myorders/{orderId}")
    public ResponseEntity<?> myOrderDetails(@RequestHeader("Authorization") String auth,
                                            @PathVariable Long orderId) {

        Long userId = jwt.extractId(strip(auth));

        // ✅ IMPORTANT: fetch order + items in ONE query
        Order order = orderRepo.findByIdAndUserIdWithItems(orderId, userId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));

        // ✅ Convert status FK to STRING (avoid lazy serialization)
        String statusName = (order.getStatus() != null) ? order.getStatus().getName() : null;

        // ✅ payment summary
        var ps = paymentRead.summaryForOrder(order.getId(), order.getTotalPrice());

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("orderId", order.getId());
        header.put("orderDate", order.getOrderDate());
        header.put("orderStatus", statusName);
        header.put("orderStatusUi", titleCaseStatus(statusName));
        header.put("totalPrice", order.getTotalPrice());
        header.put("fullyPaid", ps.isFullyPaid());
        header.put("payment", paymentToMap(ps));
        header.put("orderCode", order.getOrderCode());
        header.put("orderSeq", order.getOrderSeq());
        
        List<Map<String, Object>> items = new ArrayList<>();
        if (order.getOrderItems() != null) {
            for (OrderItem oi : order.getOrderItems()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("orderItemId", oi.getId());
                row.put("quantity", oi.getQuantity());
                row.put("unitPrice", oi.getPrice());

                Object it = oi.getItem();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("itemId", it != null ? tryGet(it, "getId") : null);
                item.put("itemName", it != null ? tryGet(it, "getName", "getItemName", "getTitle") : null);
                item.put("imageUrl", it != null ? tryGet(it, "getImageUrl", "getImage", "getImagePath") : null);
                item.put("location", it != null ? tryGet(it, "getLocation", "getAddress", "getPlace") : null);
                item.put("startDatetime", it != null ? tryGet(it, "getStartDatetime", "getStartDateTime", "getStartAt") : null);

                row.put("item", item);
                items.add(row);
            }
        }

        return ResponseEntity.ok(Map.of(
                "order", header,
                "items", items,
                "itemsCount", items.size()
        ));
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
    public ResponseEntity<?> checkout(@RequestHeader("Authorization") String auth,
                                      @Valid @RequestBody CheckoutRequest request) {
        Long userId = jwt.extractId(strip(auth));

        // ✅ ignore client lines, use DB cart lines
        CheckoutSummaryResponse summary = orderService.checkoutFromCart(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(summary);
    }


    /* ----------------------------------- USER actions ------------------------------------ */

    @PutMapping("/cancel/{orderItemId}")
    @Operation(summary = "USER: Cancel my order item")
    public ResponseEntity<?> cancel(@RequestHeader("Authorization") String auth,
                                    @PathVariable Long orderItemId) {
        Long actorId = jwt.extractId(strip(auth));
        orderService.cancelorder(orderItemId, actorId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/pending/{orderItemId}")
    @Operation(summary = "USER: Reset my order item to PENDING")
    public ResponseEntity<?> resetToPending(@RequestHeader("Authorization") String auth,
                                            @PathVariable Long orderItemId) {
        Long actorId = jwt.extractId(strip(auth));
        orderService.resetToPending(orderItemId, actorId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/delete/{orderItemId}")
    @Operation(summary = "USER: Delete my order item")
    public ResponseEntity<?> delete(@RequestHeader("Authorization") String auth,
                                    @PathVariable Long orderItemId) {
        Long actorId = jwt.extractId(strip(auth));
        orderService.deleteorder(orderItemId, actorId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/refund/{orderItemId}")
    @Operation(summary = "USER: Refund my order item (if eligible)")
    public ResponseEntity<?> refund(@RequestHeader("Authorization") String auth,
                                    @PathVariable Long orderItemId) {
        Long actorId = jwt.extractId(strip(auth));
        orderService.refundIfEligible(orderItemId, actorId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/cancel/request/{orderItemId}")
    @Operation(summary = "USER: Request cancellation (status => CANCEL_REQUESTED)")
    public ResponseEntity<?> requestCancel(@RequestHeader("Authorization") String auth,
                                           @PathVariable Long orderItemId) {
        Long userId = jwt.extractId(strip(auth));
        orderService.requestCancel(orderItemId, userId);
        return ResponseEntity.ok().build();
    }
    
    
    @PostMapping("/checkout/quote")
    public ResponseEntity<?> quote(@RequestHeader("Authorization") String auth,
                                   @Valid @RequestBody CheckoutRequest request) {

        Long userId = jwt.extractId(strip(auth));

        CheckoutSummaryResponse quote = orderService.quoteCheckoutFromCart(userId, request);

        return ResponseEntity.ok(quote);
    }
    /**
     * GET /api/orders/myorders/last-shipping
     *
     * Purpose:
     * - Prefill the checkout shipping form using the user's most recent order.
     * - This helps the user avoid re-entering shipping details every time.
     *
     * What it returns (scalars only, no entities):
     * - countryId, countryName
     * - regionId, regionName
     * - city, postalCode
     * - address (addressLine)
     * - phone
     *
     * Why scalars only?
     * - shippingCountry / shippingRegion are often LAZY @ManyToOne entities (Country/Region).
     * - Returning entities can cause: "Could not initialize proxy ... no session".
     * - Therefore we extract id/name and return safe primitives.
     *
     * Response when no previous order exists:
     * - All fields are returned as null (client shows empty form).
     */
    @GetMapping("/myorders/last-shipping-address")
    @Operation(summary = "USER: Get last used shipping address for prefilling checkout")
    public ResponseEntity<?> myLastShippingAddress(@RequestHeader("Authorization") String auth) {

        // 1) Identify the current user from JWT
        Long userId = jwt.extractId(strip(auth));

        // 2) Load the most recent order (recommended: repository fetches shippingCountry/shippingRegion eagerly)
        Order last = orderRepo.findTopByUser_IdOrderByOrderDateDesc(userId).orElse(null);

        // 3) Build response map (NOT Map.of, because Map.of does not allow null values)
        Map<String, Object> res = new LinkedHashMap<>();

        // 4) No previous order => return all fields as null
        if (last == null) {
            res.put("countryId", null);
            res.put("countryName", null);
            res.put("regionId", null);
            res.put("regionName", null);
            res.put("city", null);
            res.put("postalCode", null);
            res.put("address", null);
            res.put("phone", null);
            res.put("fullName", null);
            return ResponseEntity.ok(res);
        }

        // 5) Extract scalars from LAZY entities safely (avoid serializing proxies)
        Object country = last.getShippingCountry(); // Country entity or null
        Object region  = last.getShippingRegion();  // Region entity or null

        // NOTE: tryGet(...) is your controller reflection helper (already defined above)
        res.put("countryId",   country == null ? null : tryGet(country, "getId"));
        res.put("countryName", country == null ? null : tryGet(country, "getName", "getTitle"));
        res.put("regionId",    region == null ? null : tryGet(region, "getId"));
        res.put("regionName",  region == null ? null : tryGet(region, "getName", "getTitle"));

        // 6) Simple string fields stored on order header
        res.put("city", last.getShippingCity());
        res.put("postalCode", last.getShippingPostalCode());
        res.put("address", last.getShippingAddress());
        res.put("phone", last.getShippingPhone());
        res.put("fullName", last.getShippingFullName());

        return ResponseEntity.ok(res);
    }

    /* =========================================================================================
       BUSINESS APIs (Merchant dashboards)
       ========================================================================================= */

    @GetMapping("/mybusinessorders")
    @Operation(summary = "BUSINESS: List orders for my business items")
    public ResponseEntity<?> myBusinessOrders(@RequestHeader("Authorization") String auth) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        var list = orderItemRepo.findRichByBusinessId(businessId)
                .stream().map(this::toDashboardOrderShape).toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/insights/orders")
    @Operation(summary = "BUSINESS: Lightweight insights rows")
    public ResponseEntity<?> insights(@RequestHeader("Authorization") String auth) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        var list = orderItemRepo.findRichByBusinessId(businessId)
                .stream().map(this::toInsightShape).toList();
        return ResponseEntity.ok(list);
    }

    @PutMapping("/cancel/approve/{orderItemId}")
    @Operation(summary = "BUSINESS: Approve cancellation request (CANCEL_REQUESTED => CANCELED)")
    public ResponseEntity<?> approveCancel(@RequestHeader("Authorization") String auth,
                                           @PathVariable Long orderItemId) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        orderService.approveCancel(orderItemId, businessId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/cancel/reject/{orderItemId}")
    @Operation(summary = "BUSINESS: Reject cancellation request (CANCEL_REQUESTED => PENDING)")
    public ResponseEntity<?> rejectCancel(@RequestHeader("Authorization") String auth,
                                          @PathVariable Long orderItemId) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        orderService.rejectCancel(orderItemId, businessId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/cancel/mark-refunded/{orderItemId}")
    @Operation(summary = "BUSINESS: Mark refunded (CANCELED => REFUNDED)")
    public ResponseEntity<?> markRefunded(@RequestHeader("Authorization") String auth,
                                          @PathVariable Long orderItemId) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        orderService.markRefunded(orderItemId, businessId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/mark-paid/{orderItemId}")
    @Operation(summary = "BUSINESS: Mark order paid (manual; useful for CASH)")
    public ResponseEntity<?> markPaid(@RequestHeader("Authorization") String auth,
                                      @PathVariable Long orderItemId) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        orderService.markPaid(orderItemId, businessId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/order/reject/{orderItemId}")
    @Operation(summary = "BUSINESS: Reject order (=> REJECTED)")
    public ResponseEntity<?> rejectOrder(@RequestHeader("Authorization") String auth,
                                         @PathVariable Long orderItemId) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        orderService.rejectorder(orderItemId, businessId);
        return ResponseEntity.ok().build();
    }

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
       ========================================================================================= */

    @GetMapping("/owner/orders")
    @Operation(summary = "OWNER: List all orders (headers) in my application with items")
    public ResponseEntity<?> ownerAllOrders(@RequestHeader("Authorization") String auth) {
        Long ownerProjectId = jwt.extractOwnerProjectId(strip(auth));

        var list = orderRepo.findAllByOwnerProjectIdWithItems(ownerProjectId);

        // ✅ NEW:
        // Batch compute payment summary for all returned orders (avoid N+1 queries).
        Map<Long, BigDecimal> totalsByOrderId = new HashMap<>();
        for (Order o : list) {
            totalsByOrderId.put(o.getId(), o.getTotalPrice());
        }
        Map<Long, OrderPaymentReadService.PaymentSummary> payByOrderId =
                paymentRead.summariesForOrders(totalsByOrderId);

        // For consistency with dashboards, we shape each order into a lightweight map:
        // - header fields
        // - itemsCount
        // - status name + UI case
        // ✅ NEW: also add payment fields (paidAmount, remainingAmount, fullyPaid, paymentState)
        var out = list.stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId());
            m.put("orderDate", o.getOrderDate());
            m.put("addressline", o.getShippingAddress());
            m.put("phone", o.getShippingPhone());
            m.put("totalPrice", o.getTotalPrice());
            String st = (o.getStatus() != null) ? o.getStatus().getName() : null;
            m.put("status", st);
            m.put("statusUi", titleCaseStatus(st));
            m.put("itemsCount", (o.getOrderItems() == null) ? 0 : o.getOrderItems().size());

            OrderPaymentReadService.PaymentSummary ps = payByOrderId.get(o.getId());
            m.put("fullyPaid", ps != null && ps.isFullyPaid());
            m.put("payment", paymentToMap(ps));
            m.put("orderCode", o.getOrderCode());
            m.put("orderSeq", o.getOrderSeq());

            return m;
        }).toList();

        return ResponseEntity.ok(out);
    }

    @GetMapping("/owner/orders/status/{status}")
    @Operation(summary = "OWNER: List orders in my application filtered by status (header list)")
    public ResponseEntity<?> ownerOrdersByStatus(@RequestHeader("Authorization") String auth,
                                                 @PathVariable String status) {
        Long ownerProjectId = jwt.extractOwnerProjectId(strip(auth));
        String normalized = (status == null) ? "" : status.trim().toUpperCase();

        var list = orderRepo.findAllByOwnerProjectIdWithItemsAndStatuses(ownerProjectId, List.of(normalized));

        // ✅ NEW: batch payment summary
        Map<Long, BigDecimal> totalsByOrderId = new HashMap<>();
        for (Order o : list) totalsByOrderId.put(o.getId(), o.getTotalPrice());
        Map<Long, OrderPaymentReadService.PaymentSummary> payByOrderId =
                paymentRead.summariesForOrders(totalsByOrderId);

        var out = list.stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId());
            m.put("orderDate", o.getOrderDate());
            m.put("totalPrice", o.getTotalPrice());
            m.put("addressline", o.getShippingAddress());
            m.put("phone", o.getShippingPhone());
            String st = (o.getStatus() != null) ? o.getStatus().getName() : null;
            m.put("status", st);
            m.put("statusUi", titleCaseStatus(st));
            m.put("itemsCount", (o.getOrderItems() == null) ? 0 : o.getOrderItems().size());

            OrderPaymentReadService.PaymentSummary ps = payByOrderId.get(o.getId());
            m.put("fullyPaid", ps != null && ps.isFullyPaid());
            m.put("payment", paymentToMap(ps));

            return m;
        }).toList();

        return ResponseEntity.ok(out);
    }

    @GetMapping("/owner/orders/status")
    @Operation(summary = "OWNER: List orders in my application filtered by multiple statuses (header list)")
    public ResponseEntity<?> ownerOrdersByStatuses(@RequestHeader("Authorization") String auth,
                                                   @RequestParam(name = "statuses") List<String> statuses) {
        Long ownerProjectId = jwt.extractOwnerProjectId(strip(auth));

        // ✅ NEW: robust parsing (supports comma-separated and repeated params)
        List<String> normalized = normalizeStatuses(statuses);

        var list = orderRepo.findAllByOwnerProjectIdWithItemsAndStatuses(ownerProjectId, normalized);

        // ✅ NEW: batch payment summary
        Map<Long, BigDecimal> totalsByOrderId = new HashMap<>();
        for (Order o : list) totalsByOrderId.put(o.getId(), o.getTotalPrice());
        Map<Long, OrderPaymentReadService.PaymentSummary> payByOrderId =
                paymentRead.summariesForOrders(totalsByOrderId);

        var out = list.stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId());
            m.put("orderDate", o.getOrderDate());
            m.put("totalPrice", o.getTotalPrice());
            m.put("addressline", o.getShippingAddress());
            m.put("phone", o.getShippingPhone());
            String st = (o.getStatus() != null) ? o.getStatus().getName() : null;
            m.put("status", st);
            m.put("statusUi", titleCaseStatus(st));
            m.put("itemsCount", (o.getOrderItems() == null) ? 0 : o.getOrderItems().size());

            OrderPaymentReadService.PaymentSummary ps = payByOrderId.get(o.getId());
            m.put("fullyPaid", ps != null && ps.isFullyPaid());
            m.put("payment", paymentToMap(ps));

            return m;
        }).toList();

        return ResponseEntity.ok(out);
    }

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

    @PutMapping("/owner/orders/{orderId}/status")
    @Operation(summary = "OWNER: Update order header status (FK)")
    public ResponseEntity<?> ownerUpdateOrderStatus(@RequestHeader("Authorization") String auth,
                                                    @PathVariable Long orderId,
                                                    @RequestBody Map<String, Object> body) {
        Long ownerProjectId = jwt.extractOwnerProjectId(strip(auth));

        // Tenant isolation check
        assertOwnerCanAccessOrder(orderId, ownerProjectId);

        String statusCode = (body == null) ? null : String.valueOf(body.get("status"));
        
        
       
        String normalized = (statusCode == null) ? "" : statusCode.trim().toUpperCase(Locale.ROOT);

        // ✅ Special behavior for REJECTED: rollback coupon + stock
        if ("REJECTED".equals(normalized)) {
            orderService.ownerRejectOrder(orderId, ownerProjectId, "OWNER_REJECTED");
            return ResponseEntity.ok(Map.of(
                    "orderId", orderId,
                    "status", "REJECTED",
                    "statusUi", titleCaseStatus("REJECTED")
            ));
        }

        OrderStatus newStatus = requireStatusByName(statusCode);

        // Load order with items (optional, but safe)
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));

        /**
         * ✅ NEW (optional safety):
         * If the owner sets COMPLETED, we can enforce "fully paid" based on ledger.
         *
         * Why:
         * - Prevents a common bug: status updated but no money received.
         *
         * If you want to allow "cash on delivery" completion without online payment,
         * then in markPaid(...) you should create a PAID payment transaction (ledger),
         * and this rule will still pass.
         *
         * If you do NOT want to enforce this rule, comment the block below.
         */
        if ("COMPLETED".equalsIgnoreCase(newStatus.getName())) {
            OrderPaymentReadService.PaymentSummary ps =
                    paymentRead.summaryForOrder(order.getId(), order.getTotalPrice());
            if (!ps.isFullyPaid()) {
                throw new IllegalArgumentException(
                        "Cannot set status COMPLETED لأن الطلب غير مدفوع بالكامل. " +
                                "paid=" + ps.getPaidAmount() + " total=" + ps.getOrderTotal()
                );
            }
        }

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
    @PutMapping("/owner/orders/{orderId}/cash/mark-paid")
    @Operation(summary = "OWNER: Mark CASH payment as PAID (money collected)")
    public ResponseEntity<?> ownerMarkCashPaid(@RequestHeader("Authorization") String auth,
                                               @PathVariable Long orderId) {

        Long ownerProjectId = jwt.extractOwnerProjectId(strip(auth));

        // tenant isolation
        assertOwnerCanAccessOrder(orderId, ownerProjectId);

        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));

        var tx = paymentWrite.markCashAsPaid(orderId, order.getTotalPrice());

        return ResponseEntity.ok(Map.of(
                "orderId", orderId,
                "provider", tx.getProviderCode(),
                "status", tx.getStatus(),
                "amount", tx.getAmount(),
                "currency", tx.getCurrency()
        ));
    }
    /* =========================================================================================
       SUPER_ADMIN APIs (Engine-level)
       ========================================================================================= */

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

    @GetMapping("/superadmin/applications/{ownerProjectId}/orders")
    @Operation(summary = "SUPER_ADMIN: List all orders for a specific application")
    public ResponseEntity<?> superAdminOrdersByApplication(@RequestHeader("Authorization") String auth,
                                                           @PathVariable Long ownerProjectId) {

        var list = orderRepo.findAllByOwnerProjectIdWithItems(ownerProjectId);

        // ✅ NEW:
        // Add payment summary here too (optional but very useful for Super Admin dashboards).
        Map<Long, BigDecimal> totalsByOrderId = new HashMap<>();
        for (Order o : list) totalsByOrderId.put(o.getId(), o.getTotalPrice());
        Map<Long, OrderPaymentReadService.PaymentSummary> payByOrderId =
                paymentRead.summariesForOrders(totalsByOrderId);

        var out = list.stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId());
            m.put("orderDate", o.getOrderDate());
            m.put("totalPrice", o.getTotalPrice());
            String st = (o.getStatus() != null) ? o.getStatus().getName() : null;
            m.put("status", st);
            m.put("statusUi", titleCaseStatus(st));
            m.put("itemsCount", (o.getOrderItems() == null) ? 0 : o.getOrderItems().size());

            OrderPaymentReadService.PaymentSummary ps = payByOrderId.get(o.getId());
            m.put("fullyPaid", ps != null && ps.isFullyPaid());
            m.put("payment", paymentToMap(ps));

            return m;
        }).toList();

        return ResponseEntity.ok(out);
    }

    @GetMapping("/superadmin/applications/{ownerProjectId}/orders/status/{status}")
    @Operation(summary = "SUPER_ADMIN: List orders for a specific application filtered by status")
    public ResponseEntity<?> superAdminOrdersByApplicationAndStatus(@RequestHeader("Authorization") String auth,
                                                                    @PathVariable Long ownerProjectId,
                                                                    @PathVariable String status) {
        String normalized = (status == null) ? "" : status.trim().toUpperCase();

        var list = orderRepo.findAllByOwnerProjectIdWithItemsAndStatuses(ownerProjectId, List.of(normalized));

        // ✅ NEW: batch payment summary
        Map<Long, BigDecimal> totalsByOrderId = new HashMap<>();
        for (Order o : list) totalsByOrderId.put(o.getId(), o.getTotalPrice());
        Map<Long, OrderPaymentReadService.PaymentSummary> payByOrderId =
                paymentRead.summariesForOrders(totalsByOrderId);

        var out = list.stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", o.getId());
            m.put("orderDate", o.getOrderDate());
            m.put("totalPrice", o.getTotalPrice());
            String st = (o.getStatus() != null) ? o.getStatus().getName() : null;
            m.put("status", st);
            m.put("statusUi", titleCaseStatus(st));
            m.put("itemsCount", (o.getOrderItems() == null) ? 0 : o.getOrderItems().size());

            OrderPaymentReadService.PaymentSummary ps = payByOrderId.get(o.getId());
            m.put("fullyPaid", ps != null && ps.isFullyPaid());
            m.put("payment", paymentToMap(ps));

            return m;
        }).toList();

        return ResponseEntity.ok(out);
    }

    /* =========================================================================================
       ERROR HANDLERS (keep same behavior)
       ========================================================================================= */

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<?> notFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> serverError(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", ex.getMessage()));
    }
    
    private Long resolveOwnerProjectIdFromOrder(Order order) {
        if (order == null || order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            throw new IllegalStateException("Order has no items; cannot resolve ownerProjectId");
        }

        Long ownerProjectId = null;

        for (OrderItem oi : order.getOrderItems()) {
            if (oi == null || oi.getItem() == null || oi.getItem().getOwnerProject() == null) continue;

            Long opId = oi.getItem().getOwnerProject().getId();
            if (opId == null) continue;

            if (ownerProjectId == null) ownerProjectId = opId;
            else if (!ownerProjectId.equals(opId)) {
                throw new IllegalStateException("Order contains items from multiple ownerProjects (tenant mix) - invalid");
            }
        }

        if (ownerProjectId == null) {
            throw new IllegalStateException("Could not resolve ownerProjectId from order items");
        }

        return ownerProjectId;
    }

    private boolean isCashMethod(Order order) {
        if (order == null || order.getPaymentMethod() == null) return false;
        Object codeOrName = tryGet(order.getPaymentMethod(), "getCode", "getName");
        if (codeOrName == null) return false;
        return "CASH".equalsIgnoreCase(String.valueOf(codeOrName));
    }

}
