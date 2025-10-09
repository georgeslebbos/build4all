package com.build4all.theme.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_theme")
public class Theme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;
    
    @Column(name = "menu_type")
    private String menuType = "bottom"; // default: bottom | top | sandwich


    // JSON of the theme variables, stored as text
    // @Lob
    @Column(name = "values", columnDefinition = "TEXT", nullable = false)
    private String values;

    @Column(name = "values_mobile", columnDefinition = "TEXT")
    private String valuesMobile;
    
    

    @Column(name = "is_active")
private Boolean isActive = false;


    @Column(name = "created_at", updatable = false)
    private LocalDateTime created_at;

    @Column(name = "updated_at")
    private LocalDateTime updated_at;

    @PrePersist
    protected void onCreate() {
        this.created_at = LocalDateTime.now();
        this.updated_at = this.created_at;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updated_at = LocalDateTime.now();
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

    public String getValues() {
        return values;
    }

    public void setValues(String values) {
        this.values = values;
    }

    public String getValuesMobile() {
        return valuesMobile;
    }

    public void setValuesMobile(String valuesMobile) {
        this.valuesMobile = valuesMobile;
    }

    public Boolean getIsActive() {
    return isActive;
}

public void setIsActive(Boolean isActive) {
    this.isActive = isActive;
}


    public LocalDateTime getCreated_at() {
        return created_at;
    }

    public LocalDateTime getUpdated_at() {
        return updated_at;
    }
    
    public String getMenuType() {
        return menuType;
    }

    public void setMenuType(String menuType) {
        this.menuType = menuType;
    }

}