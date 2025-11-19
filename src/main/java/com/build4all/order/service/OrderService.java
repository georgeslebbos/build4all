package com.build4all.order.service;

import com.build4all.order.domain.OrderItem;

import java.util.List;

public interface OrderService {

    OrderItem createBookItem(Long userId, Long itemId, int participants,
                             String stripePaymentId, Long currencyId);

    OrderItem createCashBookingByBusiness(Long itemId, Long businessUserId,
                                          int participants, boolean wasPaid, Long currencyId);

    boolean hasUserAlreadyBooked(Long itemId, Long userId);

    List<OrderItem> getMyBookings(Long userId);
    List<OrderItem> getMyBookingsByStatus(Long userId, String status);

    void cancelBooking(Long orderItemId, Long actorId);
    void resetToPending(Long orderItemId, Long actorId);
    void deleteBooking(Long orderItemId, Long actorId);

    void refundIfEligible(Long orderItemId, Long actorId);
    void requestCancel(Long orderItemId, Long userId);
    void approveCancel(Long orderItemId, Long businessId);
    void rejectCancel(Long orderItemId, Long businessId);
    void markRefunded(Long orderItemId, Long businessId);
    void rejectBooking(Long orderItemId, Long businessId);
    void unrejectBooking(Long orderItemId, Long businessId);

    List<OrderItem> getBookingsByBusiness(Long businessId);
    void markPaid(Long orderItemId, Long businessId);

    void deleteBookingsByItemId(Long itemId);
}
