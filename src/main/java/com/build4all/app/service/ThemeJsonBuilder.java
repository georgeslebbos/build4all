package com.build4all.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

public class ThemeJsonBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String buildThemeJson(
            String primaryColor,
            String secondaryColor,
            String backgroundColor,
            String onBackgroundColor,
            String errorColor
    ) {
        try {
            Map<String, String> m = new LinkedHashMap<>();
            if (primaryColor != null && !primaryColor.isBlank()) {
                m.put("primary", primaryColor);
                m.put("onPrimary", "#FFFFFF");
            }
            if (secondaryColor != null && !secondaryColor.isBlank()) {
                m.put("secondary", secondaryColor);
            }
            if (backgroundColor != null && !backgroundColor.isBlank()) {
                m.put("background", backgroundColor);
            }
            if (onBackgroundColor != null && !onBackgroundColor.isBlank()) {
                m.put("onBackground", onBackgroundColor);
            }
            if (errorColor != null && !errorColor.isBlank()) {
                m.put("error", errorColor);
            }

            if (m.isEmpty()) {
                return "{}"; 
            }

            return MAPPER.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not build themeJson", e);
        }
    }
}
