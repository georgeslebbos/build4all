package com.build4all.cart.service.impl;

import com.build4all.cart.domain.Cart;
import com.build4all.cart.domain.CartItem;
import com.build4all.cart.domain.CartStatus;
import com.build4all.cart.dto.AddToCartRequest;
import com.build4all.cart.dto.CartItemResponse;
import com.build4all.cart.dto.CartResponse;
import com.build4all.cart.dto.UpdateCartItemRequest;
import com.build4all.cart.repository.CartItemRepository;
import com.build4all.cart.repository.CartRepository;
import com.build4all.cart.service.CartService;
import com.build4all.catalog.domain.Item;
import com.build4all.catalog.repository.ItemRepository;
import com.build4all.order.dto.CartLine;
import com.build4all.order.dto.CheckoutRequest;
import com.build4all.order.dto.CheckoutSummaryResponse;
import com.build4all.order.service.OrderService;
import com.build4all.user.domain.Users;
import com.build4all.user.repository.UsersRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepo;
    private final CartItemRepository cartItemRepo;
    private final UsersRepository usersRepo;
    private final ItemRepository itemRepo;
    private final OrderService orderService;

    public CartServiceImpl(CartRepository cartRepo,
                           CartItemRepository cartItemRepo,
                           UsersRepository usersRepo,
                           ItemRepository itemRepo,
                           OrderService orderService) {
        this.cartRepo = cartRepo;
        this.cartItemRepo = cartItemRepo;
        this.usersRepo = usersRepo;
        this.itemRepo = itemRepo;
        this.orderService = orderService;
    }

    /* ============================================================
       Internal helpers
       ============================================================ */

    private Cart getOrCreateActiveCart(Long userId) {
        return cartRepo.findByUser_IdAndStatus(userId, CartStatus.ACTIVE)
                .orElseGet(() -> {
                    Users user = usersRepo.findById(userId)
                            .orElseThrow(() -> new IllegalArgumentException("User not found"));

                    Cart cart = new Cart();
                    cart.setUser(user);
                    cart.setStatus(CartStatus.ACTIVE);
                    cart.setTotalPrice(BigDecimal.ZERO);
                    cart.setCreatedAt(LocalDateTime.now());
                    cart.setUpdatedAt(LocalDateTime.now());
                    return cartRepo.save(cart);
                });
    }

    private void recalcTotals(Cart cart) {
        BigDecimal total = BigDecimal.ZERO;
        for (CartItem ci : cart.getItems()) {
            BigDecimal unit = ci.getUnitPrice() == null ? BigDecimal.ZERO : ci.getUnitPrice();
            total = total.add(unit.multiply(BigDecimal.valueOf(ci.getQuantity())));
        }
        cart.setTotalPrice(total);
        cart.setUpdatedAt(LocalDateTime.now());
    }

    private void assertStockAvailable(Item item, int qty) {
        if (qty <= 0)
            throw new IllegalArgumentException("Quantity must be > 0");

        if (item.getStock() != null) {
            if (item.getStock() <= 0)
                throw new IllegalArgumentException("Out of stock");

            if (qty > item.getStock())
                throw new IllegalArgumentException("Not enough stock. Available: " + item.getStock());
        }
    }
    
    
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_UPCOMING = "UPCOMING";
    private static final String STATUS_COMING_SOON = "COMING_SOON";

    private String itemStatusCode(Item item) {
        if (item == null || item.getStatus() == null || item.getStatus().getCode() == null) {
            return "";
        }
        return item.getStatus().getCode().trim().toUpperCase(Locale.ROOT);
    }

    private boolean isCartPurchasable(Item item) {
        return STATUS_PUBLISHED.equals(itemStatusCode(item));
    }

    private void assertItemPurchasableForCart(Item item) {
        if (item == null) {
            throw new IllegalArgumentException("Item not found");
        }

        String code = itemStatusCode(item);

        // support both labels just in case
        if (STATUS_UPCOMING.equals(code) || STATUS_COMING_SOON.equals(code)) {
            throw new IllegalArgumentException("Coming Soon products cannot be added to cart or purchased yet");
        }

        if (!STATUS_PUBLISHED.equals(code)) {
            throw new IllegalArgumentException("Item is not available for purchase");
        }
    }

    /* ============================================================
       PUBLIC API (TENANT SAFE)
       ============================================================ */

    @Override
    public CartResponse getMyCart(Long aupId, Long userId) {
        Cart cart = getOrCreateActiveCart(userId);
        recalcTotals(cart);
        return buildResponse(cart);
    }

    @Override
    public CartResponse addToCart(Long aupId, Long userId, AddToCartRequest request) {

        if (request == null)
            throw new IllegalArgumentException("Request required");

        if (request.getItemId() == null)
            throw new IllegalArgumentException("itemId required");

        if (request.getQuantity() <= 0)
            throw new IllegalArgumentException("Quantity must be > 0");

        Cart cart = getOrCreateActiveCart(userId);

        // 🔒 TENANT-SCOPED STOCK LOCK
        Item item = itemRepo.findByTenantForUpdate(aupId, request.getItemId())
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));

        assertItemPurchasableForCart(item);
        CartItem existing = cart.getItems().stream()
                .filter(ci -> ci.getItem().getId().equals(item.getId()))
                .findFirst()
                .orElse(null);

        if (existing != null) {
            int newQty = existing.getQuantity() + request.getQuantity();
            assertStockAvailable(item, newQty);
            existing.setQuantity(newQty);
            existing.setUpdatedAt(LocalDateTime.now());
        } else {
            assertStockAvailable(item, request.getQuantity());

            CartItem ci = new CartItem();
            ci.setItem(item);
            ci.setQuantity(request.getQuantity());
            ci.setUnitPrice(item.getEffectivePrice());
            ci.setCreatedAt(LocalDateTime.now());
            ci.setUpdatedAt(LocalDateTime.now());
            cart.addItem(ci);
        }

        recalcTotals(cart);
        cartRepo.save(cart);

        return buildResponse(cart);
    }

    @Override
    public CartResponse updateCartItem(Long aupId, Long userId,
                                       Long cartItemId,
                                       UpdateCartItemRequest request) {

        Cart cart = getOrCreateActiveCart(userId);

        CartItem ci = cart.getItems().stream()
                .filter(x -> x.getId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found"));

        if (request.getQuantity() <= 0) {
            cart.removeItem(ci);
            cartItemRepo.delete(ci);
        } else {
            Long itemId = ci.getItem().getId();

            // 🔒 TENANT-SCOPED LOCK
            Item fresh = itemRepo.findByTenantForUpdate(aupId, itemId)
                    .orElseThrow(() -> new IllegalArgumentException("Item not found"));

            assertItemPurchasableForCart(fresh);
            assertStockAvailable(fresh, request.getQuantity());

            ci.setQuantity(request.getQuantity());
            ci.setUpdatedAt(LocalDateTime.now());
        }

        recalcTotals(cart);
        cartRepo.save(cart);
        return buildResponse(cart);
    }

    @Override
    public CartResponse removeCartItem(Long aupId, Long userId, Long cartItemId) {
        Cart cart = getOrCreateActiveCart(userId);

        CartItem ci = cart.getItems().stream()
                .filter(x -> x.getId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found"));

        cart.removeItem(ci);
        cartItemRepo.delete(ci);

        recalcTotals(cart);
        cartRepo.save(cart);

        return buildResponse(cart);
    }

    @Override
    public void clearCart(Long aupId, Long userId) {
        Cart cart = cartRepo.findByUser_IdAndStatus(userId, CartStatus.ACTIVE)
                .orElse(null);

        if (cart == null) return;

        cartItemRepo.deleteByCart_Id(cart.getId());
        cart.getItems().clear();
        recalcTotals(cart);
        cartRepo.save(cart);
    }

    @Override
    public Long checkout(Long aupId,
                         Long userId,
                         String paymentMethod,
                         String stripePaymentId,
                         Long currencyId,
                         String couponCode) {

        Cart cart = cartRepo.findByUser_IdAndStatus(userId, CartStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("No active cart"));

        if (cart.getItems().isEmpty())
            throw new IllegalStateException("Cart is empty");

        // 🔒 FINAL TENANT-SCOPED STOCK CHECK
        for (CartItem ci : cart.getItems()) {
            Item fresh = itemRepo.findByTenantForUpdate(aupId, ci.getItem().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Item not found"));

            assertItemPurchasableForCart(fresh);
            assertStockAvailable(fresh, ci.getQuantity());
        }

        List<CartLine> lines = cart.getItems().stream().map(ci -> {
            CartLine line = new CartLine();
            line.setItemId(ci.getItem().getId());
            line.setQuantity(ci.getQuantity());
            line.setUnitPrice(ci.getUnitPrice());
            return line;
        }).toList();

        CheckoutRequest checkoutRequest = new CheckoutRequest();
        checkoutRequest.setLines(lines);
        checkoutRequest.setCurrencyId(currencyId);
        checkoutRequest.setPaymentMethod(paymentMethod);
        checkoutRequest.setStripePaymentId(stripePaymentId);
        checkoutRequest.setCouponCode(couponCode);

        CheckoutSummaryResponse summary =
                orderService.checkout(userId, checkoutRequest);

        return summary.getOrderId();
    }

    /* ============================================================
       RESPONSE BUILDER
       ============================================================ */

    private CartResponse buildResponse(Cart cart) {
        CartResponse res = new CartResponse();
        res.setCartId(cart.getId());
        res.setStatus(cart.getStatus().name());
        res.setTotalPrice(cart.getTotalPrice());

        List<CartItemResponse> items = new ArrayList<>();
        for (CartItem ci : cart.getItems()) {
            CartItemResponse r = new CartItemResponse();
            r.setCartItemId(ci.getId());
            r.setItemId(ci.getItem().getId());
            r.setItemName(ci.getItem().getName());
            r.setImageUrl(ci.getItem().getImageUrl());
            r.setQuantity(ci.getQuantity());
            r.setUnitPrice(ci.getUnitPrice());
            r.setLineTotal(ci.getUnitPrice()
                    .multiply(BigDecimal.valueOf(ci.getQuantity())));
            items.add(r);
        }

        res.setItems(items);
        boolean allPurchasable = cart.getItems().stream()
                .allMatch(ci -> isCartPurchasable(ci.getItem()));

        res.setCanCheckout(!items.isEmpty() && allPurchasable);
        res.setCheckoutTotalPrice(cart.getTotalPrice());
        return res;
    }
}