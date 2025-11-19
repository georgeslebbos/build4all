package com.build4all.order.repository;

import com.build4all.order.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderStatusRepository extends JpaRepository<OrderStatus, Long> {
    boolean existsByNameIgnoreCase(String name);
    Optional<OrderStatus> findByNameIgnoreCase(String name);
}
