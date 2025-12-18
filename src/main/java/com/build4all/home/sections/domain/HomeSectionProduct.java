package com.build4all.home.sections.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * HomeSectionProduct
 *
 * Purpose:
 * - Join/link entity that attaches an existing Product (by product_id) to a HomeSection.
 * - Allows the OWNER to control:
 *   1) which products appear in each Home section
 *   2) the order of products inside the section
 *   3) whether a specific product is active/visible inside that section
 *
 * Why store productId as Long (instead of @ManyToOne Product)?
 * - Keeps this home feature lightweight and avoids extra joins / heavy object graphs.
 * - Product data is fetched on demand when building the home response (HomePageService).
 * - Also prevents cascading deletes/updates from the Home feature into the catalog/ecommerce domain.
 *
 * Visibility & ordering:
 * - active: if false, the product is hidden from the section for public users.
 * - sortOrder: controls the position of the product within the section (ascending).
 *
 * Lifecycle / audit:
 * - createdAt is set automatically on insert (persist).
 *
 * Database table:
 * - home_section_products
 * - foreign key section_id -> home_sections.section_id (usually with ON DELETE CASCADE in SQL)
 */
@Entity
@Table(name = "home_section_products")
public class HomeSectionProduct {

    /**
     * Primary key for this link record (NOT the product id).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The parent section that owns this link.
     * - Many links can belong to one section.
     * - LAZY because the section is not always needed when returning link info.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", referencedColumnName = "section_id", nullable = false)
    private HomeSection section;

    /**
     * The Product identifier being featured in this section.
     * This is the Product's item_id (because Product extends Item).
     */
    @Column(name = "product_id", nullable = false)
    private Long productId;

    /**
     * Whether this product is visible in this section.
     * If false, it will be skipped in the public home response.
     */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    /**
     * Display ordering of products inside the section (ascending).
     * Lower sortOrder appears first.
     */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    /**
     * Insert timestamp (auto-filled).
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public HomeSectionProduct() {}

    /**
     * Auto-fill createdAt on insert.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ---------------- getters & setters ----------------

    public Long getId() { return id; }

    public HomeSection getSection() { return section; }
    public void setSection(HomeSection section) { this.section = section; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
