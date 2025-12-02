package com.build4all.cart.repository;

import com.build4all.cart.domain.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    void deleteByCart_Id(Long cartId);
}
