package com.build4all.home.sections.dto;

/**
 * HomeSectionRequest
 *
 * Purpose:
 * - Request DTO used by OWNER/admin endpoints to create or update a HomeSection.
 * - This DTO maps to the HomeSection entity fields that the admin can control.
 *
 * Typical endpoints:
 * - Create:
 *     POST /api/home/sections
 *     Body: HomeSectionRequest (ownerProjectId is required)
 *
 * - Update:
 *     PUT /api/home/sections/{sectionId}
 *     Body: HomeSectionRequest (ownerProjectId is typically not needed for update)
 *
 * Multi-tenant:
 * - ownerProjectId is the tenant/app scope (AdminUserProject.aup_id).
 * - For CREATE: required to decide which app this section belongs to.
 * - For UPDATE: the service usually validates ownership through the existing sectionId,
 *   so ownerProjectId can be omitted.
 *
 * Fields:
 * - ownerProjectId:
 *     The AUP id (tenant/app).
 *     Required on create; optional on update.
 *
 * - code:
 *     Stable identifier of a section (unique per tenant).
 *     Examples: "flash_sale", "featured", "new_arrivals".
 *     Required on create. Usually NOT changed after creation (best practice),
 *     because it is used in URLs and unique constraint (aup_id, code).
 *
 * - title:
 *     Display label shown to the end user above the section.
 *     Optional.
 *
 * - layout:
 *     Determines how frontend renders products in this section.
 *     Allowed values: "HORIZONTAL" or "GRID" (case-insensitive).
 *     Required on create.
 *
 * - sortOrder:
 *     Controls ordering of sections on the home page (ascending).
 *     Optional; if null, backend should default to 0.
 *
 * - active:
 *     Visibility flag for public home page.
 *     Optional; if null, backend typically defaults to true (active).
 *
 * Notes:
 * - This DTO uses "active" (consistent with HomeSection.active and HomeBanner.active).
 * - This is more consistent than using "enabled" in separate admin DTOs.
 */
public class HomeSectionRequest {

    private Long ownerProjectId;
    private String code;
    private String title;
    private String layout;   // HORIZONTAL / GRID
    private Integer sortOrder;
    private Boolean active;

    public Long getOwnerProjectId() { return ownerProjectId; }
    public void setOwnerProjectId(Long ownerProjectId) { this.ownerProjectId = ownerProjectId; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getLayout() { return layout; }
    public void setLayout(String layout) { this.layout = layout; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
