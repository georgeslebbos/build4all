package com.build4all.theme.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// NEW: these make JSON binding flexible
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    private String menuType = "bottom"; // bottom | top | sandwich

    // Keep columns as TEXT (stringified JSON)
    @Column(name = "values", columnDefinition = "TEXT", nullable = false)
    private String values = "{}"; // placeholder if you’re mobile-only

    @Column(name = "values_mobile", columnDefinition = "TEXT")
    private String valuesMobile = "{}"; // source of truth for mobile

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
        if (this.values == null || this.values.isBlank()) this.values = "{}";
        if (this.valuesMobile == null || this.valuesMobile.isBlank()) this.valuesMobile = "{}";
        if (this.menuType == null || this.menuType.isBlank()) this.menuType = "bottom";
        if (this.isActive == null) this.isActive = false;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updated_at = LocalDateTime.now();
        if (this.values == null || this.values.isBlank()) this.values = "{}";
        if (this.valuesMobile == null || this.valuesMobile.isBlank()) this.valuesMobile = "{}";
        if (this.menuType == null || this.menuType.isBlank()) this.menuType = "bottom";
        if (this.isActive == null) this.isActive = false;
    }

    // ===== Flexible JSON setters to accept String or Object =====

    private static String toJsonString(Object any) {
        try {
            if (any == null) return "{}";

            // If already a string, try to normalize if it's JSON; else quote it
            if (any instanceof String s) {
                final String t = s.trim();
                if (t.isEmpty()) return "{}";
                try {
                    ObjectMapper om = new ObjectMapper();
                    JsonNode node = om.readTree(t);   // valid JSON string? normalize it
                    return om.writeValueAsString(node);
                } catch (Exception notJson) {
                    // Not JSON; store a valid JSON string (quoted)
                    return "\"" + t.replace("\"", "\\\"") + "\"";
                }
            }

            // If it's a Map/List/etc → stringify to JSON
            return new ObjectMapper().writeValueAsString(any);
        } catch (Exception e) {
            // worst-case fallback
            return "{}";
        }
    }

    // Called when JSON has "values": <string|object>
    @JsonSetter("values")
    public void setValuesAny(Object any) { this.values = toJsonString(any); }

    // Called when JSON has "valuesMobile": <string|object>
    @JsonSetter("valuesMobile")
    public void setValuesMobileAny(Object any) { this.valuesMobile = toJsonString(any); }

    // --- Getters and classic setters (kept for JPA and manual use) ---
    public Long getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getValues() { return values; }
    public void setValues(String values) { this.values = values; }

    public String getValuesMobile() { return valuesMobile; }
    public void setValuesMobile(String valuesMobile) { this.valuesMobile = valuesMobile; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getCreated_at() { return created_at; }
    public LocalDateTime getUpdated_at() { return updated_at; }

    public String getMenuType() { return menuType; }
    public void setMenuType(String menuType) { this.menuType = menuType; }
}
