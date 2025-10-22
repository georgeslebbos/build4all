// com/build4all/catalog/dto/ItemTypeDTO.java
package com.build4all.catalog.dto;

public class ItemTypeDTO {

    private Long id;
    private String name;
    private String displayName;
    private String icon;
    private String iconLib;
    private Long categoryId;    // required

	public ItemTypeDTO(Long id, String name, String displayName, String icon, String iconLib, Long categoryId) {
		this.id = id;
		this.name = name;
		this.displayName = displayName;
		this.icon = icon;
		this.iconLib = iconLib;
		this.categoryId = categoryId;
	}

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getIconLib() { return iconLib; }
    public void setIconLib(String iconLib) { this.iconLib = iconLib; }

	public Long getCategoryId() { return categoryId; }
	public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
}
