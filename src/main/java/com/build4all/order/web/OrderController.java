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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * OrderController
 *
 * Security model:
 * - USER: own orders only
 * - BUSINESS: orders for items owned by business
 * - OWNER: all orders in tenant (ownerProjectId from JWT)
 * - SUPER_ADMIN: cross-tenant tools (explicit path params allowed)
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final com.build4all.order.service.OrderService orderService;
    private final JwtUtil jwt;
    private final OrderItemRepository orderItemRepo;
    private final OrderRepository orderRepo;
    private final OrderStatusRepository statusRepo;
    private final OrderPaymentReadService paymentRead;
    private final OrderPaymentWriteService paymentWrite;
    private final OwnerSubscriptionGuard ownerSubscriptionGuard;

    public OrderController(
            com.build4all.order.service.OrderService orderService,
            JwtUtil jwt,
            OrderItemRepository orderItemRepo,
            OrderRepository orderRepo,
            OrderStatusRepository statusRepo,
            OrderPaymentReadService paymentRead,
            OrderPaymentWriteService paymentWrite,
            OwnerSubscriptionGuard ownerSubscriptionGuard
    ) {
        this.orderService = orderService;
        this.jwt = jwt;
        this.orderItemRepo = orderItemRepo;
        this.orderRepo = orderRepo;
        this.statusRepo = statusRepo;
        this.paymentRead = paymentRead;
        this.paymentWrite = paymentWrite;
        this.ownerSubscriptionGuard = ownerSubscriptionGuard;
    }

    /* -------------------------------- helpers -------------------------------- */

    private String strip(String auth) {
        if (auth == null) return "";
        return auth.replaceFirst("(?i)^Bearer\\s+", "").trim();
    }

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

    private static String titleCaseStatus(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toUpperCase(Locale.ROOT);
        return switch (s) {
            case "PENDING" -> "Pending";
            case "COMPLETED" -> "Completed";
            case "CANCELED" -> "Canceled";
            case "CANCEL_REQUESTED" -> "CancelRequested";
            case "REJECTED" -> "Rejected";
            case "REFUNDED" -> "Refunded";
            default -> s.isEmpty() ? "" : s.charAt(0) + s.substring(1).toLowerCase(Locale.ROOT);
        };
    }

    private static List<String> normalizeStatuses(List<String> statuses) {
        if (statuses == null) return List.of();
        List<String> out = new ArrayList<>();
        for (String s : statuses) {
            if (s == null) continue;
            for (String p : s.split(",")) {
                if (p == null) continue;
                String x = p.trim();
                if (!x.isBlank()) out.add(x.toUpperCase(Locale.ROOT));
            }
        }
        return out;
    }

    private static Map<String, Object> paymentToMap(OrderPaymentReadService.PaymentSummary ps) {
        if (ps == null) {
            Map<String, Object> z = new LinkedHashMap<>();
            z.put("orderTotal", BigDecimal.ZERO);
            z.put("paidAmount", BigDecimal.ZERO);
            z.put("remainingAmount", BigDecimal.ZERO);
            z.put("fullyPaid", false);
            z.put("paymentState", "UNPAID");
            return z;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderTotal", ps.getOrderTotal());
        m.put("paidAmount", ps.getPaidAmount());
        m.put("remainingAmount", ps.getRemainingAmount());
        m.put("fullyPaid", ps.isFullyPaid());
        m.put("paymentState", ps.getPaymentState());
        return m;
    }

    private Map<String, Object> toUserCardShape(Map<String, Object> row) {
        Map<String, Object> out = new HashMap<>(row);

        Map<String, Object> item = new HashMap<>();
        item.put("itemName", out.remove("itemName"));
        item.put("location", out.remove("location"));
        item.put("startDatetime", out.remove("startDatetime"));
        item.put("imageUrl", out.remove("imageUrl"));
        out.put("item", item);

        Map<String, Object> order = new HashMap<>();
        order.put("status", out.get("orderStatus"));
        out.put("order", order);

        Object os = out.get("orderStatus");
        out.put("orderStatus", titleCaseStatus(os == null ? null : os.toString()));

        return out;
    }

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

        Map<String, Object> user = null;
        if (u != null) {
            user = new HashMap<>();
            user.put("id", tryGet(u, "getId"));
            user.put("username", tryGet(u, "getUsername"));
            user.put("firstName", tryGet(u, "getFirstName"));
            user.put("lastName", tryGet(u, "getLastName"));
            user.put("profilePictureUrl", tryGet(u, "getProfilePictureUrl", "getAvatarUrl", "getPhotoUrl"));
        }

        double totalPrice = 0.0;
        Object oTot = (o != null) ? tryGet(o, "getTotalPrice") : null;
        if (oTot instanceof BigDecimal bd) totalPrice = bd.doubleValue();
        else if (oi.getPrice() != null) totalPrice = oi.getPrice().multiply(BigDecimal.valueOf(oi.getQuantity())).doubleValue();

        String statusName = (o != null && o.getStatus() != null) ? o.getStatus().getName() : null;
        String orderStatus = titleCaseStatus(statusName);

        // legacy (fast) paid hint
        boolean wasPaid = "COMPLETED".equalsIgnoreCase(statusName);

        Object paymentMethod = null;
        if (o != null && o.getPaymentMethod() != null) {
            Object codeOrName = tryGet(o.getPaymentMethod(), "getCode", "getName");
            paymentMethod = (codeOrName != null) ? codeOrName : tryGet(o.getPaymentMethod(), "getId");
        }

        Object ownerProjectId = (i != null) ? tryGet(tryGet(i, "getOwnerProject"), "getId") : null;

        Map<String, Object> out = new HashMap<>();
        out.put("id", oi.getId());
        out.put("orderId", (o != null) ? o.getId() : null);
        out.put("orderStatus", orderStatus);
        out.put("rawStatus", statusName);
        out.put("wasPaid", wasPaid);
        out.put("quantity", oi.getQuantity());
        out.put("totalPrice", totalPrice);
        out.put("paymentMethod", paymentMethod);
        out.put("orderDatetime", (o != null) ? tryGet(o, "getOrderDate", "getCreatedAt") : oi.getCreatedAt());
        out.put("currency", currency);
        out.put("item", item);
        out.put("user", user);
        out.put("ownerProjectId", ownerProjectId);
        return out;
    }

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

    private void assertOwnerCanAccessOrder(Long orderId, Long ownerProjectId) {
        boolean ok = orderItemRepo.existsByOrder_IdAndItem_OwnerProject_Id(orderId, ownerProjectId);
        if (!ok) throw new NoSuchElementException("Order not found");
    }

    private OrderStatus requireStatusByName(String statusCode) {
        if (statusCode == null || statusCode.isBlank()) throw new IllegalArgumentException("status is required");
        return statusRepo.findByNameIgnoreCase(statusCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("Unknown status: " + statusCode));
    }

    private Map<String, Object> toOwnerOrderDetailsResponse(Order order) {
        Map<String, Object> out = new HashMap<>();

        Map<String, Object> header = new HashMap<>();
        header.put("id", order.getId());
        header.put("orderDate", order.getOrderDate());
        header.put("totalPrice", order.getTotalPrice());
        header.put("shippingFullName", order.getShippingFullName());
        header.put("customerName", order.getShippingFullName());

        String statusName = (order.getStatus() != null) ? order.getStatus().getName() : null;
        header.put("status", statusName);
        header.put("statusUi", titleCaseStatus(statusName));

        Object paymentMethod = null;
        if (order.getPaymentMethod() != null) {
            Object codeOrName = tryGet(order.getPaymentMethod(), "getCode", "getName");
            paymentMethod = (codeOrName != null) ? codeOrName : tryGet(order.getPaymentMethod(), "getId");
        }
        header.put("paymentMethod", paymentMethod);

        if (order.getCurrency() != null) {
            Map<String, Object> currency = new HashMap<>();
            currency.put("code", tryGet(order.getCurrency(), "getCode"));
            currency.put("symbol", tryGet(order.getCurrency(), "getSymbol"));
            header.put("currency", currency);
        } else {
            header.put("currency", null);
        }

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

        OrderPaymentReadService.PaymentSummary ps =
                paymentRead.summaryForOrder(order.getId(), order.getTotalPrice());
        header.put("fullyPaid", ps.isFullyPaid());
        header.put("payment", paymentToMap(ps));

        out.put("order", header);

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
       USER APIs
       ========================================================================================= */

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/myorders")
    public ResponseEntity<?> myOrders(@RequestHeader("Authorization") String auth) {
        Long userId = jwt.extractId(strip(auth));

        var cards = orderRepo.findUserOrderCardsGrouped(userId);

        Map<Long, BigDecimal> totals = new HashMap<>();
        for (var c : cards) {
            Long orderId = ((Number) c.get("orderId")).longValue();
            BigDecimal total = (BigDecimal) c.getOrDefault("totalPrice", BigDecimal.ZERO);
            totals.put(orderId, total);
        }

        var payByOrderId = paymentRead.summariesForOrders(totals);

        for (var c : cards) {
            Long orderId = ((Number) c.get("orderId")).longValue();
            var ps = payByOrderId.get(orderId);
            c.put("payment", paymentToMap(ps));
            c.put("fullyPaid", ps != null && ps.isFullyPaid());

            Object st = c.get("orderStatus");
            c.put("orderStatusUi", titleCaseStatus(st == null ? null : String.valueOf(st)));
        }

        return ResponseEntity.ok(cards);
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/myorders/pending")
    public ResponseEntity<?> myPending(@RequestHeader("Authorization") String auth) {
        Long userId = jwt.extractId(strip(auth));
        var cards = orderRepo.findUserOrderCardsGroupedByStatuses(userId, List.of("PENDING", "CANCEL_REQUESTED"));
        return ResponseEntity.ok(cards);
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/myorders/completed")
    @Operation(summary = "USER: List my completed orders")
    public ResponseEntity<?> myCompleted(@RequestHeader("Authorization") String auth) {
        Long userId = jwt.extractId(strip(auth));
        var rows = orderItemRepo.findUserOrderCardsByStatuses(userId, List.of("COMPLETED"));
        return ResponseEntity.ok(rows.stream().map(this::toUserCardShape).toList());
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/myorders/canceled")
    @Operation(summary = "USER: List my canceled orders")
    public ResponseEntity<?> myCanceled(@RequestHeader("Authorization") String auth) {
        Long userId = jwt.extractId(strip(auth));
        var rows = orderItemRepo.findUserOrderCardsByStatuses(userId, List.of("CANCELED"));
        return ResponseEntity.ok(rows.stream().map(this::toUserCardShape).toList());
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/myorders/{orderId}")
    public ResponseEntity<?> myOrderDetails(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long orderId
    ) {
        Long userId = jwt.extractId(strip(auth));

        Order order = orderRepo.findByIdAndUserIdWithItems(orderId, userId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));

        String statusName = (order.getStatus() != null) ? order.getStatus().getName() : null;
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

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("order", header);
        res.put("items", items);
        res.put("itemsCount", items.size());
        return ResponseEntity.ok(res);
    }

    /* --------------------------- checkout + quote (USER write) --------------------------- */

    @PreAuthorize("hasRole('USER')")
    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(
            @RequestHeader("Authorization") String auth,
            @Valid @RequestBody CheckoutRequest request
    ) {
        Long userId = jwt.extractId(strip(auth));

        // tenant from token (header) — not from client
        Long ownerProjectId = jwt.requireOwnerProjectId(auth);

        ResponseEntity<?> blocked = ownerSubscriptionGuard.blockIfWriteNotAllowed(ownerProjectId);
        if (blocked != null) return blocked;

        CheckoutSummaryResponse summary = orderService.checkoutFromCart(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(summary);
    }

    @PreAuthorize("hasRole('USER')")
    @PostMapping("/checkout/quote")
    public ResponseEntity<?> quote(
            @RequestHeader("Authorization") String auth,
            @Valid @RequestBody CheckoutRequest request
    ) {
        Long userId = jwt.extractId(strip(auth));

        Long ownerProjectId = jwt.requireOwnerProjectId(auth);

        // Quote may be "read-ish" لكن realistically it hits logic that can be blocked by plan too
        ResponseEntity<?> blocked = ownerSubscriptionGuard.blockIfWriteNotAllowed(ownerProjectId);
        if (blocked != null) return blocked;

        CheckoutSummaryResponse quote = orderService.quoteCheckoutFromCart(userId, request);
        return ResponseEntity.ok(quote);
    }

    /* --------------------------- USER actions --------------------------- */

    @PreAuthorize("hasRole('USER')")
    @PutMapping("/cancel/{orderItemId}")
    public ResponseEntity<?> cancel(@RequestHeader("Authorization") String auth, @PathVariable Long orderItemId) {
        Long actorId = jwt.extractId(strip(auth));
        orderService.cancelorder(orderItemId, actorId);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('USER')")
    @PutMapping("/pending/{orderItemId}")
    public ResponseEntity<?> resetToPending(@RequestHeader("Authorization") String auth, @PathVariable Long orderItemId) {
        Long actorId = jwt.extractId(strip(auth));
        orderService.resetToPending(orderItemId, actorId);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('USER')")
    @DeleteMapping("/delete/{orderItemId}")
    public ResponseEntity<?> delete(@RequestHeader("Authorization") String auth, @PathVariable Long orderItemId) {
        Long actorId = jwt.extractId(strip(auth));
        orderService.deleteorder(orderItemId, actorId);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('USER')")
    @PutMapping("/refund/{orderItemId}")
    public ResponseEntity<?> refund(@RequestHeader("Authorization") String auth, @PathVariable Long orderItemId) {
        Long actorId = jwt.extractId(strip(auth));
        orderService.refundIfEligible(orderItemId, actorId);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('USER')")
    @PutMapping("/cancel/request/{orderItemId}")
    public ResponseEntity<?> requestCancel(@RequestHeader("Authorization") String auth, @PathVariable Long orderItemId) {
        Long userId = jwt.extractId(strip(auth));
        orderService.requestCancel(orderItemId, userId);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/myorders/last-shipping-address")
    public ResponseEntity<?> myLastShippingAddress(@RequestHeader("Authorization") String auth) {
        Long userId = jwt.extractId(strip(auth));
        Order last = orderRepo.findTopByUser_IdOrderByOrderDateDesc(userId).orElse(null);

        Map<String, Object> res = new LinkedHashMap<>();

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

        Object country = last.getShippingCountry();
        Object region = last.getShippingRegion();

        res.put("countryId", country == null ? null : tryGet(country, "getId"));
        res.put("countryName", country == null ? null : tryGet(country, "getName", "getTitle"));
        res.put("regionId", region == null ? null : tryGet(region, "getId"));
        res.put("regionName", region == null ? null : tryGet(region, "getName", "getTitle"));

        res.put("city", last.getShippingCity());
        res.put("postalCode", last.getShippingPostalCode());
        res.put("address", last.getShippingAddress());
        res.put("phone", last.getShippingPhone());
        res.put("fullName", last.getShippingFullName());

        return ResponseEntity.ok(res);
    }

    /* =========================================================================================
       BUSINESS APIs
       ========================================================================================= */

    @PreAuthorize("hasRole('BUSINESS')")
    @GetMapping("/mybusinessorders")
    public ResponseEntity<?> myBusinessOrders(@RequestHeader("Authorization") String auth) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        var list = orderItemRepo.findRichByBusinessId(businessId)
                .stream().map(this::toDashboardOrderShape).toList();
        return ResponseEntity.ok(list);
    }

    @PreAuthorize("hasRole('BUSINESS')")
    @GetMapping("/insights/orders")
    public ResponseEntity<?> insights(@RequestHeader("Authorization") String auth) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        var list = orderItemRepo.findRichByBusinessId(businessId)
                .stream().map(this::toInsightShape).toList();
        return ResponseEntity.ok(list);
    }

    @PreAuthorize("hasRole('BUSINESS')")
    @PutMapping("/cancel/approve/{orderItemId}")
    public ResponseEntity<?> approveCancel(@RequestHeader("Authorization") String auth, @PathVariable Long orderItemId) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        orderService.approveCancel(orderItemId, businessId);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('BUSINESS')")
    @PutMapping("/cancel/reject/{orderItemId}")
    public ResponseEntity<?> rejectCancel(@RequestHeader("Authorization") String auth, @PathVariable Long orderItemId) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        orderService.rejectCancel(orderItemId, businessId);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('BUSINESS')")
    @PutMapping("/cancel/mark-refunded/{orderItemId}")
    public ResponseEntity<?> markRefunded(@RequestHeader("Authorization") String auth, @PathVariable Long orderItemId) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        orderService.markRefunded(orderItemId, businessId);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('BUSINESS')")
    @PutMapping("/mark-paid/{orderItemId}")
    public ResponseEntity<?> markPaid(@RequestHeader("Authorization") String auth, @PathVariable Long orderItemId) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        orderService.markPaid(orderItemId, businessId);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('BUSINESS')")
    @PutMapping("/order/reject/{orderItemId}")
    public ResponseEntity<?> rejectOrder(@RequestHeader("Authorization") String auth, @PathVariable Long orderItemId) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        orderService.rejectorder(orderItemId, businessId);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('BUSINESS')")
    @PutMapping("/order/unreject/{orderItemId}")
    public ResponseEntity<?> unrejectOrder(@RequestHeader("Authorization") String auth, @PathVariable Long orderItemId) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        orderService.unrejectorder(orderItemId, businessId);
        return ResponseEntity.ok().build();
    }

    /* =========================================================================================
       OWNER APIs (tenant from token)
       ========================================================================================= */

    @PreAuthorize("hasRole('OWNER')")
    @GetMapping("/owner/orders")
    public ResponseEntity<?> ownerAllOrders(@RequestHeader("Authorization") String auth) {
        Long ownerProjectId = jwt.requireOwnerProjectId(auth);

        var list = orderRepo.findAllByOwnerProjectIdWithItems(ownerProjectId);

        Map<Long, BigDecimal> totalsByOrderId = new HashMap<>();
        for (Order o : list) totalsByOrderId.put(o.getId(), o.getTotalPrice());

        Map<Long, OrderPaymentReadService.PaymentSummary> payByOrderId =
                paymentRead.summariesForOrders(totalsByOrderId);

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
            m.put("fullName", o.getShippingFullName());
            m.put("shippingFullName", o.getShippingFullName());
            m.put("orderCode", o.getOrderCode());
            m.put("orderSeq", o.getOrderSeq());

            OrderPaymentReadService.PaymentSummary ps = payByOrderId.get(o.getId());
            m.put("fullyPaid", ps != null && ps.isFullyPaid());
            m.put("payment", paymentToMap(ps));
            return m;
        }).toList();

        return ResponseEntity.ok(out);
    }

    @PreAuthorize("hasRole('OWNER')")
    @GetMapping("/owner/orders/status/{status}")
    public ResponseEntity<?> ownerOrdersByStatus(
            @RequestHeader("Authorization") String auth,
            @PathVariable String status
    ) {
        Long ownerProjectId = jwt.requireOwnerProjectId(auth);
        String normalized = (status == null) ? "" : status.trim().toUpperCase(Locale.ROOT);

        var list = orderRepo.findAllByOwnerProjectIdWithItemsAndStatuses(ownerProjectId, List.of(normalized));

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
            m.put("fullName", o.getShippingFullName());
            m.put("shippingFullName", o.getShippingFullName());

            OrderPaymentReadService.PaymentSummary ps = payByOrderId.get(o.getId());
            m.put("fullyPaid", ps != null && ps.isFullyPaid());
            m.put("payment", paymentToMap(ps));
            return m;
        }).toList();

        return ResponseEntity.ok(out);
    }

    @PreAuthorize("hasRole('OWNER')")
    @GetMapping("/owner/orders/status")
    public ResponseEntity<?> ownerOrdersByStatuses(
            @RequestHeader("Authorization") String auth,
            @RequestParam(name = "statuses") List<String> statuses
    ) {
        Long ownerProjectId = jwt.requireOwnerProjectId(auth);

        List<String> normalized = normalizeStatuses(statuses);
        var list = orderRepo.findAllByOwnerProjectIdWithItemsAndStatuses(ownerProjectId, normalized);

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
            m.put("fullName", o.getShippingFullName());
            m.put("shippingFullName", o.getShippingFullName());

            OrderPaymentReadService.PaymentSummary ps = payByOrderId.get(o.getId());
            m.put("fullyPaid", ps != null && ps.isFullyPaid());
            m.put("payment", paymentToMap(ps));
            return m;
        }).toList();

        return ResponseEntity.ok(out);
    }

    @PreAuthorize("hasRole('OWNER')")
    @GetMapping("/owner/orders/{orderId}")
    public ResponseEntity<?> ownerOrderDetails(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long orderId
    ) {
        Long ownerProjectId = jwt.requireOwnerProjectId(auth);

        assertOwnerCanAccessOrder(orderId, ownerProjectId);

        Order order = orderRepo.findByIdWithItems(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));

        return ResponseEntity.ok(toOwnerOrderDetailsResponse(order));
    }

    @PreAuthorize("hasRole('OWNER')")
    @PutMapping("/owner/orders/{orderId}/status")
    public ResponseEntity<?> ownerUpdateOrderStatus(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long orderId,
            @RequestBody Map<String, Object> body
    ) {
        Long ownerProjectId = jwt.requireOwnerProjectId(auth);
        assertOwnerCanAccessOrder(orderId, ownerProjectId);

        String statusCode = (body == null) ? null : String.valueOf(body.get("status"));
        String normalized = (statusCode == null) ? "" : statusCode.trim().toUpperCase(Locale.ROOT);

        if ("REJECTED".equals(normalized)) {
            orderService.ownerRejectOrder(orderId, ownerProjectId, "OWNER_REJECTED");
            Map<String, Object> r = new HashMap<>();
            r.put("orderId", orderId);
            r.put("status", "REJECTED");
            r.put("statusUi", titleCaseStatus("REJECTED"));
            return ResponseEntity.ok(r);
        }

        OrderStatus newStatus = requireStatusByName(statusCode);

        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));

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

        order.setStatus(newStatus);
        order.setOrderDate(LocalDateTime.now());
        orderRepo.save(order);

        Map<String, Object> r = new HashMap<>();
        r.put("orderId", order.getId());
        r.put("status", newStatus.getName());
        r.put("statusUi", titleCaseStatus(newStatus.getName()));
        return ResponseEntity.ok(r);
    }

   
    @PreAuthorize("hasRole('OWNER')")
    @PutMapping("/owner/orders/{orderId}/cash/mark-paid")
    public ResponseEntity<?> ownerMarkCashPaid(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long orderId
    ) {
        Long ownerProjectId = jwt.requireOwnerProjectId(auth);
        assertOwnerCanAccessOrder(orderId, ownerProjectId);

        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));

        // 1) Mark cash tx as PAID (ledger)
        var tx = paymentWrite.markCashAsPaid(orderId, order.getTotalPrice());

        // 2) Recompute payment summary (should be fully paid now)
        OrderPaymentReadService.PaymentSummary ps =
                paymentRead.summaryForOrder(order.getId(), order.getTotalPrice());

        // 3) If fully paid => auto COMPLETE
        if (ps != null && ps.isFullyPaid()) {

            // optional: don't complete if already canceled/rejected/refunded
            String current = (order.getStatus() != null) ? order.getStatus().getName() : "";
            String cur = (current == null) ? "" : current.trim().toUpperCase(Locale.ROOT);

            if (!List.of("CANCELED", "REJECTED", "REFUNDED").contains(cur)) {
                OrderStatus completed = requireStatusByName("COMPLETED");
                order.setStatus(completed);
                order.setOrderDate(LocalDateTime.now());
                orderRepo.save(order);
            }
        }

        // 4) Return richer response so frontend updates instantly
        Map<String, Object> r = new HashMap<>();
        r.put("orderId", orderId);
        r.put("provider", tx.getProviderCode());
        r.put("txStatus", tx.getStatus());
        r.put("amount", tx.getAmount());
        r.put("currency", tx.getCurrency());

        String st = (order.getStatus() != null) ? order.getStatus().getName() : null;
        r.put("status", st);
        r.put("statusUi", titleCaseStatus(st));

        r.put("fullyPaid", ps != null && ps.isFullyPaid());
        r.put("payment", paymentToMap(ps));

        return ResponseEntity.ok(r);
    }
    /* =========================================================================================
       SUPER_ADMIN APIs
       ========================================================================================= */

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/superadmin/applications")
    public ResponseEntity<?> superAdminApplicationsOrdersCount() {
        var rows = orderRepo.countOrdersGroupedByOwnerProject();
        var out = rows.stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("ownerProjectId", r[0]);
            m.put("ordersCount", r[1]);
            return m;
        }).toList();
        return ResponseEntity.ok(out);
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/superadmin/applications/{ownerProjectId}/orders")
    public ResponseEntity<?> superAdminOrdersByApplication(@PathVariable Long ownerProjectId) {

        var list = orderRepo.findAllByOwnerProjectIdWithItems(ownerProjectId);

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

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/superadmin/applications/{ownerProjectId}/orders/status/{status}")
    public ResponseEntity<?> superAdminOrdersByApplicationAndStatus(
            @PathVariable Long ownerProjectId,
            @PathVariable String status
    ) {
        String normalized = (status == null) ? "" : status.trim().toUpperCase(Locale.ROOT);

        var list = orderRepo.findAllByOwnerProjectIdWithItemsAndStatuses(ownerProjectId, List.of(normalized));

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
    
    
    
    @PreAuthorize("hasRole('OWNER')")
    @PutMapping("/owner/orders/{orderId}/cash/reset-to-unpaid")
    public ResponseEntity<?> ownerResetCashToUnpaid(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long orderId
    ) {
        Long ownerProjectId = jwt.requireOwnerProjectId(auth);
        assertOwnerCanAccessOrder(orderId, ownerProjectId);

        // This will flip CASH txs from PAID -> OFFLINE_PENDING (or whatever you choose)
        int changed = paymentWrite.resetCashToUnpaid(orderId);

        // Return fresh payment summary so frontend updates instantly
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));

        var ps = paymentRead.summaryForOrder(order.getId(), order.getTotalPrice());

        Map<String, Object> r = new HashMap<>();
        r.put("orderId", orderId);
        r.put("changedTransactions", changed);
        r.put("fullyPaid", ps.isFullyPaid());
        r.put("payment", paymentToMap(ps));
        return ResponseEntity.ok(r);
    }

    @PreAuthorize("hasRole('OWNER')")
    @PutMapping("/owner/orders/{orderId}/reopen")
    public ResponseEntity<?> ownerReopenOrder(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long orderId
    ) {
        Long ownerProjectId = jwt.requireOwnerProjectId(auth);
        assertOwnerCanAccessOrder(orderId, ownerProjectId);

        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found"));

        // Determine payment method code (you store method as entity; we extract code or name)
        String method = "";
        if (order.getPaymentMethod() != null) {
            Object codeOrName = tryGet(order.getPaymentMethod(), "getCode", "getName");
            method = (codeOrName == null) ? "" : String.valueOf(codeOrName).trim().toUpperCase(Locale.ROOT);
        }

        // Stripe best practice: if already fully paid -> no reopen unless refund flow exists
        var ps = paymentRead.summaryForOrder(order.getId(), order.getTotalPrice());
        if ("STRIPE".equals(method) && ps.isFullyPaid()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "Cannot reopen a paid STRIPE order without refund/void."
            ));
        }

        // If CASH: undo cash paid markers
        if ("CASH".equals(method)) {
            paymentWrite.resetCashToUnpaid(orderId);
        }

        // Set status to PENDING (use your existing status table)
        OrderStatus pending = requireStatusByName("PENDING");
        order.setStatus(pending);
        order.setOrderDate(LocalDateTime.now());
        orderRepo.save(order);

        // Return updated summary
        var ps2 = paymentRead.summaryForOrder(order.getId(), order.getTotalPrice());

        Map<String, Object> r = new HashMap<>();
        r.put("orderId", order.getId());
        r.put("status", pending.getName());
        r.put("statusUi", titleCaseStatus(pending.getName()));
        r.put("fullyPaid", ps2.isFullyPaid());
        r.put("payment", paymentToMap(ps2));
        return ResponseEntity.ok(r);
    }

    
    
    /* =========================================================================================
       ERROR HANDLERS
       ========================================================================================= */

    @ExceptionHandler(CheckoutBlockedException.class)
    public ResponseEntity<?> checkoutBlocked(CheckoutBlockedException ex) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("error", "CHECKOUT_BLOCKED");
        res.put("blockingErrors", ex.getBlockingErrors());
        res.put("lineErrors", ex.getLineErrors());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(res);
    }

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
    
    
    
}