package com.build4all.order.domain;

import com.build4all.catalog.domain.Currency;
import com.build4all.catalog.domain.Country;
import com.build4all.catalog.domain.Region;
import com.build4all.user.domain.Users;
import com.build4all.payment.domain.PaymentMethod;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private Users user;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<OrderItem> orderItems = new ArrayList<>();

   
    @Column(name = "created_at", nullable = true, updatable = false)
    private LocalDateTime createdAt;

    
    @Column(name = "updated_at", nullable = true)
    private LocalDateTime updatedAt;

  
    @Column(name = "order_date", nullable = false)
    private LocalDateTime orderDate;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "status_id", nullable = false)
    private OrderStatus status;

    @Column(name = "total_price", precision = 10, scale = 2)
    private BigDecimal totalPrice = BigDecimal.ZERO;

    @ManyToOne
    @JoinColumn(name = "currency_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Currency currency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipping_country_id")
    private Country shippingCountry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipping_region_id")
    private Region shippingRegion;

    @Column(name = "shipping_city")
    private String shippingCity;

    @Column(name = "shipping_full_name", length = 200)
    private String shippingFullName;

    @Column(name = "shipping_postal_code")
    private String shippingPostalCode;

    @Column(name = "shipping_method_id")
    private Long shippingMethodId;

    @Column(name = "shipping_method_name")
    private String shippingMethodName;

    @Column(name = "shipping_address", length = 500)
    private String shippingAddress;

    @Column(name = "shipping_phone", length = 50)
    private String shippingPhone;

    @Column(name = "shipping_total", precision = 10, scale = 2)
    private BigDecimal shippingTotal = BigDecimal.ZERO;

    @Column(name = "item_tax_total", precision = 10, scale = 2)
    private BigDecimal itemTaxTotal = BigDecimal.ZERO;

    @Column(name = "shipping_tax_total", precision = 10, scale = 2)
    private BigDecimal shippingTaxTotal = BigDecimal.ZERO;

    @Column(name = "order_code", length = 32, unique = true)
    private String orderCode;

    @Column(name = "order_seq")
    private Long orderSeq;

    @Column(name = "coupon_code", length = 100)
    private String couponCode;

    @Column(name = "coupon_discount", precision = 10, scale = 2)
    private BigDecimal couponDiscount = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id")
    private PaymentMethod paymentMethod;

    /* ==========================
       ✅ Auto timestamps
       ========================== */

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;

        // keep legacy orderDate in sync with createdAt
        if (this.orderDate == null) {
            this.orderDate = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();

        // Do NOT change orderDate here (orderDate = createdAt)
        if (this.orderDate == null) {
            this.orderDate = this.createdAt != null ? this.createdAt : this.updatedAt;
        }
    }

    /* ==========================
       Helper methods for lines
       ========================== */

    public void addOrderItem(OrderItem item) {
        orderItems.add(item);
        item.setOrder(this);
    }

    public void removeOrderItem(OrderItem item) {
        orderItems.remove(item);
        item.setOrder(null);
    }

    /* ==========================
       Getters / Setters
       ========================== */

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Users getUser() { return user; }
    public void setUser(Users user) { this.user = user; }

    public List<OrderItem> getOrderItems() { return orderItems; }
    public void setOrderItems(List<OrderItem> orderItems) { this.orderItems = orderItems; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getOrderDate() { return orderDate; }
    public void setOrderDate(LocalDateTime orderDate) { this.orderDate = orderDate; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }

    public Currency getCurrency() { return currency; }
    public void setCurrency(Currency currency) { this.currency = currency; }

    public Country getShippingCountry() { return shippingCountry; }
    public void setShippingCountry(Country shippingCountry) { this.shippingCountry = shippingCountry; }

    public Region getShippingRegion() { return shippingRegion; }
    public void setShippingRegion(Region shippingRegion) { this.shippingRegion = shippingRegion; }

    public String getShippingCity() { return shippingCity; }
    public void setShippingCity(String shippingCity) { this.shippingCity = shippingCity; }

    public String getShippingFullName() { return shippingFullName; }
    public void setShippingFullName(String shippingFullName) { this.shippingFullName = shippingFullName; }

    public String getShippingPostalCode() { return shippingPostalCode; }
    public void setShippingPostalCode(String shippingPostalCode) { this.shippingPostalCode = shippingPostalCode; }

    public Long getShippingMethodId() { return shippingMethodId; }
    public void setShippingMethodId(Long shippingMethodId) { this.shippingMethodId = shippingMethodId; }

    public String getShippingMethodName() { return shippingMethodName; }
    public void setShippingMethodName(String shippingMethodName) { this.shippingMethodName = shippingMethodName; }

    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }

    public String getShippingPhone() { return shippingPhone; }
    public void setShippingPhone(String shippingPhone) { this.shippingPhone = shippingPhone; }

    public BigDecimal getShippingTotal() { return shippingTotal; }
    public void setShippingTotal(BigDecimal shippingTotal) { this.shippingTotal = shippingTotal; }

    public BigDecimal getItemTaxTotal() { return itemTaxTotal; }
    public void setItemTaxTotal(BigDecimal itemTaxTotal) { this.itemTaxTotal = itemTaxTotal; }

    public BigDecimal getShippingTaxTotal() { return shippingTaxTotal; }
    public void setShippingTaxTotal(BigDecimal shippingTaxTotal) { this.shippingTaxTotal = shippingTaxTotal; }

    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }

    public Long getOrderSeq() { return orderSeq; }
    public void setOrderSeq(Long orderSeq) { this.orderSeq = orderSeq; }

    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }

    public BigDecimal getCouponDiscount() { return couponDiscount; }
    public void setCouponDiscount(BigDecimal couponDiscount) { this.couponDiscount = couponDiscount; }

    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
}