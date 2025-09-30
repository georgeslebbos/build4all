package com.build4all.services.impl;

import com.build4all.entities.ItemBooking;
import com.build4all.repositories.ItemBookingsRepository;
import com.build4all.services.ItemBookingService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@Transactional
public class ItemBookingServiceImpl implements ItemBookingService {

    private final ItemBookingsRepository bookingsRepo;

    public ItemBookingServiceImpl(ItemBookingsRepository bookingsRepo) {
        this.bookingsRepo = bookingsRepo;
    }

    // ---- Creation flows (not implemented yet) --------------------------------

    @Override
    public ItemBooking createBookItem(Long userId, Long itemId, int participants, String stripePaymentId, Long currencyId) {
        // TODO: Implement and set currencyId on the created ItemBooking(s)
        throw new UnsupportedOperationException("createBookItem is not implemented yet.");
    }

    @Override
    public ItemBooking createCashBookingByBusiness(Long itemId, Long businessUserId, int participants, boolean wasPaid, Long currencyId) {
        // TODO: Implement and set currencyId on the created ItemBooking(s)
        throw new UnsupportedOperationException("createCashBookingByBusiness is not implemented yet.");
    }

    @Override
    public boolean hasUserAlreadyBooked(Long itemId, Long userId) {
        return bookingsRepo.existsByItem_IdAndUser_Id(itemId, userId);
    }

    // ---- “My bookings” views --------------------------------------------------

    @Override
    public List<ItemBooking> getMyBookings(Long userId) {
        return bookingsRepo.findByUser_Id(userId);
    }

    @Override
    public List<ItemBooking> getMyBookingsByStatus(Long userId, String status) {
        final String wanted = status == null ? "" : status.toLowerCase(Locale.ROOT);
        return bookingsRepo.findByUser_Id(userId).stream()
                .filter(ib -> ib.getBooking() != null
                           && ib.getBooking().getStatus() != null
                           && ib.getBooking().getStatus().equalsIgnoreCase(wanted))
                .collect(Collectors.toList());
    }

    // ---- Mutations (moderation / workflow) ------------------------------------

    @Override
    public void cancelBooking(Long bookingId, Long actorId) { throw new UnsupportedOperationException("cancelBooking is not implemented yet."); }
    @Override
    public void resetToPending(Long bookingId, Long actorId) { throw new UnsupportedOperationException("resetToPending is not implemented yet."); }
    @Override
    public void deleteBooking(Long bookingId, Long actorId) { bookingsRepo.deleteById(bookingId); }
    @Override
    public void refundIfEligible(Long bookingId, Long actorId) { throw new UnsupportedOperationException("refundIfEligible is not implemented yet."); }
    @Override
    public void requestCancel(Long bookingId, Long userId) { throw new UnsupportedOperationException("requestCancel is not implemented yet."); }
    @Override
    public void approveCancel(Long bookingId, Long businessId) { throw new UnsupportedOperationException("approveCancel is not implemented yet."); }
    @Override
    public void rejectCancel(Long bookingId, Long businessId) { throw new UnsupportedOperationException("rejectCancel is not implemented yet."); }
    @Override
    public void markRefunded(Long bookingId, Long businessId) { throw new UnsupportedOperationException("markRefunded is not implemented yet."); }

    // ---- Business views -------------------------------------------------------

    @Override
    public List<ItemBooking> getBookingsByBusiness(Long businessId) {
        return bookingsRepo.findAllByBusinessId(businessId);
    }

    @Override
    public void markPaid(Long bookingId, Long businessId) {
        throw new UnsupportedOperationException("markPaid is not implemented yet.");
    }

    // ---- Cleanup --------------------------------------------------------------

    @Override
    public void deleteBookingsByItemId(Long itemId) {
        bookingsRepo.deleteByItem_Id(itemId);
    }
}
