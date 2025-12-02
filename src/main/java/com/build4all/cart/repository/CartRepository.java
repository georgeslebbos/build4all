package com.build4all.cart.repository;

import com.build4all.cart.domain.Cart;
import com.build4all.cart.domain.CartStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    @EntityGraph(attributePaths = {"items", "items.item"})
    Optional<Cart> findByUser_IdAndStatus(Long userId, CartStatus status);
}
