package com.build4all.payment.repository;

import com.build4all.payment.domain.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {

    /**
     * Returns all payment gateways that are enabled at PLATFORM level.
     *
     * Example:
     * - STRIPE enabled=true
     * - CASH enabled=true
     * - PAYPAL enabled=false
     *
     * This method is used when you want to list "available plugins" globally
     * (like installed payment plugins in WooCommerce).
     */
    List<PaymentMethod> findByEnabledTrue();

    /**
     * Finds a payment gateway by its CODE (name) ignoring case.
     *
     * Example:
     * - findByNameIgnoreCase("stripe") -> returns PaymentMethod(name="STRIPE")
     *
     * Common usages:
     * - Validating the gateway exists before saving PaymentMethodConfig
     * - Preventing duplicates when creating payment methods
     * - Ensuring a requested payment method is supported at platform level
     */
    Optional<PaymentMethod> findByNameIgnoreCase(String name);
}
