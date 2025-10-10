package com.build4all.booking.service.impl;

import com.build4all.booking.domain.Booking;                 // booking header
import com.build4all.catalog.domain.Currency;               // currency entity
import com.build4all.catalog.domain.Item;                   // item entity
import com.build4all.booking.domain.ItemBooking;            // item booking line
import com.build4all.user.domain.Users;                     // user entity
import com.build4all.booking.repository.BookingRepository;  // repo: booking header
import com.build4all.catalog.repository.CurrencyRepository; // repo: currency
import com.build4all.booking.repository.ItemBookingsRepository; // repo: item bookings
import com.build4all.catalog.repository.ItemRepository;     // repo: items
import com.build4all.user.repository.UsersRepository;       // repo: users
import com.build4all.booking.service.ItemBookingService;    // service interface
import jakarta.transaction.Transactional;                   // transactional
import org.springframework.stereotype.Service;              // spring service

import java.lang.reflect.Method;                            // reflection (capacity)
import java.math.BigDecimal;                                // money math
import java.time.LocalDateTime;                             // timestamps
import java.util.List;                                      // list type
import java.util.Locale;                                    // lower/upper compare
import java.util.stream.Collectors;                         // streams

@Service
@Transactional
public class ItemBookingServiceImpl implements ItemBookingService {

    // repositories
    private final ItemBookingsRepository bookingsRepo;
    private final BookingRepository      bookingRepo;
    private final UsersRepository        usersRepo;
    private final ItemRepository         itemRepo;
    private final CurrencyRepository     currencyRepo;

    // constructor injection
    public ItemBookingServiceImpl(ItemBookingsRepository bookingsRepo,
                                  BookingRepository bookingRepo,
                                  UsersRepository usersRepo,
                                  ItemRepository itemRepo,
                                  CurrencyRepository currencyRepo) {
        this.bookingsRepo = bookingsRepo;
        this.bookingRepo  = bookingRepo;
        this.usersRepo    = usersRepo;
        this.itemRepo     = itemRepo;
        this.currencyRepo = currencyRepo;
    }

    // --- capacity helper: try multiple common getters without tight coupling
    private Integer tryCapacity(Item item) {
        String[] names = {"getMaxParticipants","getCapacity","getSeats","getQuantityLimit","getStock"};
        for (String n : names) {
            try {
                Method m = item.getClass().getMethod(n);
                Object v = m.invoke(item);
                if (v instanceof Integer) return (Integer) v;
                if (v instanceof Number)  return ((Number) v).intValue();
            } catch (Exception ignored) {}
        }
        return null;
    }

    // --- ownership helpers
    private ItemBooking requireUserOwned(Long bookingId, Long userId) {
        return bookingsRepo.findByIdAndUser(bookingId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Booking not found or not yours"));
    }

    private ItemBooking requireBusinessOwned(Long bookingId, Long businessId) {
        return bookingsRepo.findByIdAndBusiness(bookingId, businessId)
            .orElseThrow(() -> new IllegalArgumentException("Booking not found or not your business"));
    }

    // --- status flip helper (touches header & line and saves)
    private void flipStatus(ItemBooking ib, String newStatusUpper) {
        var header = ib.getBooking();
        if (header == null) throw new IllegalStateException("Invalid state: missing booking header");
        header.setStatus(newStatusUpper);                          // your setter uppercases
        header.setBookingDate(LocalDateTime.now());                // touch timestamp
        ib.setUpdatedAt(LocalDateTime.now());                      // reflect in lists
        bookingRepo.save(header);                                  // save header first
        bookingsRepo.save(ib);                                     // then the line
    }

    // create booking after Stripe payment (status = PENDING)
    @Override
    public ItemBooking createBookItem(Long userId, Long itemId, int quantiy,
                                      String stripePaymentId, Long currencyId) {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (itemId == null) throw new IllegalArgumentException("itemId is required");
        if (quantiy <= 0) throw new IllegalArgumentException("participants must be > 0");
        if (stripePaymentId == null || stripePaymentId.isBlank())
            throw new IllegalArgumentException("stripePaymentId is required");

        try {
            var pi = com.stripe.model.PaymentIntent.retrieve(stripePaymentId);
            if (pi == null || !"succeeded".equalsIgnoreCase(pi.getStatus()))
                throw new IllegalStateException("Stripe payment not confirmed");
        } catch (com.stripe.exception.StripeException se) {
            throw new IllegalStateException("Stripe error: " + se.getMessage());
        }

        Users user = usersRepo.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Item  item = itemRepo.findById(itemId)
            .orElseThrow(() -> new IllegalArgumentException("Item not found"));

        Currency currency = null;
        if (currencyId != null) {
            currency = currencyRepo.findById(currencyId)
                .orElseThrow(() -> new IllegalArgumentException("Currency not found"));
        }

        Integer capacity = tryCapacity(item);
        if (capacity != null) {
            int already = bookingsRepo.sumQuantityByItemIdAndBookingStatuses(itemId, List.of("COMPLETED"));
            int remaining = capacity - already;
            if (quantiy > remaining) throw new IllegalStateException("Not enough seats available");
        }

        BigDecimal unit  = item.getPrice() == null ? BigDecimal.ZERO : item.getPrice();
        BigDecimal total = unit.multiply(BigDecimal.valueOf(quantiy));

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setStatus("PENDING");                          // start as PENDING
        booking.setBookingDate(LocalDateTime.now());
        booking.setTotalPrice(total);
        if (currency != null) booking.setCurrency(currency);
        booking = bookingRepo.save(booking);

        ItemBooking line = new ItemBooking();
        line.setBooking(booking);
        line.setItem(item);
        line.setUser(user);
        line.setQuantity(quantiy);
        line.setPrice(unit);
        if (currency != null) line.setCurrency(currency);
        line.setCreatedAt(LocalDateTime.now());

        return bookingsRepo.save(line);
    }

    // create booking by business (cash), keep header PENDING; business can update later
    @Override
    public ItemBooking createCashBookingByBusiness(Long itemId, Long businessUserId,
                                                   int participants, boolean wasPaid, Long currencyId) {
        if (itemId == null) throw new IllegalArgumentException("itemId is required");
        if (businessUserId == null) throw new IllegalArgumentException("businessUserId is required");
        if (participants <= 0) throw new IllegalArgumentException("participants must be > 0");

        Users user = usersRepo.findById(businessUserId)
            .orElseThrow(() -> new IllegalArgumentException("Business user not found"));
        Item  item = itemRepo.findById(itemId)
            .orElseThrow(() -> new IllegalArgumentException("Item not found"));

        Currency currency = null;
        if (currencyId != null) {
            currency = currencyRepo.findById(currencyId)
                .orElseThrow(() -> new IllegalArgumentException("Currency not found"));
        }

        Integer capacity = tryCapacity(item);
        if (capacity != null) {
            int already = bookingsRepo.sumQuantityByItemIdAndBookingStatuses(itemId, List.of("COMPLETED"));
            int remaining = capacity - already;
            if (participants > remaining) throw new IllegalStateException("Not enough seats available");
        }

        BigDecimal unit  = item.getPrice() == null ? BigDecimal.ZERO : item.getPrice();
        BigDecimal total = unit.multiply(BigDecimal.valueOf(participants));

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setStatus("PENDING");                          // keep PENDING
        booking.setBookingDate(LocalDateTime.now());
        booking.setTotalPrice(total);
        if (currency != null) booking.setCurrency(currency);
        booking = bookingRepo.save(booking);

        ItemBooking line = new ItemBooking();
        line.setBooking(booking);
        line.setItem(item);
        line.setUser(user);
        line.setQuantity(participants);
        line.setPrice(unit);
        if (currency != null) line.setCurrency(currency);
        line.setCreatedAt(LocalDateTime.now());

        // if you want PENDING but already paid (cash), you could set COMPLETED here when wasPaid == true
        // keeping as PENDING per your previous logic
        return bookingsRepo.save(line);
    }

    // availability & duplicate checks
    @Override
    public boolean hasUserAlreadyBooked(Long itemId, Long userId) {
        return bookingsRepo.existsByItem_IdAndUser_Id(itemId, userId);
    }

    // === My bookings (EntityGraph loader to avoid lazy issues) ===
    @Override
    public List<ItemBooking> getMyBookings(Long userId) {
        return bookingsRepo.findByUser_IdOrderByCreatedAtDesc(userId);
    }

    @Override
    public List<ItemBooking> getMyBookingsByStatus(Long userId, String status) {
        final String wanted = status == null ? "" : status.toLowerCase(Locale.ROOT);
        return bookingsRepo.findByUser_IdOrderByCreatedAtDesc(userId).stream()
                .filter(ib -> ib.getBooking() != null
                           && ib.getBooking().getStatus() != null
                           && ib.getBooking().getStatus().equalsIgnoreCase(wanted))
                .collect(Collectors.toList());
    }

    /* =========================
       Activated mutations below
       ========================= */

    // user: hard cancel (PENDING|CANCEL_REQUESTED -> CANCELED)
    @Override
    public void cancelBooking(Long bookingId, Long actorId) {
        var ib = requireUserOwned(bookingId, actorId);
        var header = ib.getBooking();
        if (header == null) throw new IllegalStateException("Invalid state: missing booking header");

        String curr = header.getStatus() == null ? "" : header.getStatus().toUpperCase(Locale.ROOT);
        if ("COMPLETED".equals(curr)) {
            throw new IllegalStateException("Cannot cancel a completed booking");
        }
        if ("CANCELED".equals(curr)) return; // no-op
        flipStatus(ib, "CANCELED");
    }

    // user: reset to PENDING (undo)
    @Override
    public void resetToPending(Long bookingId, Long actorId) {
        var ib = requireUserOwned(bookingId, actorId);
        var header = ib.getBooking();
        if (header == null) throw new IllegalStateException("Invalid state: missing booking header");

        String curr = header.getStatus() == null ? "" : header.getStatus().toUpperCase(Locale.ROOT);
        if ("COMPLETED".equals(curr)) {
            throw new IllegalStateException("Cannot reset a completed booking");
        }
        flipStatus(ib, "PENDING");
    }

    @Override
    public void deleteBooking(Long bookingId, Long actorId) {
        // if you want to enforce ownership for delete, resolve ownership first:
        bookingsRepo.findByIdAndUser(bookingId, actorId)
            .or(() -> bookingsRepo.findById(bookingId)) // fallback if you want to allow admin paths
            .orElseThrow(() -> new IllegalArgumentException("Booking not found or not yours"));
        bookingsRepo.deleteById(bookingId);
    }

    // auto policy: refund CANCELED; deny COMPLETED; PENDING/CANCEL_REQUESTED => cancel then refund
    @Override
    public void refundIfEligible(Long bookingId, Long actorId) {
        // allow both user-owned or business-owned; simplest is: load and check status
        var ib = bookingsRepo.findById(bookingId)
            .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
        var header = ib.getBooking();
        if (header == null) throw new IllegalStateException("Invalid state: missing booking header");
        String curr = header.getStatus() == null ? "" : header.getStatus().toUpperCase(Locale.ROOT);

        switch (curr) {
            case "CANCELED" -> flipStatus(ib, "REFUNDED");
            case "COMPLETED" -> throw new IllegalStateException("Completed bookings are not eligible for refund");
            case "PENDING", "CANCEL_REQUESTED" -> {
                flipStatus(ib, "CANCELED");
                flipStatus(ib, "REFUNDED");
            }
            default -> throw new IllegalStateException("Unknown status: " + curr);
        }
    }

    // user: raise cancel request
    @Override
    public void requestCancel(Long bookingId, Long userId) {
        var ib = requireUserOwned(bookingId, userId);
        var header = ib.getBooking();
        if (header == null) throw new IllegalStateException("Invalid state: missing booking header");

        String curr = header.getStatus() == null ? "" : header.getStatus().toUpperCase(Locale.ROOT);
        if ("COMPLETED".equals(curr)) {
            throw new IllegalStateException("Cannot request cancel on a completed booking");
        }
        if ("CANCELED".equals(curr)) return; // no-op
        flipStatus(ib, "CANCEL_REQUESTED");
    }

    // business: approve cancel -> CANCELED
    @Override
    public void approveCancel(Long bookingId, Long businessId) {
        var ib = requireBusinessOwned(bookingId, businessId);
        var header = ib.getBooking();
        if (header == null) throw new IllegalStateException("Invalid state: missing booking header");

        String curr = header.getStatus() == null ? "" : header.getStatus().toUpperCase(Locale.ROOT);
        if ("COMPLETED".equals(curr)) {
            throw new IllegalStateException("Cannot approve cancel for a completed booking; refund instead");
        }
        if ("CANCELED".equals(curr)) return; // no-op
        flipStatus(ib, "CANCELED");
    }

    // business: reject cancel -> back to PENDING
    @Override
    public void rejectCancel(Long bookingId, Long businessId) {
        var ib = requireBusinessOwned(bookingId, businessId);
        var header = ib.getBooking();
        if (header == null) throw new IllegalStateException("Invalid state: missing booking header");

        String curr = header.getStatus() == null ? "" : header.getStatus().toUpperCase(Locale.ROOT);
        if ("CANCELED".equals(curr)) {
            throw new IllegalStateException("Cannot reject; booking is already canceled");
        }
        if ("COMPLETED".equals(curr)) return; // no-op
        flipStatus(ib, "PENDING");
    }

    // business: bookkeeping status
    @Override
    public void markRefunded(Long bookingId, Long businessId) {
        var ib = requireBusinessOwned(bookingId, businessId);
        flipStatus(ib, "REFUNDED");
    }

    // business view list
    @Override
    public List<ItemBooking> getBookingsByBusiness(Long businessId) {
        return bookingsRepo.findRichByBusinessId(businessId);
    }

    // business: mark paid => COMPLETED
    @Override
    public void markPaid(Long bookingId, Long businessId) {
        var ib = bookingsRepo.findByIdAndBusiness(bookingId, businessId)
            .orElseThrow(() -> new IllegalArgumentException("Booking not found or not yours"));
        var header = ib.getBooking();
        if (header == null) throw new IllegalStateException("Invalid state: booking header is missing");
        if (header.getStatus() != null && header.getStatus().equalsIgnoreCase("COMPLETED")) {
            return; // no-op
        }
        header.setStatus("COMPLETED");
        header.setBookingDate(LocalDateTime.now());
        ib.setUpdatedAt(LocalDateTime.now());
        bookingRepo.save(header);
        bookingsRepo.save(ib);
    }

    // cleanup
    @Override
    public void deleteBookingsByItemId(Long itemId) {
        bookingsRepo.deleteByItem_Id(itemId);
    }
    
    
    @Override
    public void rejectBooking(Long bookingId, Long businessId) {
        var ib = requireBusinessOwned(bookingId, businessId);
        var header = ib.getBooking();
        if (header == null) throw new IllegalStateException("Invalid state: missing booking header");

        String curr = header.getStatus() == null ? "" : header.getStatus().toUpperCase(java.util.Locale.ROOT);
        if ("COMPLETED".equals(curr)) {
            throw new IllegalStateException("Cannot reject a completed booking");
        }
        if ("REJECTED".equals(curr)) return; // already rejected → no-op

        // ✅ make it REJECTED (not CANCELED)
        flipStatus(ib, "REJECTED");
    }


    @Override
    public void unrejectBooking(Long bookingId, Long businessId) {
        var ib = requireBusinessOwned(bookingId, businessId);
        var header = ib.getBooking();
        if (header == null) throw new IllegalStateException("Invalid state: missing booking header");
        String curr = header.getStatus() == null ? "" : header.getStatus().toUpperCase(java.util.Locale.ROOT);

        if ("COMPLETED".equals(curr)) {
            throw new IllegalStateException("Cannot move a completed booking back to pending");
        }
        // From CANCELED or CANCEL_REQUESTED → back to PENDING
        flipStatus(ib, "PENDING");
    }

}
