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
import java.util.stream.Collectors;

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

    /* ==============================
       Reflection helper
       ============================== */

    private Object tryGet(Object target, String... candidates) {
        if (target == null) return null;
        Class<?> c = target.getClass();
        for (String name : candidates) {
            try {
                var m = c.getMethod(name);
                return m.invoke(target);
            } catch (Exception ignored) {}
        }
        return null;
    }

    /* ==============================
       Stock helpers
       ============================== */

    private Integer readStock(Item item) {
        Object v = tryGet(item, "getStock", "getAvailableStock", "getQuantity", "getAvailableQuantity");
        if (v == null) return null; // null => stock not tracked

        if (v instanceof Integer i) return i;
        if (v instanceof Long l) return l.intValue();
        if (v instanceof Number n) return n.intValue();

        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return null; }
    }

    private void assertStockAvailable(Item item, int desiredQty) {
        if (desiredQty <= 0) throw new IllegalArgumentException("Quantity must be > 0");

        Integer stock = readStock(item);
        if (stock == null) return; // not tracked => allowed

        if (stock <= 0) throw new IllegalArgumentException("Out of stock");
        if (desiredQty > stock) throw new IllegalArgumentException("Not enough stock. Available: " + stock);
    }

    /* ==============================
       Internal helpers
       ============================== */

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
        if (cart == null) return;

        BigDecimal total = BigDecimal.ZERO;
        if (cart.getItems() != null) {
            for (CartItem ci : cart.getItems()) {
                BigDecimal unit = (ci.getUnitPrice() == null) ? BigDecimal.ZERO : ci.getUnitPrice();
                total = total.add(unit.multiply(BigDecimal.valueOf(ci.getQuantity())));
            }
        }
        cart.setTotalPrice(total);
        cart.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * Builds CartResponse with:
     * - item-level stock flags (disabled/outOfStock/exceeds...)
     * - cart-level canCheckout + blockingErrors + checkoutTotalPrice
     */
    private CartResponse toResponseWithStockValidation(Cart cart) {
        CartResponse res = new CartResponse();
        res.setCartId(cart.getId());
        res.setStatus(cart.getStatus().name());
        res.setTotalPrice(cart.getTotalPrice());

        String symbol = null;
        if (cart.getCurrency() != null) {
            try { symbol = cart.getCurrency().getSymbol(); } catch (Exception ignored) {}
        }
        res.setCurrencySymbol(symbol);

        List<CartItem> cartItems = (cart.getItems() == null) ? List.of() : cart.getItems();

        // fetch fresh items in bulk (soft check)
        List<Long> ids = cartItems.stream()
                .map(ci -> ci.getItem() != null ? ci.getItem().getId() : null)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, Item> freshById = itemRepo.findAllById(ids).stream()
                .collect(Collectors.toMap(Item::getId, x -> x));

        boolean canCheckout = true;
        Set<String> blockingErrors = new LinkedHashSet<>();
        BigDecimal checkoutTotal = BigDecimal.ZERO;

        List<CartItemResponse> itemResponses = new ArrayList<>();

        for (CartItem ci : cartItems) {
            CartItemResponse r = new CartItemResponse();

            r.setCartItemId(ci.getId());
            Long itemId = (ci.getItem() != null) ? ci.getItem().getId() : null;
            r.setItemId(itemId);

            Item fresh = (itemId != null) ? freshById.get(itemId) : null;

            Object nameObj = tryGet(fresh != null ? fresh : ci.getItem(), "getName", "getItemName", "getTitle");
            r.setItemName(nameObj != null ? nameObj.toString() : null);

            Object imageObj = tryGet(fresh != null ? fresh : ci.getItem(), "getImageUrl", "getImage", "getPhotoUrl", "getThumbnailUrl");
            r.setImageUrl(imageObj != null ? imageObj.toString() : null);

            r.setQuantity(ci.getQuantity());
            r.setUnitPrice(ci.getUnitPrice() == null ? BigDecimal.ZERO : ci.getUnitPrice());
            r.setLineTotal(r.getUnitPrice().multiply(BigDecimal.valueOf(ci.getQuantity())));

            // --- Stock flags ---
            boolean disabled = false;
            String reason = null;

            Integer stock = null;
            boolean outOfStock = false;
            boolean exceeds = false;
            Integer maxAllowed = null;

            if (fresh == null) {
                disabled = true;
                reason = "Item not available anymore";
                blockingErrors.add("Some items are no longer available");
            } else {
                stock = readStock(fresh);
                if (stock != null) {
                    maxAllowed = stock;

                    if (stock <= 0) {
                        outOfStock = true;
                        disabled = true;
                        reason = "Out of stock";
                        blockingErrors.add("Out of stock items exist");
                    } else if (ci.getQuantity() > stock) {
                        exceeds = true;
                        disabled = true;
                        reason = "Only " + stock + " left";
                        blockingErrors.add("Some items exceed available stock");
                    }
                }
            }

            r.setAvailableStock(stock);
            r.setOutOfStock(outOfStock);
            r.setQuantityExceedsStock(exceeds);
            r.setMaxAllowedQuantity(maxAllowed);
            r.setDisabled(disabled);
            r.setBlockingReason(reason);

            if (disabled) {
                canCheckout = false; // Shein-style: block checkout until fixed
            } else {
                checkoutTotal = checkoutTotal.add(r.getLineTotal());
            }

            itemResponses.add(r);
        }

        // rule: false if any disabled OR no valid items
        if (checkoutTotal.compareTo(BigDecimal.ZERO) <= 0) {
            canCheckout = false;
            if (!cartItems.isEmpty()) {
                blockingErrors.add("No valid items to checkout");
            }
        }

        res.setItems(itemResponses);
        res.setCanCheckout(canCheckout);
        res.setBlockingErrors(new ArrayList<>(blockingErrors));
        res.setCheckoutTotalPrice(checkoutTotal);

        return res;
    }

    /* ==============================
       Public API
       ============================== */

    @Override
    public CartResponse getMyCart(Long userId) {
        Cart cart = getOrCreateActiveCart(userId);
        recalcTotals(cart);
        return toResponseWithStockValidation(cart);
    }

    @Override
    public CartResponse addToCart(Long userId, AddToCartRequest request) {
        if (request == null) throw new IllegalArgumentException("request is required");
        if (request.getItemId() == null) throw new IllegalArgumentException("itemId is required");
        if (request.getQuantity() <= 0) throw new IllegalArgumentException("Quantity must be > 0");

        Cart cart = getOrCreateActiveCart(userId);

        // ✅ lock & fresh stock check
        Item item = itemRepo.findByIdForStockCheck(request.getItemId())
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));

        // currency set on first item
        if (cart.getCurrency() == null && item.getCurrency() != null) {
            cart.setCurrency(item.getCurrency());
        }

        CartItem existing = cart.getItems().stream()
                .filter(ci -> ci.getItem() != null && ci.getItem().getId().equals(item.getId()))
                .findFirst()
                .orElse(null);

        if (existing != null) {
            int desiredQty = existing.getQuantity() + request.getQuantity();
            assertStockAvailable(item, desiredQty);

            existing.setQuantity(desiredQty);
            existing.setUpdatedAt(LocalDateTime.now());
        } else {
            assertStockAvailable(item, request.getQuantity());

            CartItem ci = new CartItem();
            ci.setItem(item);
            ci.setQuantity(request.getQuantity());

            BigDecimal unitPrice = item.getEffectivePrice() == null ? BigDecimal.ZERO : item.getEffectivePrice();
            ci.setUnitPrice(unitPrice);

            ci.setCurrency(item.getCurrency());
            ci.setCreatedAt(LocalDateTime.now());
            ci.setUpdatedAt(LocalDateTime.now());
            cart.addItem(ci);
        }

        recalcTotals(cart);
        cartRepo.save(cart);
        return toResponseWithStockValidation(cart);
    }

    @Override
    public CartResponse updateCartItem(Long userId, Long cartItemId, UpdateCartItemRequest request) {
        if (request == null) throw new IllegalArgumentException("request is required");

        Cart cart = getOrCreateActiveCart(userId);

        CartItem ci = cart.getItems().stream()
                .filter(x -> x.getId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found or not in your cart"));

        if (request.getQuantity() <= 0) {
            cart.removeItem(ci);
            cartItemRepo.delete(ci);
        } else {
            Long itemId = (ci.getItem() != null) ? ci.getItem().getId() : null;
            if (itemId == null) throw new IllegalStateException("Cart item has no associated product");

            Item fresh = itemRepo.findByIdForStockCheck(itemId)
                    .orElseThrow(() -> new IllegalArgumentException("Item not found"));

            assertStockAvailable(fresh, request.getQuantity());

            ci.setQuantity(request.getQuantity());
            ci.setUpdatedAt(LocalDateTime.now());
        }

        recalcTotals(cart);
        cartRepo.save(cart);
        return toResponseWithStockValidation(cart);
    }

    @Override
    public CartResponse removeCartItem(Long userId, Long cartItemId) {
        Cart cart = getOrCreateActiveCart(userId);

        CartItem ci = cart.getItems().stream()
                .filter(x -> x.getId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found or not in your cart"));

        cart.removeItem(ci);
        cartItemRepo.delete(ci);

        recalcTotals(cart);
        cartRepo.save(cart);
        return toResponseWithStockValidation(cart);
    }

    @Override
    public void clearCart(Long userId) {
        Cart cart = cartRepo.findByUser_IdAndStatus(userId, CartStatus.ACTIVE).orElse(null);
        if (cart == null) return;

        cartItemRepo.deleteByCart_Id(cart.getId());
        cart.getItems().clear();

        recalcTotals(cart);
        cartRepo.save(cart);
    }

    @Override
    public Long checkout(Long userId,
                         String paymentMethod,
                         String stripePaymentId,
                         Long currencyId,
                         String couponCode) {

        Cart cart = cartRepo.findByUser_IdAndStatus(userId, CartStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("No active cart"));

        if (cart.getItems() == null || cart.getItems().isEmpty())
            throw new IllegalStateException("Cart is empty");

        if (paymentMethod == null || paymentMethod.isBlank())
            throw new IllegalArgumentException("paymentMethod is required");

        Long effectiveCurrencyId = currencyId;
        if (effectiveCurrencyId == null) {
            if (cart.getCurrency() != null && cart.getCurrency().getId() != null) {
                effectiveCurrencyId = cart.getCurrency().getId();
            } else {
                throw new IllegalArgumentException("currencyId is required");
            }
        }

        // 1) Shein-style: block checkout if cart has disabled lines
        CartResponse snapshot = toResponseWithStockValidation(cart);
        if (!snapshot.isCanCheckout()) {
            String msg = (snapshot.getBlockingErrors() != null && !snapshot.getBlockingErrors().isEmpty())
                    ? String.join(" | ", snapshot.getBlockingErrors())
                    : "Fix cart before checkout";
            throw new IllegalArgumentException(msg);
        }

        // 2) Final hard stock gate with lock (no race)
        for (CartItem ci : cart.getItems()) {
            Long itemId = (ci.getItem() != null) ? ci.getItem().getId() : null;
            if (itemId == null) throw new IllegalStateException("Cart item has no associated product");

            Item fresh = itemRepo.findByIdForStockCheck(itemId)
                    .orElseThrow(() -> new IllegalArgumentException("Item not found"));

            assertStockAvailable(fresh, ci.getQuantity());
        }

        List<CartLine> lines = cart.getItems().stream().map(ci -> {
            Item item = ci.getItem();
            if (item == null || item.getId() == null) {
                throw new IllegalStateException("Cart item has no associated product");
            }
            CartLine line = new CartLine();
            line.setItemId(item.getId());
            line.setQuantity(ci.getQuantity());
            line.setUnitPrice(ci.getUnitPrice());
            return line;
        }).toList();

        CheckoutRequest checkoutRequest = new CheckoutRequest();
        checkoutRequest.setLines(lines);
        checkoutRequest.setCurrencyId(effectiveCurrencyId);
        checkoutRequest.setPaymentMethod(paymentMethod);
        checkoutRequest.setStripePaymentId(stripePaymentId);
        checkoutRequest.setCouponCode(couponCode);
        checkoutRequest.setShippingAddress(null);

        CheckoutSummaryResponse summary = orderService.checkout(userId, checkoutRequest);

        // ⚠️ IMPORTANT: avoid double clearing.
        // Choose ONE place to clear/convert cart:
        // If OrderService.checkout() already clears cart, DO NOT clear here.

        return summary.getOrderId();
    }
}
