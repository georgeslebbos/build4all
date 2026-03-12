package com.build4all.app.service;

import com.build4all.common.errors.ApiException;
import com.build4all.project.domain.ProjectType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class AppRuntimeConfigPolicyValidator {

    private final ObjectMapper objectMapper;

    public AppRuntimeConfigPolicyValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record ValidatedRuntimeConfig(
            String navJson,
            String homeJson,
            String enabledFeaturesJson,
            String brandingJson
    ) {}

    private record NavItem(
            String id,
            String label,
            String icon
    ) {}

    private record HomeSection(
            String type,
            String layout,
            int limit,
            String feature
    ) {}

    private record RuntimePolicy(
            Set<String> allowedMenuTypes,
            Set<String> allowedFeatures,
            Set<String> requiredFeatures,
            Set<String> allowedNavIds,
            Set<String> requiredNavIds,
            Map<String, Set<String>> navRequires,
            Set<String> allowedHomeTypes,
            int bottomNavMin,
            int bottomNavMax
    ) {
        static RuntimePolicy forProjectType(ProjectType projectType) {
            /*
             * This mirrors the CURRENT locked rules visible in your manager app:
             * - HOME + CART + PROFILE required
             * - EXPLORE optional
             * - CART requires ITEMS + ORDERS
             *
             * If later SERVICES / ACTIVITIES need different rules,
             * change only this switch.
             */
            return switch (projectType == null ? ProjectType.ECOMMERCE : projectType) {
                case ECOMMERCE, SERVICES, ACTIVITIES -> basePolicy();
            };
        }

        private static RuntimePolicy basePolicy() {
            Map<String, Set<String>> navRequires = new HashMap<>();
            navRequires.put("HOME", Set.of());
            navRequires.put("EXPLORE", Set.of("ITEMS"));
            navRequires.put("CART", Set.of("ITEMS", "ORDERS"));
            navRequires.put("PROFILE", Set.of());

            return new RuntimePolicy(
                    Set.of("BOTTOM", "HAMBURGER"),
                    Set.of("ITEMS", "BOOKING", "REVIEWS", "ORDERS", "COUPONS", "NOTIFICATIONS"),
                    Set.of("ITEMS", "ORDERS"), // because CART is locked/required
                    Set.of("HOME", "EXPLORE", "CART", "PROFILE"),
                    Set.of("HOME", "CART", "PROFILE"),
                    navRequires,
                    Set.of("HEADER", "SEARCH", "BANNER", "CATEGORY_CHIPS", "ITEM_LIST"),
                    3,
                    4
            );
        }
    }

    public ValidatedRuntimeConfig validate(
            ProjectType projectType,
            String navJson,
            String homeJson,
            String enabledFeaturesJson,
            String brandingJson
    ) {
        RuntimePolicy policy = RuntimePolicy.forProjectType(projectType);

        LinkedHashSet<String> enabledFeatures = parseFeatures(enabledFeaturesJson);
        List<NavItem> navItems = parseNav(navJson);
        List<HomeSection> homeSections = parseHome(homeJson);
        ObjectNode brandingNode = parseBranding(brandingJson);

        String menuType = normalizeUpper(brandingNode.path("menuType").asText("BOTTOM"));
        if (!policy.allowedMenuTypes().contains(menuType)) {
            throw badRequest(
                    "RUNTIME_MENU_TYPE_NOT_ALLOWED",
                    "menuType is not allowed",
                    Map.of("menuType", menuType, "projectType", safeProjectType(projectType))
            );
        }
        brandingNode.put("menuType", menuType.toLowerCase(Locale.ROOT));

        validateFeatures(policy, enabledFeatures, projectType);
        validateNav(policy, navItems, enabledFeatures, menuType, projectType);
        validateHome(policy, homeSections, enabledFeatures, projectType);

        return new ValidatedRuntimeConfig(
                writeNav(navItems),
                writeHome(homeSections),
                writeFeatures(enabledFeatures),
                writeBranding(brandingNode)
        );
    }

    private void validateFeatures(
            RuntimePolicy policy,
            Set<String> enabledFeatures,
            ProjectType projectType
    ) {
        for (String feature : enabledFeatures) {
            if (!policy.allowedFeatures().contains(feature)) {
                throw badRequest(
                        "RUNTIME_FEATURE_NOT_ALLOWED",
                        "Feature is not allowed",
                        Map.of("feature", feature, "projectType", safeProjectType(projectType))
                );
            }
        }

        Set<String> missingRequired = new HashSet<>(policy.requiredFeatures());
        missingRequired.removeAll(enabledFeatures);

        if (!missingRequired.isEmpty()) {
            throw badRequest(
                    "RUNTIME_FEATURE_REQUIRED_MISSING",
                    "Required runtime features are missing",
                    Map.of(
                            "missingFeatures", new ArrayList<>(missingRequired),
                            "projectType", safeProjectType(projectType)
                    )
            );
        }
    }

    private void validateNav(
            RuntimePolicy policy,
            List<NavItem> navItems,
            Set<String> enabledFeatures,
            String menuType,
            ProjectType projectType
    ) {
        Set<String> seen = new HashSet<>();
        Set<String> presentIds = new HashSet<>();

        for (NavItem nav : navItems) {
            if (!seen.add(nav.id())) {
                throw badRequest(
                        "RUNTIME_NAV_DUPLICATE",
                        "Duplicate nav item id",
                        Map.of("navId", nav.id(), "projectType", safeProjectType(projectType))
                );
            }

            if (!policy.allowedNavIds().contains(nav.id())) {
                throw badRequest(
                        "RUNTIME_NAV_NOT_ALLOWED",
                        "Navigation item is not allowed",
                        Map.of("navId", nav.id(), "projectType", safeProjectType(projectType))
                );
            }

            presentIds.add(nav.id());

            Set<String> deps = policy.navRequires().getOrDefault(nav.id(), Set.of());
            if (!enabledFeatures.containsAll(deps)) {
                Set<String> missing = new HashSet<>(deps);
                missing.removeAll(enabledFeatures);

                throw badRequest(
                        "RUNTIME_NAV_DEPENDENCY_MISSING",
                        "Navigation item requires missing features",
                        Map.of(
                                "navId", nav.id(),
                                "missingFeatures", new ArrayList<>(missing),
                                "projectType", safeProjectType(projectType)
                        )
                );
            }
        }

        Set<String> missingRequiredNav = new HashSet<>(policy.requiredNavIds());
        missingRequiredNav.removeAll(presentIds);

        if (!missingRequiredNav.isEmpty()) {
            throw badRequest(
                    "RUNTIME_NAV_REQUIRED_MISSING",
                    "Required navigation items are missing",
                    Map.of(
                            "missingNavIds", new ArrayList<>(missingRequiredNav),
                            "projectType", safeProjectType(projectType)
                    )
            );
        }

        if ("BOTTOM".equals(menuType)) {
            int count = navItems.size();

            if (count < policy.bottomNavMin() || count > policy.bottomNavMax()) {
                throw badRequest(
                        "RUNTIME_BOTTOM_NAV_SIZE_INVALID",
                        "Bottom navigation item count is invalid",
                        Map.of(
                                "count", count,
                                "min", policy.bottomNavMin(),
                                "max", policy.bottomNavMax(),
                                "projectType", safeProjectType(projectType)
                        )
                );
            }
        }
    }

    private void validateHome(
            RuntimePolicy policy,
            List<HomeSection> sections,
            Set<String> enabledFeatures,
            ProjectType projectType
    ) {
        for (HomeSection section : sections) {
            if (!policy.allowedHomeTypes().contains(section.type())) {
                throw badRequest(
                        "RUNTIME_HOME_TYPE_NOT_ALLOWED",
                        "Home section type is not allowed",
                        Map.of("type", section.type(), "projectType", safeProjectType(projectType))
                );
            }

            if (section.feature() != null && !section.feature().isBlank()) {
                if (!policy.allowedFeatures().contains(section.feature())) {
                    throw badRequest(
                            "RUNTIME_HOME_FEATURE_NOT_ALLOWED",
                            "Home section feature reference is not allowed",
                            Map.of("feature", section.feature(), "projectType", safeProjectType(projectType))
                    );
                }

                if (!enabledFeatures.contains(section.feature())) {
                    throw badRequest(
                            "RUNTIME_HOME_FEATURE_DISABLED",
                            "Home section references a disabled feature",
                            Map.of("feature", section.feature(), "projectType", safeProjectType(projectType))
                    );
                }
            }
        }
    }

    private LinkedHashSet<String> parseFeatures(String json) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (json == null || json.isBlank()) {
            return out;
        }

        JsonNode root = readTree(json, "INVALID_ENABLED_FEATURES_JSON", "enabledFeaturesJson must be a JSON array");
        if (!root.isArray()) {
            throw badRequest(
                    "INVALID_ENABLED_FEATURES_JSON",
                    "enabledFeaturesJson must be a JSON array",
                    Map.of("field", "enabledFeaturesJson")
            );
        }

        for (JsonNode node : root) {
            String feature = normalizeUpper(node.asText(null));
            if (feature != null && !feature.isBlank()) {
                out.add(feature);
            }
        }
        return out;
    }

    private List<NavItem> parseNav(String json) {
        List<NavItem> out = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return out;
        }

        JsonNode root = readTree(json, "INVALID_NAV_JSON", "navJson must be a JSON array");
        if (!root.isArray()) {
            throw badRequest(
                    "INVALID_NAV_JSON",
                    "navJson must be a JSON array",
                    Map.of("field", "navJson")
            );
        }

        for (JsonNode node : root) {
            if (!node.isObject()) {
                throw badRequest(
                        "INVALID_NAV_JSON",
                        "Each nav item must be a JSON object",
                        Map.of("field", "navJson")
                );
            }

            String id = normalizeUpper(requiredText(node, "id", "navJson"));
            String label = trimToNull(node.path("label").asText(null));
            String icon = trimToNull(node.path("icon").asText(null));

            out.add(new NavItem(
                    id,
                    label == null ? id : label,
                    icon == null ? "" : icon
            ));
        }

        return out;
    }

    private List<HomeSection> parseHome(String json) {
        List<HomeSection> out = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return out;
        }

        JsonNode root = readTree(json, "INVALID_HOME_JSON", "homeJson must be a JSON array or object with sections");
        JsonNode sectionsNode = root;

        if (root.isObject() && root.has("sections")) {
            sectionsNode = root.get("sections");
        }

        if (!sectionsNode.isArray()) {
            throw badRequest(
                    "INVALID_HOME_JSON",
                    "homeJson must be a JSON array or object with sections",
                    Map.of("field", "homeJson")
            );
        }

        for (JsonNode node : sectionsNode) {
            if (!node.isObject()) {
                throw badRequest(
                        "INVALID_HOME_JSON",
                        "Each home section must be a JSON object",
                        Map.of("field", "homeJson")
                );
            }

            String type = normalizeUpper(requiredText(node, "type", "homeJson"));
            String layout = normalizeUpper(node.path("layout").asText("FULL"));
            int limit = node.path("limit").asInt(0);
            String feature = normalizeUpper(trimToNull(node.path("feature").asText(null)));

            out.add(new HomeSection(type, layout, limit, feature));
        }

        return out;
    }

    private ObjectNode parseBranding(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode().put("menuType", "bottom");
        }

        JsonNode root = readTree(json, "INVALID_BRANDING_JSON", "brandingJson must be a JSON object");
        if (!root.isObject()) {
            throw badRequest(
                    "INVALID_BRANDING_JSON",
                    "brandingJson must be a JSON object",
                    Map.of("field", "brandingJson")
            );
        }

        ObjectNode obj = (ObjectNode) root.deepCopy();
        if (!obj.has("menuType") || trimToNull(obj.path("menuType").asText(null)) == null) {
            obj.put("menuType", "bottom");
        }
        return obj;
    }

    private String writeNav(List<NavItem> navItems) {
        ArrayNode arr = objectMapper.createArrayNode();
        for (NavItem nav : navItems) {
            ObjectNode n = objectMapper.createObjectNode();
            n.put("id", nav.id());
            n.put("label", nav.label());
            n.put("icon", nav.icon());
            arr.add(n);
        }
        return writeJson(arr);
    }

    private String writeHome(List<HomeSection> sections) {
        ArrayNode arr = objectMapper.createArrayNode();
        for (HomeSection section : sections) {
            ObjectNode n = objectMapper.createObjectNode();
            n.put("type", section.type());
            n.put("layout", section.layout());
            n.put("limit", section.limit());
            if (section.feature() != null && !section.feature().isBlank()) {
                n.put("feature", section.feature());
            }
            arr.add(n);
        }
        return writeJson(arr);
    }

    private String writeFeatures(Set<String> enabledFeatures) {
        List<String> features = new ArrayList<>(enabledFeatures);
        Collections.sort(features);
        return writeJson(objectMapper.valueToTree(features));
    }

    private String writeBranding(ObjectNode brandingNode) {
        return writeJson(brandingNode);
    }

    private JsonNode readTree(String json, String code, String message) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw badRequest(code, message, Map.of("raw", json));
        }
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize runtime config", ex);
        }
    }

    private String requiredText(JsonNode node, String fieldName, String parentField) {
        String value = trimToNull(node.path(fieldName).asText(null));
        if (value == null) {
            throw badRequest(
                    "RUNTIME_FIELD_REQUIRED",
                    fieldName + " is required",
                    Map.of("field", parentField + "." + fieldName)
            );
        }
        return value;
    }

    private String normalizeUpper(String value) {
        String v = trimToNull(value);
        return v == null ? null : v.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }

    private String safeProjectType(ProjectType projectType) {
        return projectType == null ? "ECOMMERCE" : projectType.name();
    }

    private ApiException badRequest(String code, String message, Map<String, Object> details) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message, details);
    }
}