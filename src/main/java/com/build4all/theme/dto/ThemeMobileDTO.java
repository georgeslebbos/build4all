package com.build4all.theme.dto;

import com.build4all.theme.domain.Theme;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.Map;

public class ThemeMobileDTO {
    private Long id;
    private String name;
    private String menuType;
    private Map<String, Object> valuesMobile;

    public ThemeMobileDTO(Theme theme) {
        this.id = theme.getId();
        this.name = theme.getName();
        this.menuType = theme.getMenuType();
        try {
            ObjectMapper mapper = new ObjectMapper();
            this.valuesMobile = mapper.readValue(
                theme.getValuesMobile(), new TypeReference<Map<String, Object>>() {}
            );
        } catch (Exception e) {
            this.valuesMobile = Collections.emptyMap();
        }
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getMenuType() { return menuType; }
    public Map<String, Object> getValuesMobile() { return valuesMobile; }
}
