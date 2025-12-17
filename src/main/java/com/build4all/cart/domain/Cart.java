package com.build4all.cart.domain;

import com.build4all.catalog.domain.Currency;
import com.build4all.user.domain.Users;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Cart (Shopping Cart / Booking Cart)
 *
 * Represents the current “basket” of a user before checkout.
 * It holds CartItem rows (each CartItem points to an Item + quantity + unitPrice, etc.)
 *
 * Typical flow:
 * 1) User adds/removes items -> Cart remains ACTIVE
 * 2) User calls /api/orders/checkout -> Order is created from cart lines
 * 3) Cart becomes CONVERTED and its items are cleared (to prevent double checkout)
 *
 * Notes:
 * - We keep currency on Cart to ensure prices are consistent for the user's session.
 * - totalPrice is a cached/denormalized value for fast display (should be recalculated when items change).
 * - status enables lifecycle control (ACTIVE -> CONVERTED, maybe ABANDONED later).
 */
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity
@Table(name = "carts")
public class Cart {

    /**
     * Primary key for the cart table.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_id")
    private Long id;

    /**
     * owner of the cart
     *
     * Many carts belong to one user (historically), but in practice you usually keep only one ACTIVE cart.
     *
     * fetch = LAZY:
     * - avoids loading user object every time you load a cart
     *
     * @JsonIgnore:
     * - prevents infinite recursion / huge payloads when serializing Cart to JSON
     *   (User -> Orders -> Items -> Cart ... etc.)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private Users user;

    /**
     * The list of items currently in the cart.
     *
     * mappedBy = "cart":
     * - CartItem has the FK column to carts (cart_id)
     *
     * cascade = ALL:
     * - saving cart will save its items
     * - deleting cart will delete its items
     *
     * orphanRemoval = true:
     * - if an item is removed from the list, it will be deleted from DB
     *   (very convenient for cart behavior)
     */
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItem> items = new ArrayList<>();

    /**
     * Currency used for this cart.
     *
     * This helps:
     * - ensure UI shows correct symbol/code
     * - avoid mixing multiple currencies in one cart
     *
     * OnDelete(CASCADE):
     * - if currency row is deleted, carts referencing it are removed.
     *   (Be careful: in production, currencies are usually never deleted.)
     */
    @ManyToOne
    @JoinColumn(name = "currency_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Currency currency;

    /**
     * Cart status (lifecycle).
     *
     * Stored as STRING in DB (ACTIVE, CONVERTED, ...),
     * so values are readable and stable across enum ordering changes.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CartStatus status = CartStatus.ACTIVE;

    /**
     * Cached total price for the cart.
     *
     * This is usually recalculated when:
     * - item quantity changes
     * - items are added/removed
     * - unit prices change
     *
     * Note:
     * - Your OrderServiceImpl has recalcTotals(cart) which recomputes from CartItem rows.
     */
    @Column(name = "total_price", precision = 10, scale = 2)
    private BigDecimal totalPrice = BigDecimal.ZERO;

    /**
     * Audit timestamps.
     * In many systems you may also replace these with @PrePersist/@PreUpdate.
     */
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    /* ===============================
       helpers
       =============================== */

    /**
     * Adds a CartItem and sets the back-reference (CartItem.cart).
     * Also updates updatedAt timestamp.
     */
    public void addItem(CartItem item) {
        items.add(item);
        item.setCart(this);
        touch();
    }

    /**
     * Removes a CartItem and clears the back-reference.
     * With orphanRemoval=true, the row will be deleted from DB when persisted.
     * Also updates updatedAt timestamp.
     */
    public void removeItem(CartItem item) {
        items.remove(item);
        item.setCart(null);
        touch();
    }

    /**
     * Updates the "updatedAt" timestamp.
     * You call this after any change to cart or items.
     */
    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    /* ===============================
       getters & setters
       =============================== */

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Users getUser() { return user; }
    public void setUser(Users user) { this.user = user; }

    public List<CartItem> getItems() { return items; }
    public void setItems(List<CartItem> items) { this.items = items; }

    public Currency getCurrency() { return currency; }
    public void setCurrency(Currency currency) { this.currency = currency; }

    public CartStatus getStatus() { return status; }
    public void setStatus(CartStatus status) { this.status = status; }

    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
