package com.build4all.payment.repository;

import com.build4all.payment.domain.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
