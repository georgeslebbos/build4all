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
import com.build4all.catalog.domain.Currency;
import com.build4all.catalog.domain.Item;
import com.build4all.catalog.repository.CurrencyRepository;
import com.build4all.catalog.repository.ItemRepository;
import com.build4all.order.domain.Order;
import com.build4all.order.domain.OrderItem;
import com.build4all.order.domain.OrderStatus;
import com.build4all.order.repository.OrderItemRepository;
import com.build4all.order.repository.OrderRepository;
import com.build4all.order.repository.OrderStatusRepository;
import com.build4all.user.domain.Users;
import com.build4all.user.repository.UsersRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepo;
    private final CartItemRepository cartItemRepo;
    private final UsersRepository usersRepo;
    private final ItemRepository itemRepo;
    private final CurrencyRepository currencyRepo;

    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final OrderStatusRepository orderStatusRepo;

    public CartServiceImpl(CartRepository cartRepo,
                           CartItemRepository cartItemRepo,
                           UsersRepository usersRepo,
                           ItemRepository itemRepo,
                           CurrencyRepository currencyRepo,
                           OrderRepository orderRepo,
                           OrderItemRepository orderItemRepo,
                           OrderStatusRepository orderStatusRepo) {
        this.cartRepo = cartRepo;
        this.cartItemRepo = cartItemRepo;
        this.usersRepo = usersRepo;
        this.itemRepo = itemRepo;
        this.currencyRepo = currencyRepo;
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.orderStatusRepo = orderStatusRepo;
    }

    /* ==============================
       Reflection helper (like in OrderController)
       ============================== */

    private Object tryGet(Object target, String... candidates) {
        if (target == null) return null;
        Class<?> c = target.getClass();
        for (String name : candidates) {
            try {
                var m = c.getMethod(name);
                return m.invoke(target);
            } catch (Exception ignored) { }
        }
        return null;
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
                BigDecimal unit = ci.getUnitPrice() == null ? BigDecimal.ZERO : ci.getUnitPrice();
                total = total.add(unit.multiply(BigDecimal.valueOf(ci.getQuantity())));
            }
        }
        cart.setTotalPrice(total);
        cart.setUpdatedAt(LocalDateTime.now());
    }

    private CartResponse toResponse(Cart cart) {
        CartResponse res = new CartResponse();
        res.setCartId(cart.getId());
        res.setStatus(cart.getStatus().name());
        res.setTotalPrice(cart.getTotalPrice());

        String symbol = null;
        if (cart.getCurrency() != null) {
            try {
                symbol = cart.getCurrency().getSymbol();
            } catch (Exception ignored) {}
        }
        res.setCurrencySymbol(symbol);

        List<CartItemResponse> items = cart.getItems().stream().map(ci -> {
            CartItemResponse r = new CartItemResponse();
            Item item = ci.getItem();

            r.setCartItemId(ci.getId());
            r.setItemId(item != null ? item.getId() : null);

            // Name: try several possible getters (like in OrderController)
            Object nameObj = tryGet(item, "getName", "getItemName", "getTitle");
            r.setItemName(nameObj != null ? nameObj.toString() : null);

            // Image URL: also try multiple common getters
            Object imageObj = tryGet(item, "getImageUrl", "getImage", "getPhotoUrl", "getThumbnailUrl");
            r.setImageUrl(imageObj != null ? imageObj.toString() : null);

            r.setQuantity(ci.getQuantity());
            r.setUnitPrice(ci.getUnitPrice() == null ? BigDecimal.ZERO : ci.getUnitPrice());
            r.setLineTotal(r.getUnitPrice().multiply(BigDecimal.valueOf(ci.getQuantity())));
            return r;
        }).toList();

        res.setItems(items);
        return res;
    }

    /* ==============================
       Public API (CartService)
       ============================== */

    @Override
    public CartResponse getMyCart(Long userId) {
        Cart cart = getOrCreateActiveCart(userId);
        recalcTotals(cart);
        return toResponse(cart);
    }

    @Override
    public CartResponse addToCart(Long userId, AddToCartRequest request) {
        if (request.getQuantity() <= 0)
            throw new IllegalArgumentException("Quantity must be > 0");

        Cart cart = getOrCreateActiveCart(userId);
        Item item = itemRepo.findById(request.getItemId())
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));

        // set cart currency from first item if null
        if (cart.getCurrency() == null && item.getCurrency() != null) {
            cart.setCurrency(item.getCurrency());
        }

        // if item already in cart → update quantity
        CartItem existing = cart.getItems().stream()
                .filter(ci -> ci.getItem().getId().equals(item.getId()))
                .findFirst()
                .orElse(null);

        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + request.getQuantity());
        } else {
            CartItem ci = new CartItem();
            ci.setItem(item);
            ci.setQuantity(request.getQuantity());
            ci.setUnitPrice(item.getPrice() == null ? BigDecimal.ZERO : item.getPrice());
            ci.setCurrency(item.getCurrency());
            ci.setCreatedAt(LocalDateTime.now());
            ci.setUpdatedAt(LocalDateTime.now());
            cart.addItem(ci);
        }

        recalcTotals(cart);
        cartRepo.save(cart);
        return toResponse(cart);
    }

    @Override
    public CartResponse updateCartItem(Long userId, Long cartItemId, UpdateCartItemRequest request) {
        Cart cart = getOrCreateActiveCart(userId);

        CartItem ci = cart.getItems().stream()
                .filter(x -> x.getId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found or not in your cart"));

        if (request.getQuantity() <= 0) {
            cart.removeItem(ci);
            cartItemRepo.delete(ci);
        } else {
            ci.setQuantity(request.getQuantity());
            ci.setUpdatedAt(LocalDateTime.now());
        }

        recalcTotals(cart);
        cartRepo.save(cart);
        return toResponse(cart);
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
        return toResponse(cart);
    }

    @Override
    public void clearCart(Long userId) {
        Cart cart = cartRepo.findByUser_IdAndStatus(userId, CartStatus.ACTIVE)
                .orElse(null);
        if (cart == null) return;

        cartItemRepo.deleteByCart_Id(cart.getId());
        cart.getItems().clear();
        recalcTotals(cart);
        cartRepo.save(cart);
    }

    @Override
    public Long checkout(Long userId, String paymentMethod, String stripePaymentId, Long currencyId) {
        Cart cart = cartRepo.findByUser_IdAndStatus(userId, CartStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("No active cart"));

        if (cart.getItems().isEmpty())
            throw new IllegalStateException("Cart is empty");

        Users user = usersRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Currency currency = null;
        if (currencyId != null) {
            currency = currencyRepo.findById(currencyId)
                    .orElseThrow(() -> new IllegalArgumentException("Currency not found"));
        } else if (cart.getCurrency() != null) {
            currency = cart.getCurrency();
        }

        // You can plug Stripe validation here if needed (similar to OrderServiceImpl)

        OrderStatus pending = orderStatusRepo.findByNameIgnoreCase("PENDING")
                .orElseThrow(() -> new IllegalStateException("OrderStatus PENDING not found"));

        // Create order header
        Order order = new Order();
        order.setUser(user);
        order.setStatus(pending);
        order.setOrderDate(LocalDateTime.now());
        recalcTotals(cart);
        order.setTotalPrice(cart.getTotalPrice());
        if (currency != null) order.setCurrency(currency);

        order = orderRepo.save(order);

        // Create order items from cart items
        for (CartItem ci : cart.getItems()) {
            OrderItem oi = new OrderItem();
            oi.setOrder(order);
            oi.setItem(ci.getItem());
            oi.setUser(user);
            oi.setQuantity(ci.getQuantity());
            oi.setPrice(ci.getUnitPrice());
            oi.setCurrency(currency != null ? currency : ci.getCurrency());
            oi.setCreatedAt(LocalDateTime.now());
            oi.setUpdatedAt(LocalDateTime.now());
            orderItemRepo.save(oi);
        }

        // mark cart as converted & clear items
        cart.setStatus(CartStatus.CONVERTED);
        cartItemRepo.deleteByCart_Id(cart.getId());
        cart.getItems().clear();
        recalcTotals(cart);
        cartRepo.save(cart);

        return order.getId();
    }
}
