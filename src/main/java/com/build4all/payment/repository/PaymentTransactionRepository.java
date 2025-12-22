package com.build4all.payment.repository;

import com.build4all.payment.domain.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    /**
     * Find the transaction by provider reference.
     *
     * Stripe webhook gives you a PaymentIntent id like: "pi_123..."
     * So in your webhook handler you typically do:
     *   findFirstByProviderCodeIgnoreCaseAndProviderPaymentId("STRIPE", "pi_123")
     *
     * Why "first"? because in theory duplicates shouldn't happen,
     * but using "first" avoids exceptions and returns one row.
     */
    Optional<PaymentTransaction> findFirstByProviderCodeIgnoreCaseAndProviderPaymentId(
            String providerCode,
            String providerPaymentId
    );

    /**
     * Find the most recent transaction for a given order (latest attempt).
     *
     * This is useful when:
     * - The user retries payment for the same order
     * - You want to show the latest payment status for an order
     * - You want to resume/inspect the latest providerPaymentId
     */
    Optional<PaymentTransaction> findFirstByOrderIdOrderByCreatedAtDesc(Long orderId);

    /**
     * For CASH flow, we need to locate the latest CASH transaction for an order
     * so the owner/business can mark it as PAID after collecting money.
     */
    Optional<PaymentTransaction> findFirstByOrderIdAndProviderCodeIgnoreCaseOrderByCreatedAtDesc(
            Long orderId,
            String providerCode
    );


    /* =========================================================================================
       ✅ NEW: Payment sums (for fully-paid computation)
       ========================================================================================= */

    /**
     * Sum of PAID transactions for a single order.
     *
     * Rule:
     * - We treat ONLY status == "PAID" as confirmed money received.
     * - CREATED / REQUIRES_ACTION / OFFLINE_PENDING should NOT count as paid.
     *
     * Note:
     * - We store status as String, so we compare using UPPER(status) = 'PAID'
     * - COALESCE to 0 so callers don't handle null.
     */
    @Query("""
           select coalesce(sum(pt.amount), 0)
           from PaymentTransaction pt
           where pt.orderId = :orderId
             and upper(pt.status) = 'PAID'
           """)
    BigDecimal sumPaidAmountByOrderId(@Param("orderId") Long orderId);

    /**
     * ✅ Batch version (IMPORTANT for OWNER list endpoints)
     *
     * Returns rows:
     *   [ orderId, paidSum ]
     *
     * This avoids N+1 queries when listing many orders.
     */
    @Query("""
           select pt.orderId, coalesce(sum(pt.amount), 0)
           from PaymentTransaction pt
           where pt.orderId in :orderIds
             and upper(pt.status) = 'PAID'
           group by pt.orderId
           """)
    List<Object[]> sumPaidAmountsByOrderIds(@Param("orderIds") List<Long> orderIds);
}
