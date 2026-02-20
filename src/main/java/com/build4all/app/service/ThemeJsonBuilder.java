package com.build4all.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

public class ThemeJsonBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Build a Flutter-compatible theme JSON.
     *
     * @param menuType  optional – "hamburger" / "drawer" / "bottom".
     *                  If null/blank the THEME default ("bottom") is used.
     *                  Pass the value from brandingJson so the menu choice is respected.
     */
    public static String buildThemeJson(
            String primaryColor,
            String secondaryColor,
            String backgroundColor,
            String onBackgroundColor,
            String errorColor,
            String menuType
    ) {
        try {
            // ---------- resolve menuType ----------
            String resolvedMenu = resolveMenuType(menuType);

            // ---------- colors ----------
            Map<String, Object> colors = new LinkedHashMap<>();

            String primary = (primaryColor != null && !primaryColor.isBlank())
                    ? primaryColor
                    : "#16A34A";
            colors.put("primary", primary);
            colors.put("onPrimary", "#FFFFFF");

            String bg = (backgroundColor != null && !backgroundColor.isBlank())
                    ? backgroundColor
                    : "#FFFFFF";
            colors.put("background", bg);
            colors.put("surface", "#FFFFFF");

            String onBg = (onBackgroundColor != null && !onBackgroundColor.isBlank())
                    ? onBackgroundColor
                    : "#111827";
            colors.put("label", onBg);
            colors.put("body", "#374151");

            colors.put("border", primary);

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
            root.put("menuType", resolvedMenu);   // ✅ from branding, never hardcoded
            root.put("valuesMobile", valuesMobile);

            return MAPPER.writeValueAsString(root);

        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not build themeJson", e);
        }
    }

    /**
     * Legacy overload – kept for callers that don't pass menuType yet.
     * Defaults to "bottom" (old behaviour).
     */
    public static String buildThemeJson(
            String primaryColor,
            String secondaryColor,
            String backgroundColor,
            String onBackgroundColor,
            String errorColor
    ) {
        return buildThemeJson(primaryColor, secondaryColor, backgroundColor,
                onBackgroundColor, errorColor, null);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /**
     * Normalise a raw menuType string to either "hamburger" or "bottom".
     * "drawer" is treated as an alias for "hamburger".
     * Anything else / null → "bottom".
     */
    public static String resolveMenuType(String raw) {
        if (raw == null || raw.isBlank()) return "bottom";
        String v = raw.trim().toLowerCase();
        if (v.equals("drawer") || v.equals("hamburger")) return "hamburger";
        if (v.equals("bottom")) return "bottom";
        return "bottom";
    }

    /**
     * Extract "menuType" from a brandingJson string safely.
     * Returns null if absent / parse error.
     */
    public static String extractMenuTypeFromBranding(String brandingJson) {
        if (brandingJson == null || brandingJson.isBlank()) return null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> b = MAPPER.readValue(brandingJson, Map.class);
            Object m = b.get("menuType");
            return (m != null && !m.toString().isBlank()) ? m.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}