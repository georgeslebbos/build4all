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

    private final CartService cartService;
    private final JwtUtil jwt;

    public CartController(CartService cartService, JwtUtil jwt) {
        this.cartService = cartService;
        this.jwt = jwt;
    }

    private String strip(String auth) {
        if (auth == null) return "";
        return auth.replace("Bearer ", "").trim();
    }

    // ===================== READ CART =====================

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

        String paymentMethod   = body.getOrDefault("paymentMethod",   "UNKNOWN").toString();
        String stripePaymentId = body.getOrDefault("stripePaymentId", "").toString();
        Long currencyId = body.get("currencyId") != null
                ? Long.valueOf(body.get("currencyId").toString())
                : null;

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
        return ResponseEntity.ok(Map.of("orderId", orderId));
    }

    // ===================== ERROR HANDLERS =====================

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> serverError(Exception ex) {
        return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
    }
}
