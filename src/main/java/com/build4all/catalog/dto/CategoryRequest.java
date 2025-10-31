package com.build4all.catalog.dto;

public class CategoryRequest {
    private Long projectId;     // required
    private String name;        // required
    private String iconName;    // optional
    private String iconLibrary; // optional

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public String getName() { return name; }
    public String getIconName() { return iconName; }
    public String getIconLibrary() { return iconLibrary; }

    public void setName(String name) { this.name = name; }
    public void setIconName(String iconName) { this.iconName = iconName; }
    public void setIconLibrary(String iconLibrary) { this.iconLibrary = iconLibrary; }
}
