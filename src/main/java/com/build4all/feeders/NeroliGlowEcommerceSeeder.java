package com.build4all.feeders;

import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.admin.repository.AdminUsersRepository;
import com.build4all.catalog.domain.Category;
import com.build4all.catalog.domain.Country;
import com.build4all.catalog.domain.Currency;
import com.build4all.catalog.domain.ItemType;
import com.build4all.catalog.domain.Region;
import com.build4all.catalog.repository.CategoryRepository;
import com.build4all.catalog.repository.CountryRepository;
import com.build4all.catalog.repository.CurrencyRepository;
import com.build4all.catalog.repository.ItemTypeRepository;
import com.build4all.catalog.repository.RegionRepository;
import com.build4all.features.ecommerce.domain.Product;
import com.build4all.features.ecommerce.domain.ProductType;
import com.build4all.features.ecommerce.repository.ProductRepository;
import com.build4all.project.domain.Project;
import com.build4all.project.repository.ProjectRepository;
import com.build4all.promo.domain.Coupon;
import com.build4all.promo.domain.CouponDiscountType;
import com.build4all.promo.repository.CouponRepository;
import com.build4all.role.domain.Role;
import com.build4all.role.repository.RoleRepository;
import com.build4all.shipping.domain.ShippingMethod;
import com.build4all.shipping.domain.ShippingMethodType;
import com.build4all.shipping.repository.ShippingMethodRepository;
import com.build4all.tax.domain.TaxRule;
import com.build4all.tax.repository.TaxRuleRepository;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * NeroliGlow seed data loader (JSON -> JPA repositories)
 *
 * Usage:
 *   1) Put JSON file under: src/main/resources/seed/neroliglow_seed_dataset_URL.json
 *   2) Run with profile "seed":
 *        mvn spring-boot:run -Dspring-boot.run.profiles=seed
 */
@Configuration
@Profile("seed")
public class NeroliGlowEcommerceSeeder {

    // ---------- JSON model ----------
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SeedDataset {
        public String projectName;
        public String projectDescription;
        public Owner owner;
        public Tenant tenant;

        public List<CategorySeed> categories = new ArrayList<>();
        public List<ItemTypeSeed> itemTypes = new ArrayList<>();
        public List<ProductSeed> products = new ArrayList<>();

        // NEW:
        public List<TaxRuleSeed> taxRules = new ArrayList<>();
        public List<ShippingMethodSeed> shippingMethods = new ArrayList<>();
        public List<CouponSeed> coupons = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Owner {
        public String username;
        public String firstName;
        public String lastName;
        public String email;
        public String password;
        public String role; // OWNER
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Tenant {
        public String slug;
        public String appName;
        public String status; // ACTIVE
        public String currencyCode; // USD
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CategorySeed {
        public String name;

        @JsonAlias({"iconName","icon"})
        public String iconName;

        public String iconLibrary;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ItemTypeSeed {
        public String name;
        public String categoryName;

        @JsonAlias({"iconName","icon"})
        public String iconName;

        public String iconLibrary;
        public boolean defaultForCategory;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProductSeed {
        public String name;
        public String sku;
        public String productType;     // SIMPLE | VARIABLE | ...
        public String itemTypeName;    // must exist
        public BigDecimal price;
        public Integer stock;
        public String imageUrl;
        public String description;

        public String status;
        public Boolean virtualProduct;
        public Boolean downloadable;
    }

    // ===== TAX RULE JSON (maps to your TaxRule) =====
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TaxRuleSeed {
        public String name;                 // "VAT Lebanon 11%"
        public BigDecimal rate;             // 11.00 (%)
        public Boolean appliesToShipping;   // true/false
        public Boolean enabled;             // true/false

        public String countryIso2;          // "LB"
        public String countryIso3;          // optional "LBN"
        public String regionCode;           // Region.code
    }

    // ===== SHIPPING JSON (maps to your ShippingMethod) =====
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ShippingMethodSeed {
        public String name;
        public String description;
        public String type;                 // FLAT_RATE, FREE_OVER_THRESHOLD, ...
        public BigDecimal flatRate;
        public BigDecimal pricePerKg;
        public BigDecimal freeShippingThreshold;
        public Boolean enabled;

        public String countryIso2;
        public String countryIso3;
        public String regionCode;
    }

    // ===== COUPON JSON (maps to your promo.Coupon) =====
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CouponSeed {
        public String code;
        public String description;
        public String type;                 // PERCENT | FIXED | FREE_SHIPPING
        public BigDecimal value;
        public Integer globalUsageLimit;
        public BigDecimal maxDiscountAmount;
        public BigDecimal minOrderAmount;
        public LocalDateTime validFrom;
        public LocalDateTime validTo;
        public Boolean active;
    }

    @Value("classpath:seed/neroliglow_seed_dataset_URL.json")
    private Resource seedJson;

    @Bean
    public CommandLineRunner seedNeroliGlow(
            ObjectMapper mapper,
            PasswordEncoder passwordEncoder,

            ProjectRepository projectRepo,
            CategoryRepository categoryRepo,
            ItemTypeRepository itemTypeRepo,
            RoleRepository roleRepo,
            AdminUsersRepository adminRepo,
            AdminUserProjectRepository aupRepo,
            CurrencyRepository currencyRepo,
            ProductRepository productRepo,

            TaxRuleRepository taxRuleRepo,
            CountryRepository countryRepo,
            RegionRepository regionRepo,
            ShippingMethodRepository shippingRepo,
            CouponRepository couponRepo
    ) {
        return args -> {
            System.out.println("🧪 NeroliGlow JSON seeder running (profile=seed) ...");

            SeedDataset data = mapper.readValue(seedJson.getInputStream(), SeedDataset.class);

            // 1) Project
            Project project = projectRepo.findByProjectNameIgnoreCase(data.projectName)
                    .orElseGet(() -> {
                        Project p = new Project();
                        p.setProjectName(data.projectName);
                        p.setDescription(data.projectDescription);
                        p.setActive(true);
                        return projectRepo.save(p);
                    });

            // 2) Categories (project-scoped)
            Map<String, Category> categoriesByName = new HashMap<>();
            for (CategorySeed cs : data.categories) {
                Category c = categoryRepo.findByNameIgnoreCaseAndProject_Id(cs.name, project.getId())
                        .orElseGet(() -> {
                            Category created = new Category();
                            created.setProject(project);
                            created.setName(cs.name);
                            created.setIconName(cs.iconName);
                            created.setIconLibrary(cs.iconLibrary);
                            return categoryRepo.save(created);
                        });
                categoriesByName.put(cs.name.toUpperCase(), c);
            }

            // 3) ItemTypes
            Map<String, ItemType> itemTypesByName = new HashMap<>();
            for (ItemTypeSeed its : data.itemTypes) {
                Category cat = categoriesByName.get(its.categoryName.toUpperCase());
                if (cat == null) {
                    throw new IllegalStateException("Missing category for itemType: " + its.name + " -> " + its.categoryName);
                }

                ItemType t = itemTypeRepo.findByName(its.name)
                        .orElseGet(() -> {
                            ItemType created = new ItemType();
                            created.setName(its.name);
                            created.setIcon(its.iconName);
                            created.setIconLibrary(its.iconLibrary);
                            created.setCategory(cat);
                            created.setDefaultForCategory(its.defaultForCategory);
                            return itemTypeRepo.save(created);
                        });

                boolean dirty = false;
                if (t.getCategory() == null || !Objects.equals(t.getCategory().getId(), cat.getId())) {
                    t.setCategory(cat);
                    dirty = true;
                }
                if (t.isDefaultForCategory() != its.defaultForCategory) {
                    t.setDefaultForCategory(its.defaultForCategory);
                    dirty = true;
                }
                if (dirty) itemTypeRepo.save(t);

                itemTypesByName.put(its.name.toLowerCase(), t);
            }

            // 4) Role + Admin owner
            String ownerRoleName = (data.owner != null && data.owner.role != null) ? data.owner.role : "OWNER";
            Role ownerRole = roleRepo.findByNameIgnoreCase(ownerRoleName)
                    .orElseGet(() -> roleRepo.save(new Role(ownerRoleName.toUpperCase())));

            AdminUser owner = adminRepo.findByEmail(data.owner.email)
                    .orElseGet(() -> {
                        AdminUser a = new AdminUser();
                        a.setUsername(data.owner.username);
                        a.setFirstName(data.owner.firstName);
                        a.setLastName(data.owner.lastName);
                        a.setEmail(data.owner.email);
                        a.setPasswordHash(passwordEncoder.encode(data.owner.password));
                        a.setRole(ownerRole);
                        return adminRepo.save(a);
                    });

            // 5) Tenant link (AdminUserProject)
            AdminUserProject aup = aupRepo
                    .findByAdmin_AdminIdAndProject_IdAndSlug(owner.getAdminId(), project.getId(), data.tenant.slug)
                    .orElseGet(() -> {
                        AdminUserProject link = new AdminUserProject();
                        link.setAdmin(owner);
                        link.setProject(project);
                        link.setSlug(data.tenant.slug);
                        link.setAppName(data.tenant.appName);
                        link.setStatus(data.tenant.status != null ? data.tenant.status : "ACTIVE");
                        link.setValidFrom(LocalDate.now());
                        link.setEndTo(LocalDate.now().plusYears(1));
                        return aupRepo.save(link);
                    });

            // 6) Currency
            Currency currency = currencyRepo.findByCodeIgnoreCase(data.tenant.currencyCode)
                    .orElseGet(() -> currencyRepo.findByCurrencyType("DOLLAR")
                            .orElseThrow(() -> new IllegalStateException("USD currency not found. Ensure CurrencySeeder ran.")));

            // 7) Products (tenant-scoped) - avoid duplicates by sku or name
            List<Product> existingProducts = productRepo.findByOwnerProject_Id(aup.getId());
            Set<String> existingSkus = new HashSet<>();
            Set<String> existingNames = new HashSet<>();
            for (Product p : existingProducts) {
                if (p.getSku() != null) existingSkus.add(p.getSku().toUpperCase());
                if (p.getName() != null) existingNames.add(p.getName().toLowerCase());
            }

            int insertedProducts = 0;
            for (ProductSeed ps : data.products) {
                String skuKey = ps.sku != null ? ps.sku.toUpperCase() : null;
                String nameKey = ps.name != null ? ps.name.toLowerCase() : null;

                if (skuKey != null && existingSkus.contains(skuKey)) continue;
                if (skuKey == null && nameKey != null && existingNames.contains(nameKey)) continue;

                ItemType itemType = itemTypesByName.get(ps.itemTypeName.toLowerCase());
                if (itemType == null) {
                    throw new IllegalStateException("Missing itemType for product: " + ps.name + " -> " + ps.itemTypeName);
                }

                Product p = new Product();
                p.setOwnerProject(aup);
                p.setItemType(itemType);
                p.setItemName(ps.name);
                p.setDescription(ps.description);
                p.setPrice(ps.price != null ? ps.price : BigDecimal.ZERO);
                p.setCurrency(currency);
                p.setStock(ps.stock != null ? ps.stock : 0);

                p.setStatus(ps.status != null ? ps.status : "Active");
                p.setImageUrl(ps.imageUrl);
                p.setSku(ps.sku);
                p.setProductType(ps.productType != null ? ProductType.valueOf(ps.productType) : ProductType.SIMPLE);

                p.setVirtualProduct(ps.virtualProduct != null && ps.virtualProduct);
                p.setDownloadable(ps.downloadable != null && ps.downloadable);

                productRepo.save(p);
                insertedProducts++;
            }

            // 8) TAX RULES (tenant-scoped)
            List<TaxRule> existingRules = taxRuleRepo.findByOwnerProject_Id(aup.getId());
            Set<String> existingRuleNames = new HashSet<>();
            for (TaxRule r : existingRules) {
                if (r.getName() != null) existingRuleNames.add(r.getName().trim().toLowerCase());
            }

            int insertedTaxRules = 0;
            for (TaxRuleSeed ts : data.taxRules) {
                if (ts == null || ts.name == null || ts.name.isBlank()) continue;

                String key = ts.name.trim().toLowerCase();
                if (existingRuleNames.contains(key)) continue;

                TaxRule rule = new TaxRule();
                rule.setOwnerProject(aup);
                rule.setName(ts.name.trim());
                rule.setRate(ts.rate != null ? ts.rate : BigDecimal.ZERO);
                rule.setAppliesToShipping(Boolean.TRUE.equals(ts.appliesToShipping));
                rule.setEnabled(ts.enabled == null || ts.enabled);

                Country c = resolveCountry(countryRepo, ts.countryIso2, ts.countryIso3);
                if (c != null) rule.setCountry(c);

                Region region = resolveRegion(regionRepo, c, ts.regionCode);
                if (region != null) rule.setRegion(region);

                taxRuleRepo.save(rule);
                insertedTaxRules++;
                existingRuleNames.add(key);
            }

            // 9) SHIPPING METHODS (tenant-scoped)
            List<ShippingMethod> existingShipping = shippingRepo.findByOwnerProject_Id(aup.getId());
            Set<String> existingShippingNames = new HashSet<>();
            for (ShippingMethod m : existingShipping) {
                if (m.getName() != null) existingShippingNames.add(m.getName().trim().toLowerCase());
            }

            int insertedShippingMethods = 0;
            for (ShippingMethodSeed sm : data.shippingMethods) {
                if (sm == null || sm.name == null || sm.name.isBlank()) continue;

                String key = sm.name.trim().toLowerCase();
                if (existingShippingNames.contains(key)) continue;

                ShippingMethod m = new ShippingMethod();
                m.setOwnerProject(aup);
                m.setName(sm.name.trim());
                m.setDescription(sm.description);

                ShippingMethodType type = ShippingMethodType.FLAT_RATE;
                if (sm.type != null && !sm.type.isBlank()) {
                    type = ShippingMethodType.valueOf(sm.type.trim().toUpperCase());
                }
                m.setType(type);

                if (sm.flatRate != null) m.setFlatRate(sm.flatRate);
                if (sm.pricePerKg != null) m.setPricePerKg(sm.pricePerKg);
                if (sm.freeShippingThreshold != null) m.setFreeShippingThreshold(sm.freeShippingThreshold);

                m.setEnabled(sm.enabled == null || sm.enabled);

                Country c = resolveCountry(countryRepo, sm.countryIso2, sm.countryIso3);
                if (c != null) m.setCountry(c);

                Region r = resolveRegion(regionRepo, c, sm.regionCode);
                if (r != null) m.setRegion(r);

                shippingRepo.save(m);
                insertedShippingMethods++;
                existingShippingNames.add(key);
            }

            // 10) COUPONS (tenant-scoped)
            List<Coupon> existingCoupons = couponRepo.findByOwnerProjectId(aup.getId());
            Set<String> existingCouponCodes = new HashSet<>();
            for (Coupon c : existingCoupons) {
                if (c.getCode() != null) existingCouponCodes.add(c.getCode().trim().toLowerCase());
            }

            int insertedCoupons = 0;
            for (CouponSeed cs : data.coupons) {
                if (cs == null || cs.code == null || cs.code.isBlank()) continue;

                String codeKey = cs.code.trim().toLowerCase();
                if (existingCouponCodes.contains(codeKey)) continue;

                Coupon c = new Coupon();
                c.setOwnerProjectId(aup.getId());
                c.setCode(cs.code.trim().toUpperCase());
                c.setDescription(cs.description);

                CouponDiscountType t = CouponDiscountType.PERCENT;
                if (cs.type != null && !cs.type.isBlank()) {
                    t = CouponDiscountType.valueOf(cs.type.trim().toUpperCase());
                }
                c.setType(t);

                c.setValue(cs.value);
                c.setGlobalUsageLimit(cs.globalUsageLimit);
                c.setMaxDiscountAmount(cs.maxDiscountAmount);
                c.setMinOrderAmount(cs.minOrderAmount);
                c.setValidFrom(cs.validFrom);
                c.setValidTo(cs.validTo);
                c.setActive(cs.active == null || cs.active);

                couponRepo.save(c);
                insertedCoupons++;
                existingCouponCodes.add(codeKey);
            }

            System.out.println("✅ NeroliGlow seeding complete.");
            System.out.println("   Inserted products       : " + insertedProducts);
            System.out.println("   Inserted tax rules      : " + insertedTaxRules);
            System.out.println("   Inserted shipping meths : " + insertedShippingMethods);
            System.out.println("   Inserted coupons        : " + insertedCoupons);
            System.out.println("   Tenant (aup_id) = " + aup.getId() + ", slug=" + aup.getSlug());
            System.out.println("   Owner login (admin): " + data.owner.email + " / " + data.owner.password);
        };
    }

    // ---------- helpers ----------
    private static Country resolveCountry(CountryRepository repo, String iso2, String iso3) {
        if (iso2 != null && !iso2.isBlank()) {
            return repo.findByIso2CodeIgnoreCase(iso2.trim()).orElse(null);
        }
        if (iso3 != null && !iso3.isBlank()) {
            return repo.findByIso3CodeIgnoreCase(iso3.trim()).orElse(null);
        }
        return null;
    }

    private static Region resolveRegion(RegionRepository repo, Country country, String regionCode) {
        if (country == null) return null;
        if (regionCode == null || regionCode.isBlank()) return null;
        return repo.findByCountryAndCodeIgnoreCase(country, regionCode.trim()).orElse(null);
    }
}