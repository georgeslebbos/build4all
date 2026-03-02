package com.build4all.cart.web;

import com.build4all.cart.dto.AddToCartRequest;
import com.build4all.cart.dto.CartResponse;
import com.build4all.cart.dto.UpdateCartItemRequest;
import com.build4all.cart.service.CartService;
import com.build4all.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/cart")
@Tag(name = "Cart")
@PreAuthorize("hasRole('USER') or hasRole('SUPER_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
public class CartController {

    private final CartService cartService;
    private final JwtUtil jwt;

    public CartController(CartService cartService, JwtUtil jwt) {
        this.cartService = cartService;
        this.jwt = jwt;
    }

    /* ============================
     * AUTH HELPERS (STRICT)
     * ============================ */

    private String requireRawToken(String authHeader) {
        if (authHeader == null || authHeader.isBlank() || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing/invalid Authorization header");
        }
        return authHeader.replaceFirst("(?i)^Bearer\\s+", "").trim();
    }

    /**
     * ✅ Cart endpoints are USER-only.
     * - rejects BUSINESS/OWNER/SUPER_ADMIN tokens
     */
    private Long requireUserId(String authHeader) {
        String token = requireRawToken(authHeader);

        if (!jwt.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }

        if (!jwt.isUserToken(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden: USER token required");
        }

        return jwt.extractId(token);
    }

    /**
     * ✅ Enforce tenant presence in USER token (ownerProjectId claim).
     * This is your linkId / aup_id scope.
     */
    private Long requireTenantId(String authHeader) {
        // This method already validates and enforces tenant claim presence
        // (in your JwtUtil.requireOwnerProjectId).
        return jwt.requireOwnerProjectId(authHeader);
    }

    /* ============================
     * READ CART
     * ============================ */

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get my active cart", description = "Returns the active cart for the authenticated user")
    public ResponseEntity<CartResponse> getMyCart(
            @RequestHeader("Authorization") String authHeader
    ) {
        Long tenantId = requireTenantId(authHeader); // ✅ tenant scope enforced
        Long userId = requireUserId(authHeader);     // ✅ user-only

        // (Optional) if you later update service signature: cartService.getMyCart(tenantId, userId)
        return ResponseEntity.ok(cartService.getMyCart(userId));
    }

    /* ============================
     * ADD ITEM
     * ============================ */

    @PostMapping(value = "/items", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Add item to cart", description = "Adds an item to the authenticated user's active cart")
    public ResponseEntity<CartResponse> addToCart(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody AddToCartRequest req
    ) {
        Long tenantId = requireTenantId(authHeader);
        Long userId = requireUserId(authHeader);

        return ResponseEntity.ok(cartService.addToCart(userId, req));
    }

    /* ============================
     * UPDATE ITEM
     * ============================ */

    @PutMapping(value = "/items/{cartItemId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update quantity of cart item", description = "Updates the quantity of a given cart item for the authenticated user")
    public ResponseEntity<CartResponse> updateCartItem(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long cartItemId,
            @RequestBody UpdateCartItemRequest req
    ) {
        Long tenantId = requireTenantId(authHeader);
        Long userId = requireUserId(authHeader);

        return ResponseEntity.ok(cartService.updateCartItem(userId, cartItemId, req));
    }

    /* ============================
     * REMOVE ITEM
     * ============================ */

    @DeleteMapping(value = "/items/{cartItemId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Remove item from cart", description = "Removes a specific item from the authenticated user's cart")
    public ResponseEntity<CartResponse> removeCartItem(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long cartItemId
    ) {
        Long tenantId = requireTenantId(authHeader);
        Long userId = requireUserId(authHeader);

        return ResponseEntity.ok(cartService.removeCartItem(userId, cartItemId));
    }

    /* ============================
     * CLEAR CART
     * ============================ */

    @DeleteMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Clear my cart", description = "Removes all items from the authenticated user's cart")
    public ResponseEntity<?> clearCart(
            @RequestHeader("Authorization") String authHeader
    ) {
        Long tenantId = requireTenantId(authHeader);
        Long userId = requireUserId(authHeader);

        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }

    /* ============================
     * CHECKOUT
     * ============================ */

    @PostMapping(value = "/checkout", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Checkout cart and create order", description = "Converts the authenticated user's active cart to an Order + OrderItems")
    public ResponseEntity<?> checkout(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> body
    ) {
        Long tenantId = requireTenantId(authHeader);
        Long userId = requireUserId(authHeader);

        String paymentMethod = body.getOrDefault("paymentMethod", "UNKNOWN").toString();
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

    /* ============================
     * ERROR HANDLERS
     * ============================ */

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> status(ResponseStatusException ex) {
        HttpStatus status = (HttpStatus) ex.getStatusCode();
        return ResponseEntity.status(status).body(Map.of("error", ex.getReason()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> serverError(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error", "details", ex.getClass().getSimpleName()));
    }
}