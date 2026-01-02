package com.build4all.shipping.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SeedDataset {
    public String projectName;
    public String projectDescription;
    public String projectType; // ECOMMERCE/SERVICES/ACTIVITIES

    public Owner owner;
    public Tenant tenant;

    public List<CategorySeed> categories = new ArrayList<>();
    public List<ItemTypeSeed> itemTypes = new ArrayList<>();
    public List<ProductSeed> products = new ArrayList<>();
    public List<TaxRuleSeed> taxRules = new ArrayList<>();
    public List<ShippingMethodSeed> shippingMethods = new ArrayList<>();
    public List<CouponSeed> coupons = new ArrayList<>();

    public static class Owner {
        public String username;
        public String firstName;
        public String lastName;
        public String email;
        public String password;
        public String role;
    }

    public static class Tenant {
        public String slug;
        public String appName;
        public String status;
        public String currencyCode;
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
        public boolean defaultForCategory;
    }

    public static class ProductSeed {
        public String name;
        public String sku;
        public String productType;
        public String itemTypeName;
        public BigDecimal price;
        public Integer stock;
        public String imageUrl;
        public String description;
        public String status;
        public Boolean virtualProduct;
        public Boolean downloadable;
        public String categoryName; // optional for validation/report
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
        public String type;
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
        public String type;
        public BigDecimal value;
        public Integer globalUsageLimit;
        public BigDecimal maxDiscountAmount;
        public BigDecimal minOrderAmount;
        public LocalDateTime validFrom;
        public LocalDateTime validTo;
        public Boolean active;
    }
}
