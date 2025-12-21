package com.build4all.payment.service;

import com.build4all.payment.repository.PaymentTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * OrderPaymentReadService
 *
 * ✅ Purpose:
 * - Compute "paidAmount" and "fullyPaid" based on payment_transactions ledger
 * - Provide a SAFE read model for OrderController (OWNER views, dashboards, etc.)
 *
 * ✅ Why this exists:
 * - Order.status (COMPLETED) is NOT a reliable indicator of actual payment,
 *   because business/owner can change statuses manually.
 * - The source of truth for money is payment_transactions (audit ledger).
 *
 * Rule used here:
 * - paidAmount = SUM(payment_transactions.amount) WHERE status = 'PAID'
 * - fullyPaid = paidAmount >= orderTotal (order.totalPrice)
 *
 * Notes:
 * - We compare with BigDecimal safely (no floating)
 * - We default null totals to zero.
 */
@Service
public class OrderPaymentReadService {

    private final PaymentTransactionRepository txRepo;

    public OrderPaymentReadService(PaymentTransactionRepository txRepo) {
        this.txRepo = txRepo;
    }

    /**
     * Computes payment summary for ONE order.
     */
    @Transactional(readOnly = true)
    public PaymentSummary summaryForOrder(Long orderId, BigDecimal orderTotal) {
        BigDecimal total = safe(orderTotal);

        BigDecimal paid = txRepo.sumPaidAmountByOrderId(orderId);
        paid = safe(paid);

        // Remaining cannot be negative
        BigDecimal remaining = total.subtract(paid);
        if (remaining.signum() < 0) remaining = BigDecimal.ZERO;

        boolean fullyPaid = paid.compareTo(total) >= 0 && total.signum() > 0;

        PaymentState state;
        if (total.signum() <= 0) {
            // If order total is 0, treat as "PAID" logically (free order)
            state = PaymentState.PAID;
            fullyPaid = true;
            remaining = BigDecimal.ZERO;
        } else if (paid.signum() <= 0) {
            state = PaymentState.UNPAID;
        } else if (fullyPaid) {
            state = PaymentState.PAID;
        } else {
            state = PaymentState.PARTIALLY_PAID;
        }

        return new PaymentSummary(orderId, total, paid, remaining, fullyPaid, state.name());
    }

    /**
     * ✅ Batch computation for many orders.
     *
     * Input:
     * - orderId -> orderTotal
     *
     * Output:
     * - orderId -> PaymentSummary
     *
     * Internally uses one DB query: sumPaidAmountsByOrderIds(...)
     */
    @Transactional(readOnly = true)
    public Map<Long, PaymentSummary> summariesForOrders(Map<Long, BigDecimal> orderTotalsByOrderId) {
        if (orderTotalsByOrderId == null || orderTotalsByOrderId.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> orderIds = new ArrayList<>(orderTotalsByOrderId.keySet());

        // One query for all paid sums
        List<Object[]> rows = txRepo.sumPaidAmountsByOrderIds(orderIds);

        // Build a map of orderId -> paidSum
        Map<Long, BigDecimal> paidByOrderId = new HashMap<>();
        for (Object[] r : rows) {
            if (r == null || r.length < 2) continue;
            Long orderId = (r[0] instanceof Long) ? (Long) r[0] : Long.valueOf(String.valueOf(r[0]));
            BigDecimal paid = (r[1] instanceof BigDecimal) ? (BigDecimal) r[1] : new BigDecimal(String.valueOf(r[1]));
            paidByOrderId.put(orderId, safe(paid));
        }

        Map<Long, PaymentSummary> out = new HashMap<>();

        for (Map.Entry<Long, BigDecimal> e : orderTotalsByOrderId.entrySet()) {
            Long orderId = e.getKey();
            BigDecimal total = safe(e.getValue());
            BigDecimal paid = safe(paidByOrderId.get(orderId));

            BigDecimal remaining = total.subtract(paid);
            if (remaining.signum() < 0) remaining = BigDecimal.ZERO;

            boolean fullyPaid = paid.compareTo(total) >= 0 && (total.signum() > 0);

            PaymentState state;
            if (total.signum() <= 0) {
                state = PaymentState.PAID;
                fullyPaid = true;
                remaining = BigDecimal.ZERO;
            } else if (paid.signum() <= 0) {
                state = PaymentState.UNPAID;
            } else if (fullyPaid) {
                state = PaymentState.PAID;
            } else {
                state = PaymentState.PARTIALLY_PAID;
            }

            out.put(orderId, new PaymentSummary(orderId, total, paid, remaining, fullyPaid, state.name()));
        }

        return out;
    }

    private BigDecimal safe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** Simple states for UI filtering / badges */
    public enum PaymentState {
        UNPAID,
        PARTIALLY_PAID,
        PAID
    }

    /**
     * PaymentSummary
     *
     * ✅ Returned to controllers; safe to expose to UI.
     * - No secrets
     * - No raw provider payloads
     */
    public static class PaymentSummary {
        private final Long orderId;
        private final BigDecimal orderTotal;
        private final BigDecimal paidAmount;
        private final BigDecimal remainingAmount;
        private final boolean fullyPaid;
        private final String paymentState;

        public PaymentSummary(Long orderId,
                              BigDecimal orderTotal,
                              BigDecimal paidAmount,
                              BigDecimal remainingAmount,
                              boolean fullyPaid,
                              String paymentState) {
            this.orderId = orderId;
            this.orderTotal = orderTotal;
            this.paidAmount = paidAmount;
            this.remainingAmount = remainingAmount;
            this.fullyPaid = fullyPaid;
            this.paymentState = paymentState;
        }

        public Long getOrderId() { return orderId; }
        public BigDecimal getOrderTotal() { return orderTotal; }
        public BigDecimal getPaidAmount() { return paidAmount; }
        public BigDecimal getRemainingAmount() { return remainingAmount; }
        public boolean isFullyPaid() { return fullyPaid; }
        public String getPaymentState() { return paymentState; }
    }
}
