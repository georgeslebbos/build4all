// com/build4all/catalog/domain/ItemType.java
package com.build4all.catalog.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

@Entity
@Table(name = "ItemTypes")
public class ItemType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_type_name", unique = true, nullable = false)
    @JsonProperty("item_type")
    private String name;

    @Column(name = "icon_name")
    private String icon;

    @Column(name = "icon_library")
    private String iconLibrary;

    /** Category carries the project scope */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /**
     * NEW: internal flag to know this is the default type for its category.
     * Used when client sends only categoryId (no itemTypeId).
     */
    @Column(name = "is_default_for_category", nullable = false)
    private boolean defaultForCategory = false;

    public ItemType() {}

    public ItemType(String name, String icon, String iconLibrary, Category category) {
        this.name = name;
        this.icon = icon;
        this.iconLibrary = iconLibrary;
        this.category = category;
    }

    // --- Getters & Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getIconLibrary() { return iconLibrary; }
    public void setIconLibrary(String iconLibrary) { this.iconLibrary = iconLibrary; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    // 🔴 NEW
    public boolean isDefaultForCategory() {
        return defaultForCategory;
    }

    public void setDefaultForCategory(boolean defaultForCategory) {
        this.defaultForCategory = defaultForCategory;
    }
}
