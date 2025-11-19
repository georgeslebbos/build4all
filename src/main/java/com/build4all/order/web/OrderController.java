package com.build4all.order.web;

import com.build4all.order.domain.OrderItem;
import com.build4all.order.repository.OrderItemRepository;
import com.build4all.order.service.OrderService;
import com.build4all.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final JwtUtil jwt;
    private final OrderItemRepository orderItemRepo;

    public OrderController(OrderService orderService,
                           JwtUtil jwt,
                           OrderItemRepository orderItemRepo) {
        this.orderService = orderService;
        this.jwt = jwt;
        this.orderItemRepo = orderItemRepo;
    }

    private String strip(String auth) {
        if (auth == null) return "";
        return auth.replace("Bearer ", "").trim();
    }

    /* ---------------------------- helpers (safe getters + shaping) ---------------------------- */

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
        String s = raw.trim().toUpperCase();
        return switch (s) {
            case "PENDING" -> "Pending";
            case "COMPLETED" -> "Completed";
            case "CANCELED" -> "Canceled";
            case "CANCEL_REQUESTED" -> "CancelRequested";
            default -> s.isEmpty() ? "" : s.charAt(0) + s.substring(1).toLowerCase();
        };
    }

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
        if (oTot instanceof BigDecimal bd) {
            totalPrice = bd.doubleValue();
        } else if (oi.getPrice() != null) {
            totalPrice = oi.getPrice().multiply(BigDecimal.valueOf(oi.getQuantity())).doubleValue();
        }

        Object rawStatus = (o != null) ? tryGet(o, "getStatus") : null;
        String statusName = null;
        if (rawStatus != null) {
            Object name = tryGet(rawStatus, "getName");
            statusName = name == null ? null : name.toString();
        }

        String orderStatus = titleCaseStatus(statusName);
        boolean wasPaid = "COMPLETED".equalsIgnoreCase(statusName);

        Object paymentMethod = (o != null) ? tryGet(o, "getPaymentMethod", "getMethod", "getPaymentType") : null;

        Map<String, Object> out = new HashMap<>();
        out.put("id", oi.getId());
        out.put("orderStatus", orderStatus);
        out.put("wasPaid", wasPaid);
        out.put("numberOfParticipants", oi.getQuantity());
        out.put("totalPrice", totalPrice);
        out.put("paymentMethod", paymentMethod);
        out.put("orderDatetime", (o != null) ? tryGet(o, "getOrderDate", "getCreatedAt") : oi.getCreatedAt());
        out.put("currency", currency);
        out.put("item", item);
        out.put("user", user);
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

    @GetMapping("/myorders")
    @Operation(summary = "List all my orders (card model)")
    public ResponseEntity<?> myOrders(@RequestHeader("Authorization") String auth) {
        Long userId = jwt.extractId(strip(auth));
        var rows = orderItemRepo.findUserOrderCards(userId);
        var shaped = rows.stream().map(this::toUserCardShape).toList();
        return ResponseEntity.ok(shaped);
    }

    @GetMapping("/myorders/pending")
    public ResponseEntity<?> myPending(@RequestHeader("Authorization") String auth) {
        Long userId = jwt.extractId(strip(auth));
        var rows = orderItemRepo.findUserOrderCardsByStatuses(
                userId, List.of("PENDING", "CANCEL_REQUESTED"));
        return ResponseEntity.ok(rows.stream().map(this::toUserCardShape).toList());
    }

    @GetMapping("/myorders/completed")
    public ResponseEntity<?> myCompleted(@RequestHeader("Authorization") String auth) {
        Long userId = jwt.extractId(strip(auth));
        var rows = orderItemRepo.findUserOrderCardsByStatuses(userId, List.of("COMPLETED"));
        return ResponseEntity.ok(rows.stream().map(this::toUserCardShape).toList());
    }

    @GetMapping("/myorders/canceled")
    public ResponseEntity<?> myCanceled(@RequestHeader("Authorization") String auth) {
        Long userId = jwt.extractId(strip(auth));
        var rows = orderItemRepo.findUserOrderCardsByStatuses(userId, List.of("CANCELED"));
        return ResponseEntity.ok(rows.stream().map(this::toUserCardShape).toList());
    }

    /* ----------------------------------- actions ------------------------------------ */

    @PutMapping("/cancel/{orderItemId}")
    public ResponseEntity<?> cancel(@RequestHeader("Authorization") String auth,
                                    @PathVariable Long orderItemId) {
        Long actorId = jwt.extractId(strip(auth));
        orderService.cancelorder(orderItemId, actorId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/pending/{orderItemId}")
    public ResponseEntity<?> resetToPending(@RequestHeader("Authorization") String auth,
                                            @PathVariable Long orderItemId) {
        Long actorId = jwt.extractId(strip(auth));
        orderService.resetToPending(orderItemId, actorId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/delete/{orderItemId}")
    public ResponseEntity<?> delete(@RequestHeader("Authorization") String auth,
                                    @PathVariable Long orderItemId) {
        Long actorId = jwt.extractId(strip(auth));
        orderService.deleteorder(orderItemId, actorId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/refund/{orderItemId}")
    public ResponseEntity<?> refund(@RequestHeader("Authorization") String auth,
                                    @PathVariable Long orderItemId) {
        Long actorId = jwt.extractId(strip(auth));
        orderService.refundIfEligible(orderItemId, actorId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/cancel/request/{orderItemId}")
    public ResponseEntity<?> requestCancel(@RequestHeader("Authorization") String auth,
                                           @PathVariable Long orderItemId) {
        Long userId = jwt.extractId(strip(auth));
        orderService.requestCancel(orderItemId, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/cancel/approve/{orderItemId}")
    public ResponseEntity<?> approveCancel(@RequestHeader("Authorization") String auth,
                                           @PathVariable Long orderItemId) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        orderService.approveCancel(orderItemId, businessId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/cancel/reject/{orderItemId}")
    public ResponseEntity<?> rejectCancel(@RequestHeader("Authorization") String auth,
                                          @PathVariable Long orderItemId) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        orderService.rejectCancel(orderItemId, businessId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/cancel/mark-refunded/{orderItemId}")
    public ResponseEntity<?> markRefunded(@RequestHeader("Authorization") String auth,
                                          @PathVariable Long orderItemId) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        orderService.markRefunded(orderItemId, businessId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/mark-paid/{orderItemId}")
    public ResponseEntity<?> markPaid(@RequestHeader("Authorization") String auth,
                                      @PathVariable Long orderItemId) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        orderService.markPaid(orderItemId, businessId);
        return ResponseEntity.ok().build();
    }

    /* ------------------------------- business views --------------------------------- */

    @GetMapping("/mybusinessorders")
    public ResponseEntity<?> myBusinessOrders(@RequestHeader("Authorization") String auth) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        var list = orderItemRepo.findRichByBusinessId(businessId)
                .stream().map(this::toBusinessOrderShape).toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/insights/orders")
    public ResponseEntity<?> insights(@RequestHeader("Authorization") String auth) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        var list = orderItemRepo.findRichByBusinessId(businessId)
                .stream().map(this::toInsightShape).toList();
        return ResponseEntity.ok(list);
    }

    /* ----------------------------------- errors ------------------------------------- */

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> serverError(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", ex.getMessage()));
    }

    @PutMapping("/order/reject/{orderItemId}")
    public ResponseEntity<?> rejectOrder(@RequestHeader("Authorization") String auth,
                                         @PathVariable Long orderItemId) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        orderService.rejectorder(orderItemId, businessId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/order/unreject/{orderItemId}")
    public ResponseEntity<?> unrejectOrder(@RequestHeader("Authorization") String auth,
                                           @PathVariable Long orderItemId) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        orderService.unrejectorder(orderItemId, businessId);
        return ResponseEntity.ok().build();
    }
}
