package com.build4all.payment.repository;

import com.build4all.payment.domain.PaymentMethodConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Transactional(readOnly = true) // ✅ helps ensure @Lob reads happen in a real tx
public interface PaymentMethodConfigRepository extends JpaRepository<PaymentMethodConfig, Long> {

    /**
     * Finds the configuration of ONE gateway for ONE project.
     *
     * Example usage:
     * - ownerProjectId = 100
     * - methodName     = "STRIPE"
     *
     * It searches payment_method_configs joined with payment_methods by name:
     *   WHERE owner_project_id = 100
     *     AND payment_methods.name ILIKE 'STRIPE'
     *
     * Used in:
     * - PaymentOrchestratorService.startPayment(...) to load keys before calling Stripe
     * - Owner settings: GET/PUT a specific gateway config for that project
     * - Webhook: sometimes you need the project's webhookSecret, so you load STRIPE config
     */
    Optional<PaymentMethodConfig> findByOwnerProjectIdAndPaymentMethod_NameIgnoreCase(
            Long ownerProjectId,
            String methodName
    );

    /**
     * Returns all ENABLED gateways for a project.
     *
     * Example usage:
     * - ownerProjectId = 100
     * Result: [STRIPE enabled, CASH enabled]
     *
     * Used in:
     * - Public checkout screen: GET /api/projects/{ownerProjectId}/payment/enabled
     *   so the mobile app shows only methods that the owner enabled.
     */
    List<PaymentMethodConfig> findByOwnerProjectIdAndEnabledTrue(Long ownerProjectId);

    /**
     * ✅ Returns all configs for a project in one query (better for owner checkbox list).
     * join fetch paymentMethod to avoid lazy loading issues.
     */
    @Query("""
        select c from PaymentMethodConfig c
        join fetch c.paymentMethod pm
        where c.ownerProjectId = :ownerProjectId
    """)
    List<PaymentMethodConfig> findAllByOwnerProjectIdWithMethod(@Param("ownerProjectId") Long ownerProjectId);
}