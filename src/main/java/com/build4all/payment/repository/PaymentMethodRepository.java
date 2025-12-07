package com.build4all.payment.repository;

import com.build4all.payment.domain.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {

    List<PaymentMethod> findByEnabledTrue();

    // 👇 to validate methods by name (STRIPE, CASH, ...)
    Optional<PaymentMethod> findByNameIgnoreCase(String name);
}
