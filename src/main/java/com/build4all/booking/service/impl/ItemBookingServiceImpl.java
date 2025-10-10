package com.build4all.booking.service.impl;

import com.build4all.booking.domain.Booking;                 // booking header
import com.build4all.catalog.domain.Currency;               // currency entity
import com.build4all.catalog.domain.Item;                   // item entity
import com.build4all.booking.domain.ItemBooking;            // item booking line
import com.build4all.user.domain.Users;                  // user entity
import com.build4all.booking.repository.BookingRepository;  // repo: booking header
import com.build4all.catalog.repository.CurrencyRepository; // repo: currency
import com.build4all.booking.repository.ItemBookingsRepository; // repo: item bookings
import com.build4all.catalog.repository.ItemRepository;     // repo: items
import com.build4all.user.repository.UsersRepository;    // repo: users
import com.build4all.booking.service.ItemBookingService;     // service interface
import jakarta.transaction.Transactional;             // transactional
import org.springframework.stereotype.Service;        // spring service

import java.lang.reflect.Method;                      // reflection (capacity)
import java.math.BigDecimal;                          // money math
import java.time.LocalDateTime;                       // timestamps
import java.util.List;                                // list type
import java.util.Locale;                              // lower/upper compare
import java.util.stream.Collectors;                   // streams

@Service                                              // service bean
@Transactional                                        // wrap in tx
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

    // try to read capacity from Item class (optional, no hard coupling)
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

    // create booking after Stripe payment (status = PENDING)
    @Override
    public ItemBooking createBookItem(Long userId, Long itemId, int quantiy,
                                      String stripePaymentId, Long currencyId) {
        if (userId == null) throw new IllegalArgumentException("userId is required");      // basic checks
        if (itemId == null) throw new IllegalArgumentException("itemId is required");
        if (quantiy <= 0) throw new IllegalArgumentException("participants must be > 0");
        if (stripePaymentId == null || stripePaymentId.isBlank())
            throw new IllegalArgumentException("stripePaymentId is required");

        try {
            var pi = com.stripe.model.PaymentIntent.retrieve(stripePaymentId);             // get PI
            if (pi == null || !"succeeded".equalsIgnoreCase(pi.getStatus()))               // must be paid
                throw new IllegalStateException("Stripe payment not confirmed");
        } catch (com.stripe.exception.StripeException se) {
            throw new IllegalStateException("Stripe error: " + se.getMessage());           // map error
        }

        Users user = usersRepo.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found")); // load user
        Item  item = itemRepo.findById(itemId).orElseThrow(() -> new IllegalArgumentException("Item not found")); // load item

        Currency currency = null;                                                          // currency optional
        if (currencyId != null) {
            currency = currencyRepo.findById(currencyId).orElseThrow(() -> new IllegalArgumentException("Currency not found"));
        }

        Integer capacity = tryCapacity(item);                                              // capacity if available
        if (capacity != null) {
            int already = bookingsRepo.sumQuantityByItemIdAndBookingStatuses(itemId, List.of("COMPLETED")); // taken seats
            int remaining = capacity - already;                                            // left seats
            if (quantiy > remaining) throw new IllegalStateException("Not enough seats available");    // block
        }

        BigDecimal unit  = item.getPrice() == null ? BigDecimal.ZERO : item.getPrice();   // unit price
        BigDecimal total = unit.multiply(BigDecimal.valueOf(quantiy));                // total

        Booking booking = new Booking();                                                   // header
        booking.setUser(user);                                                             // who
        booking.setStatus("PENDING");                                                      // start as PENDING
        booking.setBookingDate(LocalDateTime.now());                                       // time
        booking.setTotalPrice(total);                                                      // total
        if (currency != null) booking.setCurrency(currency);                               // optional currency
        booking = bookingRepo.save(booking);                                               // save header

        ItemBooking line = new ItemBooking();                                              // line
        line.setBooking(booking);                                                          // link header
        line.setItem(item);                                                                // link item
        line.setUser(user);                                                                // link user
        line.setQuantity(quantiy);                                                    // qty
        line.setPrice(unit);                                                               // unit snapshot
        if (currency != null) line.setCurrency(currency);                                  // line currency
        line.setCreatedAt(LocalDateTime.now());                                            // timestamp

        return bookingsRepo.save(line);                                                    // save line
    }

    // create booking by business (cash, keep header PENDING; business can update later)
    @Override
    public ItemBooking createCashBookingByBusiness(Long itemId, Long businessUserId,
                                                   int participants, boolean wasPaid, Long currencyId) {
        if (itemId == null) throw new IllegalArgumentException("itemId is required");
        if (businessUserId == null) throw new IllegalArgumentException("businessUserId is required");
        if (participants <= 0) throw new IllegalArgumentException("participants must be > 0");

        Users user = usersRepo.findById(businessUserId).orElseThrow(() -> new IllegalArgumentException("Business user not found"));
        Item  item = itemRepo.findById(itemId).orElseThrow(() -> new IllegalArgumentException("Item not found"));

        Currency currency = null;
        if (currencyId != null) {
            currency = currencyRepo.findById(currencyId).orElseThrow(() -> new IllegalArgumentException("Currency not found"));
        }

        Integer capacity = tryCapacity(item);
        if (capacity != null) {
            int already = bookingsRepo.sumQuantityByItemIdAndBookingStatuses(itemId, List.of("COMPLETED"));
            int remaining = capacity - already;
            if (participants > remaining) throw new IllegalStateException("Not enough seats available");
        }

        BigDecimal unit  = item.getPrice() == null ? BigDecimal.ZERO : item.getPrice();
        BigDecimal total = unit.multiply(BigDecimal.valueOf(participants));

        String headerStatus = "PENDING";                                                   // keep PENDING

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setStatus(headerStatus);
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

        return bookingsRepo.save(line);
    }

    // required by interface (used by availability & duplicate checks)
    @Override
    public boolean hasUserAlreadyBooked(Long itemId, Long userId) {
        return bookingsRepo.existsByItem_IdAndUser_Id(itemId, userId);
    }

    // === My bookings (use EntityGraph loader to avoid lazy problems) ===
    @Override
    public List<ItemBooking> getMyBookings(Long userId) {
        return bookingsRepo.findByUser_IdOrderByCreatedAtDesc(userId);                     // item + booking prefetched
    }

    @Override
    public List<ItemBooking> getMyBookingsByStatus(Long userId, String status) {
        final String wanted = status == null ? "" : status.toLowerCase(Locale.ROOT);       // normalize
        return bookingsRepo.findByUser_IdOrderByCreatedAtDesc(userId).stream()             // already prefetched
                .filter(ib -> ib.getBooking() != null
                           && ib.getBooking().getStatus() != null
                           && ib.getBooking().getStatus().equalsIgnoreCase(wanted))
                .collect(Collectors.toList());
    }

    // mutations (left as TODOs)
    @Override public void cancelBooking(Long bookingId, Long actorId) { throw new UnsupportedOperationException("cancelBooking is not implemented yet."); }
    @Override public void resetToPending(Long bookingId, Long actorId) { throw new UnsupportedOperationException("resetToPending is not implemented yet."); }
    @Override public void deleteBooking(Long bookingId, Long actorId) { bookingsRepo.deleteById(bookingId); }
    @Override public void refundIfEligible(Long bookingId, Long actorId) { throw new UnsupportedOperationException("refundIfEligible is not implemented yet."); }
    @Override public void requestCancel(Long bookingId, Long userId) { throw new UnsupportedOperationException("requestCancel is not implemented yet."); }
    @Override public void approveCancel(Long bookingId, Long businessId) { throw new UnsupportedOperationException("approveCancel is not implemented yet."); }
    @Override public void rejectCancel(Long bookingId, Long businessId) { throw new UnsupportedOperationException("rejectCancel is not implemented yet."); }
    @Override public void markRefunded(Long bookingId, Long businessId) { throw new UnsupportedOperationException("markRefunded is not implemented yet."); }

    // business views
    @Override public List<ItemBooking> getBookingsByBusiness(Long businessId) { return bookingsRepo.findAllByBusinessId(businessId); }
    @Override
    public void markPaid(Long bookingId, Long businessId) {
        // 1) Ensure this item-booking belongs to the caller’s business
        var ib = bookingsRepo.findByIdAndBusiness(bookingId, businessId)
            .orElseThrow(() -> new IllegalArgumentException("Booking not found or not yours"));

        // 2) Flip the header (Booking) status to COMPLETED (you’re using String status on Booking)
        var header = ib.getBooking();
        if (header == null) {
            throw new IllegalStateException("Invalid state: booking header is missing");
        }

        // No-op if already COMPLETED
        if (header.getStatus() != null && header.getStatus().equalsIgnoreCase("COMPLETED")) {
            return;
        }

        header.setStatus("COMPLETED");                // normalize to uppercase
        header.setBookingDate(java.time.LocalDateTime.now()); // optional touch; keep if you want latest timestamp

        // 3) Touch the line’s updatedAt so your list reflects the change immediately
        ib.setUpdatedAt(java.time.LocalDateTime.now());

        // 4) Persist changes
        bookingRepo.save(header); // header first
        bookingsRepo.save(ib);    // then the line
    }

    // cleanup
    @Override public void deleteBookingsByItemId(Long itemId) { bookingsRepo.deleteByItem_Id(itemId); }
}
