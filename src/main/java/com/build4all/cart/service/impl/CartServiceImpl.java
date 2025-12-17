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
import java.util.List;

@Service
@Transactional
public class CartServiceImpl implements CartService {

    // Cart header repository (Cart table)
    private final CartRepository cartRepo;

    // Cart lines repository (CartItem table)
    private final CartItemRepository cartItemRepo;

    // Used to validate the cart owner exists when creating a new cart
    private final UsersRepository usersRepo;

    // Used to load items/products when adding to cart
    private final ItemRepository itemRepo;

    // Delegation to central checkout/order logic (pricing + order creation)
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
       Reflection helper (like in OrderController)
       ============================== */

    /**
     * Tries to call a getter method from a list of candidate method names.
     * This is useful because different Item subclasses might expose different getters:
     * - getName / getTitle / getItemName
     * - getImageUrl / getImage / getThumbnailUrl
     *
     * We keep it "safe" (catch exceptions) so the API never crashes due to missing methods.
     */
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

    /**
     * Returns the user's ACTIVE cart.
     * If none exists, creates a new cart with status ACTIVE and returns it.
     *
     * Why this helper?
     * - Keeps all public methods consistent (always operate on ACTIVE cart)
     * - Centralizes "create if missing" behavior in one place
     */
    private Cart getOrCreateActiveCart(Long userId) {
        return cartRepo.findByUser_IdAndStatus(userId, CartStatus.ACTIVE)
                .orElseGet(() -> {
                    // Validate user exists (cart must have an owner)
                    Users user = usersRepo.findById(userId)
                            .orElseThrow(() -> new IllegalArgumentException("User not found"));

                    // Create cart header
                    Cart cart = new Cart();
                    cart.setUser(user);
                    cart.setStatus(CartStatus.ACTIVE);
                    cart.setTotalPrice(BigDecimal.ZERO);
                    cart.setCreatedAt(LocalDateTime.now());
                    cart.setUpdatedAt(LocalDateTime.now());
                    return cartRepo.save(cart);
                });
    }

    /**
     * Recalculates the cart header total from its items:
     * total = Σ (unitPrice * quantity)
     *
     * Notes:
     * - unitPrice is the price "captured" when the item was added to cart.
     * - We update updatedAt on each recalculation.
     */
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

    /**
     * Maps Cart entity → CartResponse DTO (API response shape).
     *
     * Why not return entities?
     * - Avoid LazyInitialization issues
     * - Avoid exposing internal JPA model
     * - Provide a stable and frontend-friendly JSON structure
     */
    private CartResponse toResponse(Cart cart) {
        CartResponse res = new CartResponse();
        res.setCartId(cart.getId());
        res.setStatus(cart.getStatus().name());
        res.setTotalPrice(cart.getTotalPrice());

        // currency symbol is optional (cart may not have currency set yet)
        String symbol = null;
        if (cart.getCurrency() != null) {
            try {
                symbol = cart.getCurrency().getSymbol();
            } catch (Exception ignored) {}
        }
        res.setCurrencySymbol(symbol);

        // Build item responses
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

            // Quantity + pricing
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

    /**
     * Returns the user's cart (creates one if not found).
     * Always recalculates totals before returning.
     */
    @Override
    public CartResponse getMyCart(Long userId) {
        Cart cart = getOrCreateActiveCart(userId);
        recalcTotals(cart);
        return toResponse(cart);
    }

    /**
     * Adds an item to the cart.
     *
     * Behavior:
     * - If item already exists in cart → increments quantity
     * - Else → creates a new CartItem line
     * - Captures unit price at time of adding (important if prices can change later)
     * - Recalculates totals and saves cart
     */
    @Override
    public CartResponse addToCart(Long userId, AddToCartRequest request) {
        if (request.getQuantity() <= 0)
            throw new IllegalArgumentException("Quantity must be > 0");

        Cart cart = getOrCreateActiveCart(userId);

        // Load item from DB (ensures it exists and we have its currency/price)
        Item item = itemRepo.findById(request.getItemId())
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));

        // set cart currency from first item if null
        // (prevents mixing currencies accidentally)
        if (cart.getCurrency() == null && item.getCurrency() != null) {
            cart.setCurrency(item.getCurrency());
        }

        // if item already in cart → update quantity
        CartItem existing = cart.getItems().stream()
                .filter(ci -> ci.getItem().getId().equals(item.getId()))
                .findFirst()
                .orElse(null);

        if (existing != null) {
            // Increase quantity, preserve captured unitPrice
            existing.setQuantity(existing.getQuantity() + request.getQuantity());
        } else {
            // Create new line
            CartItem ci = new CartItem();
            ci.setItem(item);
            ci.setQuantity(request.getQuantity());

            // Capture unit price at time of adding to cart
            // (in ecommerce you may later want to use "effective price" if item is Product with discount)
            ci.setUnitPrice(item.getPrice() == null ? BigDecimal.ZERO : item.getPrice());

            ci.setCurrency(item.getCurrency());
            ci.setCreatedAt(LocalDateTime.now());
            ci.setUpdatedAt(LocalDateTime.now());
            cart.addItem(ci); // also sets back-reference + updates cart timestamp
        }

        recalcTotals(cart);
        cartRepo.save(cart);
        return toResponse(cart);
    }

    /**
     * Updates quantity for a cart item.
     *
     * Rules:
     * - cartItemId must belong to the user's ACTIVE cart
     * - quantity <= 0 means remove the item
     */
    @Override
    public CartResponse updateCartItem(Long userId, Long cartItemId, UpdateCartItemRequest request) {
        Cart cart = getOrCreateActiveCart(userId);

        CartItem ci = cart.getItems().stream()
                .filter(x -> x.getId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found or not in your cart"));

        if (request.getQuantity() <= 0) {
            // Remove from cart and delete line
            cart.removeItem(ci);
            cartItemRepo.delete(ci);
        } else {
            // Update quantity
            ci.setQuantity(request.getQuantity());
            ci.setUpdatedAt(LocalDateTime.now());
        }

        recalcTotals(cart);
        cartRepo.save(cart);
        return toResponse(cart);
    }

    /**
     * Removes a cart item completely from the user's ACTIVE cart.
     */
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

    /**
     * Clears all cart items and resets totals.
     * Safe behavior: if cart doesn't exist, do nothing.
     */
    @Override
    public void clearCart(Long userId) {
        Cart cart = cartRepo.findByUser_IdAndStatus(userId, CartStatus.ACTIVE)
                .orElse(null);
        if (cart == null) return;

        // Bulk delete (fast)
        cartItemRepo.deleteByCart_Id(cart.getId());

        // Keep entity state consistent in memory
        cart.getItems().clear();

        recalcTotals(cart);
        cartRepo.save(cart);
    }

    /**
     * Converts the user's ACTIVE cart into an Order by delegating to OrderService.checkout,
     * so that tax, shipping, and coupon logic are applied in one place.
     */
    @Override
    public Long checkout(Long userId,
                         String paymentMethod,
                         String stripePaymentId,
                         Long currencyId,
                         String couponCode) {

        // Load active cart (must exist)
        Cart cart = cartRepo.findByUser_IdAndStatus(userId, CartStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("No active cart"));

        if (cart.getItems() == null || cart.getItems().isEmpty())
            throw new IllegalStateException("Cart is empty");

        // Validate user exists (also helps with clearer error message)
        Users user = usersRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (paymentMethod == null || paymentMethod.isBlank()) {
            throw new IllegalArgumentException("paymentMethod is required");
        }

        // Determine effective currencyId:
        // - Prefer explicit currencyId argument
        // - Else fallback to cart currency
        Long effectiveCurrencyId = currencyId;
        if (effectiveCurrencyId == null) {
            if (cart.getCurrency() != null && cart.getCurrency().getId() != null) {
                effectiveCurrencyId = cart.getCurrency().getId();
            } else {
                throw new IllegalArgumentException("currencyId is required");
            }
        }

        // Map CartItems → CartLine list for CheckoutRequest
        List<CartLine> lines = cart.getItems().stream().map(ci -> {
            Item item = ci.getItem();
            if (item == null || item.getId() == null) {
                throw new IllegalStateException("Cart item has no associated product");
            }

            CartLine line = new CartLine();
            line.setItemId(item.getId());
            line.setQuantity(ci.getQuantity());

            // Optionally pass unit price; OrderService may still recompute from DB
            // (In your OrderServiceImpl.checkout, you recompute unitPrice anyway)
            line.setUnitPrice(ci.getUnitPrice());

            return line;
        }).toList();

        // Build CheckoutRequest for OrderService
        CheckoutRequest checkoutRequest = new CheckoutRequest();
        checkoutRequest.setLines(lines);
        checkoutRequest.setCurrencyId(effectiveCurrencyId);
        checkoutRequest.setPaymentMethod(paymentMethod);

        // Legacy field: stripePaymentId (depending on your new flow it might be unused)
        checkoutRequest.setStripePaymentId(stripePaymentId);

        // Coupon code (optional)
        checkoutRequest.setCouponCode(couponCode);

        // If you later store shipping on Cart, you can map it here:
        // ShippingAddressDTO addr = new ShippingAddressDTO();
        // addr.setCountryId(cart.getShippingCountryId());
        // addr.setRegionId(cart.getShippingRegionId());
        // addr.setCity(cart.getShippingCity());
        // addr.setPostalCode(cart.getShippingPostalCode());
        // checkoutRequest.setShippingAddress(addr);
        // checkoutRequest.setShippingMethodId(cart.getShippingMethodId());
        checkoutRequest.setShippingAddress(null);

        // You can plug Stripe validation here if needed (similar to OrderServiceImpl)

        // Delegate to OrderService so tax, shipping, coupon etc. are all applied there
        CheckoutSummaryResponse summary = orderService.checkout(userId, checkoutRequest);

        // mark cart as converted & clear items
        // IMPORTANT: In the "new payment flow", you might prefer clearing cart only after payment is confirmed.
        // Your OrderServiceImpl.checkout already clears cart after starting payment successfully.
        // If both places clear cart, you may end up doing it twice.
        cart.setStatus(CartStatus.CONVERTED);
        cartItemRepo.deleteByCart_Id(cart.getId());
        cart.getItems().clear();
        recalcTotals(cart);
        cartRepo.save(cart);

        return summary.getOrderId();
    }
}
