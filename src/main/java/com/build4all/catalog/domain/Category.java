package com.build4all.catalog.domain;

import com.build4all.project.domain.Project;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long id;

    /** Project-level scope (shared across owners using this project) */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "icon_name")
    private String iconName;

    @Column(name = "icon_library")
    private String iconLibrary;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "owner_project_id")
    private Long ownerProjectId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Category() {}

    public Category(Project project, String name, String iconName, String iconLibrary) {
        this.project = project;
        this.name = name;
        this.iconName = iconName;
        this.iconLibrary = iconLibrary;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
            this.updatedAt = this.createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /* Getters & Setters */
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }

    public String getIconName() { return iconName; }
    public void setIconName(String iconName) { this.iconName = iconName; }

    public String getIconLibrary() { return iconLibrary; }
    public void setIconLibrary(String iconLibrary) { this.iconLibrary = iconLibrary; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

	public Long getOwnerProjectId() {
		return ownerProjectId;
	}

	public void setOwnerProjectId(Long ownerProjectId) {
		this.ownerProjectId = ownerProjectId;
	}
    
    
}
