// File: src/main/java/com/build4all/feeders/importer/dto/SeedDataset.java
package com.build4all.importer.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Parsed from Excel.
 * Keep fields aligned with your template sheets/columns.
 */
public class SeedDataset {

    // SETUP
    public String projectName;
    public String projectDescription;

    // Optional (recommended): make your Excel include it (ECOMMERCE/SERVICES/ACTIVITIES)
    public String projectType; // will map to com.build4all.project.domain.ProjectType

    public Owner owner = new Owner();
    public Tenant tenant = new Tenant();

    // Lists
    public List<CategorySeed> categories = new ArrayList<>();
    public List<ItemTypeSeed> itemTypes = new ArrayList<>();
    public List<ProductSeed> products = new ArrayList<>();
    public List<TaxRuleSeed> taxRules = new ArrayList<>();
    public List<ShippingMethodSeed> shippingMethods = new ArrayList<>();
    public List<CouponSeed> coupons = new ArrayList<>();

    // ---------- inner DTOs ----------
    public static class Owner {
        public String username;
        public String firstName;
        public String lastName;
        public String email;
        public String password;
        public String role; // OWNER
    }

    public static class Tenant {
        public String slug;
        public String appName;
        public String status;       // ACTIVE
        public String currencyCode; // USD/LBP/...
    }

    public static class CategorySeed {
        public String name;
        public String iconName;
        public String iconLibrary;
        public Boolean includeInMenu;
        public Integer weight;
    }

    public static class ItemTypeSeed {
        public String name;
        public String categoryName;
        public String iconName;
        public String iconLibrary;
        public Boolean defaultForCategory;
    }

    public static class ProductSeed {
        public String name;
        public String description;
        public BigDecimal price;
        public String status;
        public Integer stock;
        public String sku;
        public String productType;  // SIMPLE/VARIABLE/...
        public Boolean virtualProduct;
        public Boolean downloadable;
        public String imageUrl;
        public String imageRemoteUrl; // if you want it
        public String categoryName;   // optional if itemType implies category
        public String itemTypeName;   // FK to ItemType.name
    }

    public static class TaxRuleSeed {
        public String name;
        public BigDecimal rate;
        public Boolean appliesToShipping;
        public Boolean enabled;
        public String countryIso2;
        public String countryIso3;
        public String regionCode;
    }

    public static class ShippingMethodSeed {
        public String name;
        public String description;
        public String type; // FLAT_RATE, FREE_OVER_THRESHOLD, LOCAL_PICKUP...
        public BigDecimal flatRate;
        public BigDecimal pricePerKg;
        public BigDecimal freeShippingThreshold;
        public Boolean enabled;
        public String countryIso2;
        public String countryIso3;
        public String regionCode;
    }

    public static class CouponSeed {
        public String code;
        public String description;
        public String type; // PERCENT, FIXED, FREE_SHIPPING
        public BigDecimal value;
        public Integer globalUsageLimit;
        public BigDecimal maxDiscountAmount;
        public BigDecimal minOrderAmount;
        public LocalDateTime validFrom;
        public LocalDateTime validTo;
        public Boolean active;
    }
}
