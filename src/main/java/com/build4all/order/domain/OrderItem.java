package com.build4all.order.domain;

import com.build4all.catalog.domain.Item;
import com.build4all.catalog.domain.Currency;
import com.build4all.user.domain.Users;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * OrderItem (order_items table)
 *
 * Represents one "line" inside an Order:
 * - Which product/activity/service was ordered (Item)
 * - Quantity
 * - Unit price captured at purchase time
 * - Currency used for this line (usually same as Order currency)
 * - Who ordered it (User)
 *
 * Why we store price here:
 * - Item price can change later (discounts, price updates).
 * - Order history must keep the exact unit price that was used when the order was created.
 *
 * Notes:
 * - All relations are LAZY to avoid heavy loading.
 * - @JsonIgnore is used to prevent circular references and huge payloads
 *   (Order -> OrderItems -> Order -> ...).
 */
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity
@Table(name = "order_items")
public class OrderItem {

    /** Primary key for the line item */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 🔗 Many OrderItems belong to one Order
    /**
     * Parent order header.
     * This is the FK column order_id in order_items table.
     * LAZY because the header is not always needed when listing items.
     * @JsonIgnore avoids recursion in JSON (order -> items -> order -> ...).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    private Order order;

    // 🔗 Many OrderItems belong to one Item
    /**
     * The ordered catalog item (could be ecommerce Product, Activity, Service...).
     * FK: item_id.
     * @JsonIgnore to avoid exposing full item graph in the order response
     * (you already shape the response in OrderController).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    @JsonIgnore
    private Item item;

    // 🔗 currency
    /**
     * Currency for this order line.
     * In your current flow you set it to the order currency (same currency for all lines).
     * Keeping it here gives flexibility later if you ever allow mixed-currency (usually you won't),
     * and makes reporting easier.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id")
    @JsonIgnore
    private Currency currency;

    // 🔗 user who ordered this item
    /**
     * The user who purchased this line.
     * This duplicates information from Order.user but is useful for:
     * - quick filtering at OrderItem level
     * - business reports and queries without joining orders
     *
     * FK: user_id.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private Users user;

    // Extra details

    /**
     * Quantity ordered.
     * For activities this can represent participants/seats.
     * For ecommerce products this is the item quantity.
     */
    private int quantity;

    /**
     * Unit price at the moment of checkout (NOT total).
     * Total line amount is typically: price * quantity.
     */
    private BigDecimal price;

    /**
     * When this line item was created.
     * You set it explicitly during checkout, but it also defaults to now.
     */
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Last update timestamp for this line.
     * In your code you update it during status flips / actions.
     */
    private LocalDateTime updatedAt = LocalDateTime.now();

    // ---------- Getters & Setters ----------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }

    public Item getItem() { return item; }
    public void setItem(Item item) { this.item = item; }

    public Users getUser() { return user; }
    public void setUser(Users user) { this.user = user; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Currency getCurrency() { return currency; }
    public void setCurrency(Currency currency) { this.currency = currency; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
