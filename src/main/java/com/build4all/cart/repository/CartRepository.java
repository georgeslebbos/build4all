package com.build4all.cart.repository;

import com.build4all.cart.domain.Cart;
import com.build4all.cart.domain.CartStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * CartRepository
 *
 * Spring Data JPA repository for Cart entity.
 * - Provides basic CRUD operations via JpaRepository
 * - Adds custom finder(s) for the "active cart" use case
 */
public interface CartRepository extends JpaRepository<Cart, Long> {

    /**
     * Find a cart for a specific user and status (most commonly: ACTIVE cart).
     *
     * Why @EntityGraph(attributePaths = {"items", "items.item"})?
     * - By default, Cart.items is usually LAZY loaded.
     * - If we fetch the cart and then later access cart.getItems(), Hibernate may:
     *   1) trigger N+1 queries (one query per item), OR
     *   2) throw LazyInitializationException if we are outside the session/transaction.
     *
     * EntityGraph tells JPA/Hibernate:
     * - Fetch cart + items + each item's Item entity in a single optimized query plan.
     *
     * Typical usage:
     * - Cart cart = cartRepo.findByUser_IdAndStatus(userId, CartStatus.ACTIVE).orElseThrow(...)
     * - Render cart details to the client
     * - Convert cart to order during checkout
     *
     * @param userId  the cart owner user id
     * @param status  cart status (ACTIVE / CONVERTED / etc.)
     * @return        Optional cart matching the criteria
     */
    @EntityGraph(attributePaths = {"items", "items.item"})
    Optional<Cart> findByUser_IdAndStatus(Long userId, CartStatus status);
}
