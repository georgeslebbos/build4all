package com.build4all.cart.web;

import com.build4all.cart.dto.AddToCartRequest;
import com.build4all.cart.dto.CartResponse;
import com.build4all.cart.dto.UpdateCartItemRequest;
import com.build4all.cart.service.CartService;
import com.build4all.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/cart")
@Tag(name = "Cart")
@SecurityRequirement(name = "bearerAuth")  // 🔐 same as ProductController
public class CartController {

    // Service layer that contains all cart business logic (CRUD cart, checkout, totals...)
    private final CartService cartService;

    // JWT utility used to extract the authenticated user's id from the Authorization header
    private final JwtUtil jwt;

    public CartController(CartService cartService, JwtUtil jwt) {
        this.cartService = cartService;
        this.jwt = jwt;
    }

    /**
     * Removes the "Bearer " prefix from the Authorization header and returns the raw JWT token.
     * - If header is null, returns empty string
     * - Keeps controller code clean by centralizing token stripping in one place
     */
    private String strip(String auth) {
        if (auth == null) return "";
        return auth.replace("Bearer ", "").trim();
    }

    // ===================== READ CART =====================

    /**
     * Returns the authenticated user's active cart.
     * - The user is resolved from JWT (no userId passed from the client)
     * - If the service creates a cart automatically when missing, frontend always receives a cart object
     */
    @GetMapping
    @Operation(
            summary = "Get my active cart",
            description = "Returns the active cart for the authenticated user"
    )
    public ResponseEntity<CartResponse> getMyCart(
            @RequestHeader("Authorization") String auth
    ) {
        Long userId = jwt.extractId(strip(auth));   // 🔐 user from JWT
        return ResponseEntity.ok(cartService.getMyCart(userId));
    }

    // ===================== ADD ITEM =====================

    /**
     * Adds a new item to the authenticated user's ACTIVE cart.
     * Behavior is in service:
     * - If same item already exists -> increments quantity
     * - Else -> adds a new CartItem line
     */
    @PostMapping("/items")
    @Operation(
            summary = "Add item to cart",
            description = "Adds an item to the authenticated user's active cart"
    )
    public ResponseEntity<CartResponse> addToCart(
            @RequestHeader("Authorization") String auth,
            @RequestBody AddToCartRequest req
    ) {
        Long userId = jwt.extractId(strip(auth));   // 🔐 user from JWT
        return ResponseEntity.ok(cartService.addToCart(userId, req));
    }

    // ===================== UPDATE ITEM =====================

    /**
     * Updates quantity of an existing cart item line.
     * Common rules (service-side):
     * - cartItemId must belong to this user's cart
     * - quantity <= 0 usually means "remove this line"
     */
    @PutMapping("/items/{cartItemId}")
    @Operation(
            summary = "Update quantity of cart item",
            description = "Updates the quantity of a given cart item for the authenticated user"
    )
    public ResponseEntity<CartResponse> updateCartItem(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long cartItemId,
            @RequestBody UpdateCartItemRequest req
    ) {
        Long userId = jwt.extractId(strip(auth));   // 🔐 user from JWT
        return ResponseEntity.ok(cartService.updateCartItem(userId, cartItemId, req));
    }

    // ===================== REMOVE ITEM =====================

    /**
     * Removes a single cart item line from the authenticated user's cart.
     * This is the explicit "delete line" operation.
     */
    @DeleteMapping("/items/{cartItemId}")
    @Operation(
            summary = "Remove item from cart",
            description = "Removes a specific item from the authenticated user's cart"
    )
    public ResponseEntity<CartResponse> removeCartItem(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long cartItemId
    ) {
        Long userId = jwt.extractId(strip(auth));   // 🔐 user from JWT
        return ResponseEntity.ok(cartService.removeCartItem(userId, cartItemId));
    }

    // ===================== CLEAR CART =====================

    /**
     * Clears the entire cart (removes all items).
     * - Implementation is in service (bulk delete + totals reset)
     * - Returns 204 No Content on success
     */
    @DeleteMapping
    @Operation(
            summary = "Clear my cart",
            description = "Removes all items from the authenticated user's cart"
    )
    public ResponseEntity<?> clearCart(
            @RequestHeader("Authorization") String auth
    ) {
        Long userId = jwt.extractId(strip(auth));   // 🔐 user from JWT
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }

    // ===================== CHECKOUT =====================

    /**
     * Converts the user's ACTIVE cart into an Order + OrderItems.
     *
     * Inputs are passed as a simple JSON map to keep the controller flexible.
     * Example request body:
     * {
     *   "paymentMethod": "STRIPE",
     *   "stripePaymentId": "pi_...",
     *   "currencyId": 1,
     *   "couponCode": "WELCOME10"
     * }
     *
     * NOTE:
     * - In your newer payment flow, stripePaymentId may no longer be required here.
     * - If OrderService.checkout starts payment and also clears the cart,
     *   be careful about "double clearing" in CartServiceImpl (if it also clears).
     */
    @PostMapping("/checkout")
    @Operation(
            summary = "Checkout cart and create order",
            description = "Converts the authenticated user's active cart to an Order + OrderItems"
    )
    public ResponseEntity<?> checkout(
            @RequestHeader("Authorization") String auth,
            @RequestBody Map<String, Object> body
    ) {
        Long userId = jwt.extractId(strip(auth));   // 🔐 user from JWT

        // paymentMethod is a string code (ex: STRIPE, CASH, PAYPAL...)
        // Default to UNKNOWN if not provided (service will typically reject it)
        String paymentMethod   = body.getOrDefault("paymentMethod",   "UNKNOWN").toString();

        // Legacy Stripe field (may be empty in new flow)
        String stripePaymentId = body.getOrDefault("stripePaymentId", "").toString();

        // currencyId is optional here because CartServiceImpl can fall back to cart.currency
        Long currencyId = body.get("currencyId") != null
                ? Long.valueOf(body.get("currencyId").toString())
                : null;

        // Optional coupon for discounts
        String couponCode = body.get("couponCode") != null
                ? body.get("couponCode").toString()
                : null;

        Long orderId = cartService.checkout(
                userId,
                paymentMethod,
                stripePaymentId,
                currencyId,
                couponCode
        );

        // Return a minimal response (frontend can call /api/orders/... for details if needed)
        return ResponseEntity.ok(Map.of("orderId", orderId));
    }

    // ===================== ERROR HANDLERS =====================

    /**
     * Handles expected validation errors (bad input, missing required fields, etc.)
     * Returns HTTP 400 with a simple JSON error object.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    /**
     * Handles unexpected errors.
     * Returns HTTP 500 with a simple JSON error object.
     * (In production, you might want to hide internal messages and log them instead.)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> serverError(Exception ex) {
        return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
    }
}
