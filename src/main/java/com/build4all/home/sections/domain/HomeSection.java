package com.build4all.home.sections.domain;

import com.build4all.admin.domain.AdminUserProject;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * HomeSection
 *
 * Purpose:
 * - Represents a configurable "section" on the Home page (below the top banners).
 * - Each section groups either products (via HomeSectionProduct links) and can later be extended
 *   to support banners or other content types.
 *
 * Multi-tenant (Build4All) design:
 * - A section belongs to a generated app/tenant represented by AdminUserProject (AUP).
 * - The tenant is stored in DB as aup_id.
 * - We enforce that the same section code cannot be duplicated within the same tenant:
 *   unique (aup_id, code).
 *
 * Ordering & visibility:
 * - active: if false, section is hidden from the public home endpoint.
 * - sortOrder: determines ordering of sections on the home page (ascending).
 *
 * Layout:
 * - layout controls how the frontend renders products inside the section:
 *   HORIZONTAL (carousel) or GRID.
 *
 * Audit:
 * - createdAt/updatedAt are set automatically on persist/update.
 */
@Entity
@Table(
        name = "home_sections",
        uniqueConstraints = @UniqueConstraint(columnNames = {"aup_id", "code"})
)
public class HomeSection {

    /**
     * Primary key for home_sections table.
     * Note: This is NOT the tenant id; tenant id is stored in ownerProject (aup_id).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "section_id")
    private Long id;

    /**
     * Tenant scope / generated app owner.
     * - Links this section to AdminUserProject (AUP) via aup_id.
     * - @JsonIgnore prevents leaking tenant structure or causing recursion in JSON.
     * - LAZY because we usually don't need to load full AUP when returning section data.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aup_id", referencedColumnName = "aup_id", nullable = false)
    @JsonIgnore
    private AdminUserProject ownerProject;

    /**
     * Section code (stable identifier).
     * Used by frontend/backend to reference a section programmatically.
     * Examples: "flash_sale", "featured", "new_arrivals".
     *
     * Must be unique per tenant (AUP) because of the unique constraint (aup_id, code).
     */
    @Column(nullable = false, length = 100)
    private String code;

    /**
     * Section title (display name shown to end users).
     * Can be null/empty if frontend decides to derive title from code.
     */
    private String title;

    /**
     * UI layout used by frontend to render section content.
     * - HORIZONTAL: horizontal list/carousel.
     * - GRID: grid layout.
     *
     * Stored as STRING for readability and easier DB maintenance.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private HomeSectionLayout layout = HomeSectionLayout.HORIZONTAL;

    /**
     * Whether this section is visible in the public home page response.
     * If false, section is hidden.
     */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    /**
     * Display ordering for sections (ascending).
     * Lower sortOrder appears first.
     */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    /**
     * Creation timestamp (auto-filled).
     * Marked updatable=false so it won't be changed on updates.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Update timestamp (auto-updated on each update).
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public HomeSection() {}

    /**
     * Auto-fill audit fields on insert.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Auto-update audit field on update.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ---------------- getters & setters ----------------

    public Long getId() { return id; }

    public AdminUserProject getOwnerProject() { return ownerProject; }
    public void setOwnerProject(AdminUserProject ownerProject) { this.ownerProject = ownerProject; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public HomeSectionLayout getLayout() { return layout; }
    public void setLayout(HomeSectionLayout layout) { this.layout = layout; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
