package com.build4all.catalog.domain;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.business.domain.Businesses;
import com.build4all.tax.domain.TaxClass;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "items")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Long id;

    // NEW: single FK to admin_user_projects (aup_id)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aup_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private AdminUserProject ownerProject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = true)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Businesses business;

    @Column(name = "item_name", nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_type_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private ItemType itemType;

    @Column(name = "price")
    private BigDecimal price;

    // --- SALE / DISCOUNT (✅ now Item-level, not Product-level) ---
    @Column(name = "sale_price")
    private BigDecimal salePrice;

    @Column(name = "sale_start")
    private LocalDateTime saleStart;

    @Column(name = "sale_end")
    private LocalDateTime saleEnd;

    @Column(name = "taxable", nullable = false)
    private boolean taxable = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_class", nullable = false)
    private TaxClass taxClass = TaxClass.STANDARD;

    @Column(name = "status", nullable = false)
    private String status = "Upcoming";

    @Column(name = "stock", nullable = false)
    private Integer stock = 0;

    @Column(name = "image_url")
    private String imageUrl;

    @ManyToOne
    @JoinColumn(name = "currency_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Currency currency;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { this.createdAt = this.updatedAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    // ---------------- SALE LOGIC (✅ now reusable by all Item subclasses) ----------------

    @Transient
    public boolean isOnSaleNow() {
        // Need a positive base price and sale price
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) return false;
        if (salePrice == null || salePrice.compareTo(BigDecimal.ZERO) <= 0) return false;

        // Sale price must be lower than base price
        if (salePrice.compareTo(price) >= 0) return false;

        LocalDateTime now = LocalDateTime.now();

        if (saleStart != null && now.isBefore(saleStart)) return false;
        if (saleEnd != null && now.isAfter(saleEnd)) return false;

        return true;
    }

    @Transient
    public BigDecimal getEffectivePrice() {
        return isOnSaleNow() ? salePrice : price;
    }

    // ---------------- getters & setters ----------------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public AdminUserProject getOwnerProject() { return ownerProject; }
    public void setOwnerProject(AdminUserProject ownerProject) { this.ownerProject = ownerProject; }

    public Businesses getBusiness() { return business; }
    public void setBusiness(Businesses business) { this.business = business; }

    public String getItemName() { return name; }
    public void setItemName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public ItemType getItemType() { return itemType; }
    public void setItemType(ItemType itemType) { this.itemType = itemType; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getSalePrice() { return salePrice; }
    public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice; }

    public LocalDateTime getSaleStart() { return saleStart; }
    public void setSaleStart(LocalDateTime saleStart) { this.saleStart = saleStart; }

    public LocalDateTime getSaleEnd() { return saleEnd; }
    public void setSaleEnd(LocalDateTime saleEnd) { this.saleEnd = saleEnd; }

    public boolean isTaxable() { return taxable; }
    public void setTaxable(boolean taxable) { this.taxable = taxable; }

    public TaxClass getTaxClass() { return taxClass; }
    public void setTaxClass(TaxClass taxClass) { this.taxClass = taxClass; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public Currency getCurrency() { return currency; }
    public void setCurrency(Currency currency) { this.currency = currency; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
