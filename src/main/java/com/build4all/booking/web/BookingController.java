package com.build4all.booking.web;

import com.build4all.booking.domain.ItemBooking;
import com.build4all.booking.repository.ItemBookingsRepository;
import com.build4all.booking.service.ItemBookingService;
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
@RequestMapping("/api/bookings")
public class BookingController {

    private final ItemBookingService bookingService;
    private final JwtUtil jwt;
    private final ItemBookingsRepository itemBookingsRepo;

    public BookingController(ItemBookingService bookingService,
                             JwtUtil jwt,
                             ItemBookingsRepository itemBookingsRepo) {
        this.bookingService = bookingService;
        this.jwt = jwt;
        this.itemBookingsRepo = itemBookingsRepo;
    }

    private String strip(String auth) {
        if (auth == null) return "";
        return auth.replace("Bearer ", "").trim();
    }

    /* ---------------------------- helpers (safe getters + shaping) ---------------------------- */

    /** Try a list of no-arg getters; return first non-null result, else null. Never throws. */
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

    /** Turn the flat projection map into the nested shape Flutter expects. */
    private Map<String, Object> toUserCardShape(Map<String, Object> row) {
        Map<String, Object> out = new HashMap<>(row);

        // item{}
        Map<String, Object> item = new HashMap<>();
        item.put("itemName", out.remove("itemName"));
        item.put("location", out.remove("location"));
        item.put("startDatetime", out.remove("startDatetime"));
        item.put("imageUrl", out.remove("imageUrl"));
        out.put("item", item);

        // booking{status}
        Map<String, Object> booking = new HashMap<>();
        booking.put("status", out.get("bookingStatus"));
        out.put("booking", booking);

        // normalize for UI filters
        Object bs = out.get("bookingStatus");
        out.put("bookingStatus", titleCaseStatus(bs == null ? null : bs.toString()));

        return out;
    }

    /** Build rich map for business list without compile-time coupling to entity getter names. */
    private Map<String, Object> toBusinessBookingShape(ItemBooking ib) {
        var b = ib.getBooking();
        var i = ib.getItem();
        var u = ib.getUser();
        var cur = ib.getCurrency();

        // item{} via safe getters
        Map<String, Object> item = new HashMap<>();
        item.put("id", i != null ? tryGet(i, "getId") : null);
        item.put("itemName", i != null ? tryGet(i, "getName", "getItemName", "getTitle") : null);
        item.put("location", i != null ? tryGet(i, "getLocation", "getAddress", "getPlace") : null);
        item.put("startDatetime", i != null ? tryGet(i, "getStartDatetime", "getStartDateTime", "getStartAt", "getStart") : null);
        item.put("imageUrl", i != null ? tryGet(i, "getImageUrl", "getImage", "getImagePath") : null);

        // currency{} (prefer header, else line)
        Map<String, Object> currency = null;
        Object bc = (b != null) ? tryGet(b, "getCurrency") : null;
        if (bc != null) {
            Object code = tryGet(bc, "getCode");
            Object symbol = tryGet(bc, "getSymbol");
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

        // user{}
        Map<String, Object> user = null;
        if (u != null) {
            user = new HashMap<>();
            user.put("id", tryGet(u, "getId"));
            user.put("username", tryGet(u, "getUsername"));
            user.put("firstName", tryGet(u, "getFirstName"));
            user.put("lastName", tryGet(u, "getLastName"));
            user.put("profilePictureUrl", tryGet(u, "getProfilePictureUrl", "getAvatarUrl", "getPhotoUrl"));
        }

        // totals: prefer booking.totalPrice, else line price * qty
        double totalPrice = 0.0;
        Object bTot = (b != null) ? tryGet(b, "getTotalPrice") : null;
        if (bTot instanceof BigDecimal bd) {
            totalPrice = bd.doubleValue();
        } else if (ib.getPrice() != null) {
            totalPrice = ib.getPrice().multiply(BigDecimal.valueOf(ib.getQuantity())).doubleValue();
        }

        String bookingStatus = titleCaseStatus((b != null) ? String.valueOf(tryGet(b, "getStatus")) : null);
        boolean wasPaid = (b != null && "COMPLETED".equalsIgnoreCase(String.valueOf(tryGet(b, "getStatus"))));

        // paymentMethod may not exist -> try alternatives, else null
        Object paymentMethod = (b != null) ? tryGet(b, "getPaymentMethod", "getMethod", "getPaymentType") : null;

        Map<String, Object> out = new HashMap<>();
        out.put("id", ib.getId());
        out.put("bookingStatus", bookingStatus);
        out.put("wasPaid", wasPaid);
        out.put("numberOfParticipants", ib.getQuantity());
        out.put("totalPrice", totalPrice);
        out.put("paymentMethod", paymentMethod);
        out.put("bookingDatetime", (b != null) ? tryGet(b, "getBookingDate", "getCreatedAt") : ib.getCreatedAt());
        out.put("currency", currency);
        out.put("item", item);
        out.put("user", user);
        return out;
    }

    /** Small tile map for insights. */
    private Map<String, Object> toInsightShape(ItemBooking ib) {
        var u = ib.getUser();
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
        String itemName = (ib.getItem() != null)
                ? String.valueOf(tryGet(ib.getItem(), "getName", "getItemName", "getTitle"))
                : null;
        boolean wasPaid = (ib.getBooking() != null)
                && "COMPLETED".equalsIgnoreCase(String.valueOf(tryGet(ib.getBooking(), "getStatus")));

        Map<String, Object> out = new HashMap<>();
        out.put("id", ib.getId());
        out.put("businessUserId", null); // no per-line businessUser in your model
        out.put("clientName", clientName);
        out.put("itemName", itemName);
        out.put("wasPaid", wasPaid);
        return out;
    }

    /* --------------------------------- user tickets --------------------------------- */

    @GetMapping("/mybookings")
    @Operation(summary = "List all my bookings (card model)")
    public ResponseEntity<?> myBookings(@RequestHeader("Authorization") String auth) {
        Long userId = jwt.extractId(strip(auth));
        var rows = itemBookingsRepo.findUserBookingCards(userId); // flat projection
        var shaped = rows.stream().map(this::toUserCardShape).toList();
        return ResponseEntity.ok(shaped);
    }

    @GetMapping("/mybookings/pending")
    public ResponseEntity<?> myPending(@RequestHeader("Authorization") String auth) {
        Long userId = jwt.extractId(strip(auth));
        var rows = itemBookingsRepo.findUserBookingCardsByStatuses(
                userId, List.of("PENDING", "CANCEL_REQUESTED"));
        return ResponseEntity.ok(rows.stream().map(this::toUserCardShape).toList());
    }

    @GetMapping("/mybookings/completed")
    public ResponseEntity<?> myCompleted(@RequestHeader("Authorization") String auth) {
        Long userId = jwt.extractId(strip(auth));
        var rows = itemBookingsRepo.findUserBookingCardsByStatuses(userId, List.of("COMPLETED"));
        return ResponseEntity.ok(rows.stream().map(this::toUserCardShape).toList());
    }

    @GetMapping("/mybookings/canceled")
    public ResponseEntity<?> myCanceled(@RequestHeader("Authorization") String auth) {
        Long userId = jwt.extractId(strip(auth));
        var rows = itemBookingsRepo.findUserBookingCardsByStatuses(userId, List.of("CANCELED"));
        return ResponseEntity.ok(rows.stream().map(this::toUserCardShape).toList());
    }

    /* ----------------------------------- actions ------------------------------------ */

    @PutMapping("/cancel/{bookingId}")
    public ResponseEntity<?> cancel(@RequestHeader("Authorization") String auth,
                                    @PathVariable Long bookingId) {
        Long actorId = jwt.extractId(strip(auth));
        bookingService.cancelBooking(bookingId, actorId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/pending/{bookingId}")
    public ResponseEntity<?> resetToPending(@RequestHeader("Authorization") String auth,
                                            @PathVariable Long bookingId) {
        Long actorId = jwt.extractId(strip(auth));
        bookingService.resetToPending(bookingId, actorId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/delete/{bookingId}")
    public ResponseEntity<?> delete(@RequestHeader("Authorization") String auth,
                                    @PathVariable Long bookingId) {
        Long actorId = jwt.extractId(strip(auth));
        bookingService.deleteBooking(bookingId, actorId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/refund/{bookingId}")
    public ResponseEntity<?> refund(@RequestHeader("Authorization") String auth,
                                    @PathVariable Long bookingId) {
        Long actorId = jwt.extractId(strip(auth));
        bookingService.refundIfEligible(bookingId, actorId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/cancel/request/{bookingId}")
    public ResponseEntity<?> requestCancel(@RequestHeader("Authorization") String auth,
                                           @PathVariable Long bookingId) {
        Long userId = jwt.extractId(strip(auth));
        bookingService.requestCancel(bookingId, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/cancel/approve/{bookingId}")
    public ResponseEntity<?> approveCancel(@RequestHeader("Authorization") String auth,
                                           @PathVariable Long bookingId) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        bookingService.approveCancel(bookingId, businessId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/cancel/reject/{bookingId}")
    public ResponseEntity<?> rejectCancel(@RequestHeader("Authorization") String auth,
                                          @PathVariable Long bookingId) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        bookingService.rejectCancel(bookingId, businessId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/cancel/mark-refunded/{bookingId}")
    public ResponseEntity<?> markRefunded(@RequestHeader("Authorization") String auth,
                                          @PathVariable Long bookingId) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        bookingService.markRefunded(bookingId, businessId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/mark-paid/{bookingId}")
    public ResponseEntity<?> markPaid(@RequestHeader("Authorization") String auth,
                                      @PathVariable Long bookingId) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        bookingService.markPaid(bookingId, businessId);
        return ResponseEntity.ok().build();
    }

    /* ------------------------------- business views --------------------------------- */

    @GetMapping("/mybusinessbookings")
    public ResponseEntity<?> myBusinessBookings(@RequestHeader("Authorization") String auth) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        var list = itemBookingsRepo.findRichByBusinessId(businessId)
                .stream().map(this::toBusinessBookingShape).toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/insights/bookings")
    public ResponseEntity<?> insights(@RequestHeader("Authorization") String auth) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        var list = itemBookingsRepo.findRichByBusinessId(businessId)
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
    
    
    @PutMapping("/booking/reject/{bookingId}")
    public ResponseEntity<?> rejectBooking(@RequestHeader("Authorization") String auth,
                                           @PathVariable Long bookingId) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        bookingService.rejectBooking(bookingId, businessId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/booking/unreject/{bookingId}")
    public ResponseEntity<?> unrejectBooking(@RequestHeader("Authorization") String auth,
                                             @PathVariable Long bookingId) {
        Long businessId = jwt.extractBusinessId(strip(auth));
        bookingService.unrejectBooking(bookingId, businessId);
        return ResponseEntity.ok().build();
    }


}
