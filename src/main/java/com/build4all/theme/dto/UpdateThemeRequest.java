// src/main/java/com/build4all/theme/dto/UpdateThemeRequest.java
package com.build4all.theme.dto;

import java.util.Map;

/**
 * All fields optional. Only sent fields will be updated.
 */
public class UpdateThemeRequest {

    private String name;                // optional
    private Boolean isActive;           // optional
    private Map<String, Object> values; // optional

    public String getName() {
        return name;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public void setValues(Map<String, Object> values) {
        this.values = values;
    }
}
