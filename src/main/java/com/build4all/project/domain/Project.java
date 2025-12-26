package com.build4all.project.domain;

import com.build4all.admin.domain.AdminUserProject;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "projects", uniqueConstraints = @UniqueConstraint(columnNames = "project_name"))
public class Project {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_name", nullable = false)
    private String projectName;

    @Column(length = 1000)
    private String description;

    @Column(name = "is_active")
    private boolean active = true;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "project_type", nullable = false)
    private ProjectType projectType = ProjectType.ECOMMERCE;


    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private Set<AdminUserProject> adminLinks = new HashSet<>();
    
   

    @PreUpdate
    public void touch() { this.updatedAt = LocalDateTime.now(); }

    // getters/setters
    public Long getId() { return id; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean getActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public Set<AdminUserProject> getAdminLinks() { return adminLinks; }
    public void setAdminLinks(Set<AdminUserProject> adminLinks) { this.adminLinks = adminLinks; }

    public ProjectType getProjectType() { 
        return projectType; 
    }

    public void setProjectType(ProjectType projectType) { 
        this.projectType = projectType; 
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = createdAt;
    }
}
