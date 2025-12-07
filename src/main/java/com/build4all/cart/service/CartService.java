package com.build4all.cart.service;

import com.build4all.cart.dto.AddToCartRequest;
import com.build4all.cart.dto.CartResponse;
import com.build4all.cart.dto.UpdateCartItemRequest;

public interface CartService {

    CartResponse getMyCart(Long userId);

    CartResponse addToCart(Long userId, AddToCartRequest request);

    CartResponse updateCartItem(Long userId, Long cartItemId, UpdateCartItemRequest request);

    CartResponse removeCartItem(Long userId, Long cartItemId);

    void clearCart(Long userId);

    // converts active cart to Order + OrderItems, returns created orderId
    Long checkout(Long userId,
                  String paymentMethod,
                  String stripePaymentId,
                  Long currencyId,
                  String couponCode);
}
