package com.build4all.catalog.domain;

import com.build4all.admin.domain.AdminUserProject;
import jakarta.persistence.*;

@Entity
@Table(name = "item_attributes")
public class ItemAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_attribute_id")
    private Long id;

    /** Scope: per owner project (aup_id) */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "aup_id", nullable = false)
    private AdminUserProject ownerProject;

    /** Optional: restrict to one ItemType (e.g. only Products) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_type_id")
    private ItemType itemType; // nullable: applies to all item types for this AUP

    @Column(name = "code", nullable = false)
    private String code;       // "brand", "color", "size"

    @Column(name = "label", nullable = false)
    private String label;      // "Brand", "Color", "Size"

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false)
    private ItemAttributeDataType dataType = ItemAttributeDataType.STRING;

    @Column(name = "filterable", nullable = false)
    private boolean filterable = true;

    @Column(name = "for_variations", nullable = false)
    private boolean forVariations = false;

    // ===== Getters & Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public AdminUserProject getOwnerProject() { return ownerProject; }
    public void setOwnerProject(AdminUserProject ownerProject) { this.ownerProject = ownerProject; }

    public ItemType getItemType() { return itemType; }
    public void setItemType(ItemType itemType) { this.itemType = itemType; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public ItemAttributeDataType getDataType() { return dataType; }
    public void setDataType(ItemAttributeDataType dataType) { this.dataType = dataType; }

    public boolean isFilterable() { return filterable; }
    public void setFilterable(boolean filterable) { this.filterable = filterable; }

    public boolean isForVariations() { return forVariations; }
    public void setForVariations(boolean forVariations) { this.forVariations = forVariations; }
}
