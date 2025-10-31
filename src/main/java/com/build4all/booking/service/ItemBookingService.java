package com.build4all.booking.service;

import com.build4all.booking.domain.ItemBooking;
import java.util.List;

public interface ItemBookingService {

	ItemBooking createBookItem(Long userId, Long itemId, int participants, String stripePaymentId, Long currencyId);

    ItemBooking createCashBookingByBusiness(Long itemId, Long businessUserId, int participants, boolean wasPaid, Long currencyId);

    boolean hasUserAlreadyBooked(Long itemId, Long userId);

    List<ItemBooking> getMyBookings(Long userId);
    List<ItemBooking> getMyBookingsByStatus(Long userId, String status);

    void cancelBooking(Long bookingId, Long actorId);
    void resetToPending(Long bookingId, Long actorId);
    void deleteBooking(Long bookingId, Long actorId);

    void refundIfEligible(Long bookingId, Long actorId);
    void requestCancel(Long bookingId, Long userId);
    void approveCancel(Long bookingId, Long businessId);
    void rejectCancel(Long bookingId, Long businessId);
    void markRefunded(Long bookingId, Long businessId);
    void rejectBooking(Long bookingId, Long businessId);
    void unrejectBooking(Long bookingId, Long businessId);


    List<ItemBooking> getBookingsByBusiness(Long businessId);
    void markPaid(Long bookingId, Long businessId);

    void deleteBookingsByItemId(Long itemId);
}
