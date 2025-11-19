package com.build4all.order.service;

import com.build4all.order.domain.OrderItem;

import java.util.List;

public interface OrderService {

    OrderItem createBookItem(Long userId, Long itemId, int participants,
                             String stripePaymentId, Long currencyId);

    OrderItem createCashorderByBusiness(Long itemId, Long businessUserId,
                                          int participants, boolean wasPaid, Long currencyId);

    boolean hasUserAlreadyBooked(Long itemId, Long userId);

    List<OrderItem> getMyorders(Long userId);
    List<OrderItem> getMyordersByStatus(Long userId, String status);

    void cancelorder(Long orderItemId, Long actorId);
    void resetToPending(Long orderItemId, Long actorId);
    void deleteorder(Long orderItemId, Long actorId);

    void refundIfEligible(Long orderItemId, Long actorId);
    void requestCancel(Long orderItemId, Long userId);
    void approveCancel(Long orderItemId, Long businessId);
    void rejectCancel(Long orderItemId, Long businessId);
    void markRefunded(Long orderItemId, Long businessId);
    void rejectorder(Long orderItemId, Long businessId);
    void unrejectorder(Long orderItemId, Long businessId);

    List<OrderItem> getordersByBusiness(Long businessId);
    void markPaid(Long orderItemId, Long businessId);

    void deleteordersByItemId(Long itemId);
}
