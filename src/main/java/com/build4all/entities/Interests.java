package com.build4all.entities;

import com.build4all.enums.IconLibraryEnum;
import com.build4all.enums.InterestIconEnum;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "interests")
public class Interests {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "interest_id")
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "icon_name")
    private InterestIconEnum icon;

    @Enumerated(EnumType.STRING)
    @Column(name = "icon_library")
    private IconLibraryEnum iconLib;

    @OneToMany(mappedBy = "id.interest", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserInterests> userInterests;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // --- Constructors ---
    public Interests() {}

    public Interests(String name) {
        this.name = name;
    }

    public Interests(String name, InterestIconEnum icon, IconLibraryEnum iconLib) {
        this.name = name;
        this.icon = icon;
        this.iconLib = iconLib;
    }

    // --- Getters and Setters ---
    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public InterestIconEnum getIcon() {
        return icon;
    }

    public void setIcon(InterestIconEnum icon) {
        this.icon = icon;
    }

    public IconLibraryEnum getIconLib() {
        return iconLib;
    }

    public void setIconLib(IconLibraryEnum iconLib) {
        this.iconLib = iconLib;
    }

    public Set<UserInterests> getUserInterests() {
        return userInterests;
    }

    public void setUserInterests(Set<UserInterests> userInterests) {
        this.userInterests = userInterests;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}