package com.build4all.cart.domain;

import com.build4all.catalog.domain.Currency;
import com.build4all.catalog.domain.Item;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CartItem (line inside a Cart)
 *
 * Each CartItem represents one selected Item + quantity (and captured unitPrice) inside a Cart.
 *
 * Why store unitPrice here?
 * - So the cart can keep a consistent price snapshot for the user session
 * - Even if the Item price changes later (admin updates price / discounts / etc.)
 *
 * Note:
 * - The final “truth” at checkout is recalculated again by OrderService + CheckoutPricingService
 *   (shipping, taxes, coupons, etc.), but having unitPrice here helps for quick cart rendering.
 */
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity
@Table(name = "cart_items")
public class CartItem {

    /**
     * Primary key for cart_items table.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_item_id")
    private Long id;

    /**
     * Parent cart reference (FK cart_id).
     *
     * fetch = LAZY:
     * - load cart only when needed
     *
     * @JsonIgnore:
     * - prevents recursion: Cart -> items -> cart -> items ...
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    @JsonIgnore
    private Cart cart;

    /**
     * The catalog Item being added to the cart.
     *
     * This can represent:
     * - Activity
     * - Ecommerce product
     * - Service / booking item
     *
     * @JsonIgnore:
     * - keeps API payload small
     * - avoids recursion / lazy proxy serialization issues
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    @JsonIgnore
    private Item item;

    /**
     * Currency used for this cart line.
     *
     * In many cases this matches Cart.currency.
     * It's kept here to allow future flexibility (or to store snapshot info),
     * but you can also choose to rely only on Cart.currency.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id")
    @JsonIgnore
    private Currency currency;

    /**
     * Quantity selected for this line.
     * For activities: number of seats/participants.
     * For ecommerce: number of units.
     */
    @Column(nullable = false)
    private int quantity;

    /**
     * price at time of adding to cart (unit price)
     *
     * Snapshot of the unit price when user added the item.
     * Useful for:
     * - showing cart totals quickly
     * - tracking what user saw at the time
     *
     * Final pricing is validated again during checkout (tax/shipping/coupons).
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal unitPrice;

    /**
     * Audit timestamps.
     * You update updatedAt when quantity changes.
     */
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    /* ===============================
       getters & setters
       =============================== */

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Cart getCart() { return cart; }
    public void setCart(Cart cart) { this.cart = cart; }

    public Item getItem() { return item; }
    public void setItem(Item item) { this.item = item; }

    public Currency getCurrency() { return currency; }
    public void setCurrency(Currency currency) { this.currency = currency; }

    public int getQuantity() { return quantity; }

    /**
     * Updates the quantity and refreshes updatedAt.
     * This is helpful because quantity is the main mutable field in cart lines.
     */
    public void setQuantity(int quantity) {
        this.quantity = quantity;
        this.updatedAt = LocalDateTime.now();
    }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
