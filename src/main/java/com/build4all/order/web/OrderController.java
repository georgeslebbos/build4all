package com.build4all.order.web;

import com.build4all.order.domain.OrderItem;
import com.build4all.order.dto.CheckoutRequest;
import com.build4all.order.dto.CheckoutSummaryResponse;
import com.build4all.order.repository.OrderItemRepository;
import com.build4all.order.service.OrderService;
import com.build4all.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OrderController
 *
 * REST API entry point for:
 * - User order listing (my orders)
 * - Generic checkout from cart (activities + ecommerce)
 * - Order actions (cancel, refund, mark paid, etc.)
 * - Business views (orders for a business, insights)
 *
 * Important design choice:
 * - This controller is mostly a thin layer:
 *   * extract user/business id from JWT
 *   * call OrderService for business logic
 *   * shape responses into the Flutter-friendly “card” models
 *
 * The checkout endpoint:
 * - POST /api/orders/checkout
 * - Calls orderService.checkout(userId, request)
 * - Returns CheckoutSummaryResponse
 *
 * If you integrated Payment Orchestrator:
 * - CheckoutSummaryResponse should carry payment fields (clientSecret, redirectUrl, etc.)
 * - Flutter uses these fields to complete payment and/or show status.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    /** Main entry point for all order-related business logic */
    private final OrderService orderService;

    /** JWT parser used to extract userId/businessId from Authorization Bearer token */
    private final JwtUtil jwt;

    /** Used only for read-model queries (order cards, business list, insights) */
    private final OrderItemRepository orderItemRepo;

    public OrderController(OrderService orderService,
                           JwtUtil jwt,
                           OrderItemRepository orderItemRepo) {
        this.orderService = orderService;
        this.jwt = jwt;
        this.orderItemRepo = orderItemRepo;
    }

    /**
     * Strip "Bearer " prefix (if present) from Authorization header.
     * Your JwtUtil expects the raw token string, not the full header.
     */
    private String strip(String auth) {
        if (auth == null) return "";
        return auth.replace("Bearer ", "").trim();
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
     * Shapes a rich OrderItem entity into a Business Dashboard order row:
     * Includes:
     * - item minimal info
     * - user minimal info
     * - computed totalPrice
     * - status/wasPaid
     * - paymentMethod (if present on order header)
     *
     * Note:
     * - paymentMethod is extracted via reflection because your Order.getPaymentMethod()
     *   may return an entity (PaymentMethod) not a string; you may later normalize this
     *   to return code/name only (e.g., "STRIPE").
     */
    private Map<String, Object> toBusinessOrderShape(OrderItem oi) {
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

        // Currency might come from order header or from line currency, so we handle both safely.
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

        // Minimal user card for business view
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

        // Resolve order status name (status is an entity)
        Object rawStatus = (o != null) ? tryGet(o, "getStatus") : null;
        String statusName = null;
        if (rawStatus != null) {
            Object name = tryGet(rawStatus, "getName");
            statusName = name == null ? null : name.toString();
        }

        String orderStatus = titleCaseStatus(statusName);
        boolean wasPaid = "COMPLETED".equalsIgnoreCase(statusName);

        // Payment method:
        // Depending on your domain it could be:
        // - Order.getPaymentMethod() -> PaymentMethod entity
        // - Order.getMethod() -> string
        // We keep reflection to be safe.
        Object paymentMethod = (o != null) ? tryGet(o, "getPaymentMethod", "getMethod", "getPaymentType") : null;

        Map<String, Object> out = new HashMap<>();
        out.put("id", oi.getId());
        out.put("orderStatus", orderStatus);
        out.put("wasPaid", wasPaid);
        out.put("quantity", oi.getQuantity());
        out.put("totalPrice", totalPrice);
        out.put("paymentMethod", paymentMethod);
        out.put("orderDatetime", (o != null) ? tryGet(o, "getOrderDate", "getCreatedAt") : oi.getCreatedAt());
        out.put("currency", currency);
        out.put("item", item);
        out.put("user", user);
        return out;
    }

    /**
     * Shapes OrderItem into a lightweight “insights” row:
     * - who (clientName)
     * - what (itemName)
     * - paid or not (wasPaid)
     *
     * Used by: GET /api/orders/insights/orders
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
        Object rawStatus = (o != null) ? tryGet(o, "getStatus") : null;
        String statusName = rawStatus != null ? String.valueOf(tryGet(rawStatus, "getName")) : null;
        boolean wasPaid = "COMPLETED".equalsIgnoreCase(statusName);

        Map<String, Object> out = new HashMap<>();
        out.put("id", oi.getId());
        out.put("businessUserId", null);
        out.put("clientName", clientName);
        out.put("itemName", itemName);
        out.put("wasPaid", wasPaid);
        return out;
    }

    /* --------------------------------- user tickets --------------------------------- */

    /**
     * GET /api/orders/myorders
     * Returns the user's orders in the Flutter “card” structure.
     */
    @GetMapping("/myorders")
    @Operation(summary = "List all my orders (card model)")
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
    public ResponseEntity<?> myPending(@RequestHeader("Authorization") String auth) {
        Long userId = jwt.extractId(strip(auth));
        var rows = orderItemRepo.findUserOrderCardsByStatuses(
                userId, List.of("PENDING", "CANCEL_REQUESTED"));
        return ResponseEntity.ok(rows.stream().map(this::toUserCardShape).toList());
    }

    /** GET /api/orders/myorders/completed */
    @GetMapping("/myorders/completed")
    public ResponseEntity<?> myCompleted(@RequestHeader("Authorization") String auth) {
        Long userId = jwt.extractId(strip(auth));
        var rows = orderItemRepo.findUserOrderCardsByStatuses(userId, List.of("COMPLETED"));
        return ResponseEntity.ok(rows.stream().map(this::toUserCardShape).toList());
    }

    /** GET /api/orders/myorders/canceled */
    @GetMapping("/myorders/canceled")
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
    @Operation(summary = "Create order from cart (activities + ecommerce)")
    public ResponseEntity<?> checkout(@RequestHeader("Authorization") String auth,
                                      @Valid @RequestBody CheckoutRequest request) {
        Long userId = jwt.extractId(strip(auth));
        CheckoutSummaryResponse summary = orderService.checkout(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(summary);
    }

    /* ----------------------------------- actions ------------------------------------ */

    /**
     * PUT /api/orders/cancel/{orderItemId}
     * User cancels their own order item (service enforces ownership rules).
     */
    @PutMapping("/cancel/{orderItemId}")
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
    public ResponseEntity<?> requestCancel(@RequestHeader("Authorization") String auth,
                                           @PathVariable Long orderItemId) {
        Long userId = jwt.extractId(strip(auth));
        orderService.requestCancel(orderItemId, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * PUT /api/orders/cancel/approve/{orderItemId}
     * Business approves the cancel request (ownership enforced by service).
     */
    @PutMapping("/cancel/approve/{orderItemId}")
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
    public ResponseEntity<?> markPaid(@RequestHeader("Authorization") String auth,
                                      @PathVariable Long orderItemId) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        orderService.markPaid(orderItemId, businessId);
        return ResponseEntity.ok().build();
    }

    /* ------------------------------- business views --------------------------------- */

    /**
     * GET /api/orders/mybusinessorders
     * Returns business orders list (shaped for business dashboard).
     */
    @GetMapping("/mybusinessorders")
    public ResponseEntity<?> myBusinessOrders(@RequestHeader("Authorization") String auth) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        var list = orderItemRepo.findRichByBusinessId(businessId)
                .stream().map(this::toBusinessOrderShape).toList();
        return ResponseEntity.ok(list);
    }

    /**
     * GET /api/orders/insights/orders
     * Returns lightweight insights rows (clientName, itemName, wasPaid).
     */
    @GetMapping("/insights/orders")
    public ResponseEntity<?> insights(@RequestHeader("Authorization") String auth) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        var list = orderItemRepo.findRichByBusinessId(businessId)
                .stream().map(this::toInsightShape).toList();
        return ResponseEntity.ok(list);
    }

    /* ----------------------------------- errors ------------------------------------- */

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
     * Catch-all exception handler: HTTP 500 with JSON error message.
     * (In production, consider logging ex + hiding internal error details.)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> serverError(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * PUT /api/orders/order/reject/{orderItemId}
     * Business rejects an order (status => REJECTED).
     */
    @PutMapping("/order/reject/{orderItemId}")
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
    public ResponseEntity<?> unrejectOrder(@RequestHeader("Authorization") String auth,
                                           @PathVariable Long orderItemId) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        orderService.unrejectorder(orderItemId, businessId);
        return ResponseEntity.ok().build();
    }
}
