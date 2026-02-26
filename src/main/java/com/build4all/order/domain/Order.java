package com.build4all.order.domain;

import com.build4all.catalog.domain.Currency;
import com.build4all.catalog.domain.Country;
import com.build4all.catalog.domain.Region;
import com.build4all.user.domain.Users;
import com.build4all.payment.domain.PaymentMethod; // 🔹 NEW import
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Order (orders table)
 *
 * Represents the "order header" record:
 * - Who placed the order (user)
 * - Current status (PENDING/COMPLETED/...)
 * - Totals (grand total, shipping, taxes, coupon)
 * - Shipping destination/method
 * - Payment method chosen (STRIPE/CASH/...)
 *
 * One Order has many OrderItem lines.
 *
 * Notes:
 * - We use LAZY relations to avoid loading heavy objects automatically.
 * - Some fields are @JsonIgnore to avoid infinite recursion in JSON serialization
 *   and to keep API responses light.
 */
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity
@Table(name = "orders")
public class Order {

    /** Primary key for the order header (order_id) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long id;

    // 🔗 user who owns the order
    /**
     * The user who placed/owns this order.
     * LAZY: fetched only when needed.
     * @JsonIgnore: prevents exposing full user entity in order JSON
     *             and avoids circular references.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private Users user;

    // 🔗 one order -> many orderItems
    /**
     * Order lines.
     * mappedBy="order": OrderItem has the FK to Order.
     * cascade=ALL: saving Order can save its OrderItems.
     * orphanRemoval=true: removing a line from this list will delete it from DB.
     * @JsonIgnore to avoid recursion (Order -> OrderItem -> Order -> ...)
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<OrderItem> orderItems = new ArrayList<>();

    /**
     * Date/time when this order was created/updated (you are using it also in status flips).
     * Default = now when the entity is instantiated.
     */
    @Column(name = "order_date", nullable = false)
    private LocalDateTime orderDate = LocalDateTime.now();

    // 🔗 status from order_status table
    /**
     * Current status of the order.
     * Stored as a foreign key to order_status table (status_id).
     * Examples: PENDING, COMPLETED, CANCELED, REFUNDED, ...
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "status_id", nullable = false)
    private OrderStatus status;
    /**
     * Grand total amount for the order (items + shipping + taxes - discount).
     * precision=10, scale=2 => up to 99999999.99 (depends on DB)
     */
    @Column(name = "total_price", precision = 10, scale = 2)
    private BigDecimal totalPrice = BigDecimal.ZERO;

    /**
     * Currency of the order total (USD/SAR/LBP...).
     * OnDelete CASCADE: if a currency is deleted, orders referencing it will be deleted.
     * (Be careful: usually currencies are "master data" and should never be deleted.)
     */
    @ManyToOne
    @JoinColumn(name = "currency_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Currency currency;

    // --- Shipping & tax ---

    /**
     * Shipping country (optional).
     * For digital products or bookings, it can be null.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipping_country_id")
    private Country shippingCountry;

    /**
     * Shipping region/state (optional).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipping_region_id")
    private Region shippingRegion;

    /** City in shipping address (optional) */
    @Column(name = "shipping_city")
    private String shippingCity;

    /** Postal/ZIP code (optional) */
    @Column(name = "shipping_postal_code")
    private String shippingPostalCode;

    /**
     * Selected shipping method id (from your shipping module),
     * stored as a simple Long for flexibility.
     */
    @Column(name = "shipping_method_id")
    private Long shippingMethodId;

    /** Human-readable shipping method name (useful for invoices/history) */
    @Column(name = "shipping_method_name")
    private String shippingMethodName;

    /** Full address line (street/building/apartment, etc.) */
    @Column(name = "shipping_address", length = 500)
    private String shippingAddress;

    /** Phone number used for delivery contact */
    @Column(name = "shipping_phone", length = 50)
    private String shippingPhone;

    /** Shipping total amount (without taxes if you separate them) */
    @Column(name = "shipping_total", precision = 10, scale = 2)
    private BigDecimal shippingTotal = BigDecimal.ZERO;

    /** Tax amount that applies to items subtotal */
    @Column(name = "item_tax_total", precision = 10, scale = 2)
    private BigDecimal itemTaxTotal = BigDecimal.ZERO;

    /** Tax amount that applies to shipping total */
    @Column(name = "shipping_tax_total", precision = 10, scale = 2)
    private BigDecimal shippingTaxTotal = BigDecimal.ZERO;

    // --- Coupon ---

    /** Coupon code applied at checkout (if any) */
    @Column(name = "coupon_code", length = 100)
    private String couponCode;

    /** Discount amount coming from coupon (positive number stored as amount) */
    @Column(name = "coupon_discount", precision = 10, scale = 2)
    private BigDecimal couponDiscount = BigDecimal.ZERO;

    // --- Payment method (STRIPE, CASH, PAYPAL, etc.) ---
    /**
     * Payment method selected at checkout:
     * - STRIPE
     * - CASH
     * - PAYPAL (future)
     *
     * Stored as FK to payment_method table (payment_method_id).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id")
    private PaymentMethod paymentMethod;

    // ===== Helper methods to manage lines =====

    /**
     * Adds an OrderItem to this order and sets the back-reference.
     * Use this if you build order items through the entity instead of setting order manually.
     */
    public void addOrderItem(OrderItem item) {
        orderItems.add(item);
        item.setOrder(this);
    }

    /**
     * Removes an OrderItem from this order and clears the back-reference.
     * orphanRemoval=true => removing from list will delete the row from DB after flush.
     */
    public void removeOrderItem(OrderItem item) {
        orderItems.remove(item);
        item.setOrder(null);
    }

    // ===== Getters and Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Users getUser() { return user; }
    public void setUser(Users user) { this.user = user; }

    public Currency getCurrency() { return currency; }
    public void setCurrency(Currency currency) { this.currency = currency; }

    public LocalDateTime getOrderDate() { return orderDate; }
    public void setOrderDate(LocalDateTime orderDate) { this.orderDate = orderDate; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }

    public List<OrderItem> getOrderItems() { return orderItems; }
    public void setOrderItems(List<OrderItem> orderItems) { this.orderItems = orderItems; }

    public Country getShippingCountry() {
        return shippingCountry;
    }

    public void setShippingCountry(Country shippingCountry) {
        this.shippingCountry = shippingCountry;
    }

    public Region getShippingRegion() {
        return shippingRegion;
    }

    public void setShippingRegion(Region shippingRegion) {
        this.shippingRegion = shippingRegion;
    }

    public String getShippingCity() {
        return shippingCity;
    }

    public void setShippingCity(String shippingCity) {
        this.shippingCity = shippingCity;
    }

    public String getShippingPostalCode() {
        return shippingPostalCode;
    }

    public void setShippingPostalCode(String shippingPostalCode) {
        this.shippingPostalCode = shippingPostalCode;
    }

    public Long getShippingMethodId() {
        return shippingMethodId;
    }

    public void setShippingMethodId(Long shippingMethodId) {
        this.shippingMethodId = shippingMethodId;
    }

    public String getShippingMethodName() {
        return shippingMethodName;
    }

    public void setShippingMethodName(String shippingMethodName) {
        this.shippingMethodName = shippingMethodName;
    }

    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }

    public String getShippingPhone() { return shippingPhone; }
    public void setShippingPhone(String shippingPhone) { this.shippingPhone = shippingPhone; }

    public BigDecimal getShippingTotal() {
        return shippingTotal;
    }

    public void setShippingTotal(BigDecimal shippingTotal) {
        this.shippingTotal = shippingTotal;
    }

    public BigDecimal getItemTaxTotal() {
        return itemTaxTotal;
    }

    public void setItemTaxTotal(BigDecimal itemTaxTotal) {
        this.itemTaxTotal = itemTaxTotal;
    }

    public BigDecimal getShippingTaxTotal() {
        return shippingTaxTotal;
    }

    public void setShippingTaxTotal(BigDecimal shippingTaxTotal) {
        this.shippingTaxTotal = shippingTaxTotal;
    }

    public String getCouponCode() {
        return couponCode;
    }

    public void setCouponCode(String couponCode) {
        this.couponCode = couponCode;
    }

    public BigDecimal getCouponDiscount() {
        return couponDiscount;
    }

    public void setCouponDiscount(BigDecimal couponDiscount) {
        this.couponDiscount = couponDiscount;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
}
