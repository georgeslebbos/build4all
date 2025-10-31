package com.build4all.theme.dto;

import java.util.Map;

/** Incoming payload. We only care about valuesMobile (mobile-first). */
public class CreateThemeRequest {
    private String name;                      // required
    private String menuType;                  // "bottom" | "top" | "sandwich"
    private Boolean isActive;                 // optional
    private Map<String, Object> valuesMobile; // source of truth

    public String getName() { return name; }
    public String getMenuType() { return menuType; }
    public Boolean getIsActive() { return isActive; }
    public Map<String, Object> getValuesMobile() { return valuesMobile; }

    public void setName(String name) { this.name = name; }
    public void setMenuType(String menuType) { this.menuType = menuType; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public void setValuesMobile(Map<String, Object> valuesMobile) { this.valuesMobile = valuesMobile; }
}
