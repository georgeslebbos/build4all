package com.build4all.cart.service;

import com.build4all.cart.dto.AddToCartRequest;
import com.build4all.cart.dto.CartResponse;
import com.build4all.cart.dto.UpdateCartItemRequest;

public interface CartService {

    CartResponse getMyCart(Long aupId, Long userId);

    CartResponse addToCart(Long aupId, Long userId, AddToCartRequest request);

    CartResponse updateCartItem(Long aupId, Long userId, Long cartItemId, UpdateCartItemRequest request);

    CartResponse removeCartItem(Long aupId, Long userId, Long cartItemId);

    void clearCart(Long aupId, Long userId);

    Long checkout(Long aupId,
                  Long userId,
                  String paymentMethod,
                  String stripePaymentId,
                  Long currencyId,
                  String couponCode);
}