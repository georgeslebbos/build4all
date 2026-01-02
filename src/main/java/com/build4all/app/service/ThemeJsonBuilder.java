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
            // ---------- colors ----------
            Map<String, Object> colors = new LinkedHashMap<>();

            // primary
            String primary = (primaryColor != null && !primaryColor.isBlank())
                    ? primaryColor
                    : "#16A34A"; // default green
            colors.put("primary", primary);
            colors.put("onPrimary", "#FFFFFF");

            // background/surface
            String bg = (backgroundColor != null && !backgroundColor.isBlank())
                    ? backgroundColor
                    : "#FFFFFF";
            colors.put("background", bg);
            colors.put("surface", "#FFFFFF");

            // label/body
            String onBg = (onBackgroundColor != null && !onBackgroundColor.isBlank())
                    ? onBackgroundColor
                    : "#111827";
            colors.put("label", onBg);
            colors.put("body", "#374151");

            // border
            colors.put("border", primary);

            // error + extras (optional but your Flutter supports them)
            String err = (errorColor != null && !errorColor.isBlank())
                    ? errorColor
                    : "#DC2626";
            colors.put("error", err);
            colors.put("danger", err);
            colors.put("muted", "#9CA3AF");
            colors.put("success", primary);

            // ---------- tokens ----------
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("radius", 16);
            card.put("elevation", 4);
            card.put("padding", 12);
            card.put("imageHeight", 120);
            card.put("showShadow", true);
            card.put("showBorder", true);

            Map<String, Object> search = new LinkedHashMap<>();
            search.put("radius", 16);
            search.put("borderWidth", 1.4);
            search.put("dense", true);

            Map<String, Object> button = new LinkedHashMap<>();
            button.put("radius", 16);
            button.put("height", 48);
            button.put("textSize", 15);
            button.put("fullWidth", true);

            Map<String, Object> valuesMobile = new LinkedHashMap<>();
            valuesMobile.put("colors", colors);
            valuesMobile.put("card", card);
            valuesMobile.put("search", search);
            valuesMobile.put("button", button);

            // ---------- root ----------
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("menuType", "bottom");
            root.put("valuesMobile", valuesMobile);

            return MAPPER.writeValueAsString(root);

        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not build themeJson", e);
        }
    }
}
