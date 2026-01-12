package com.build4all.importer.importer;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.catalog.domain.*;
import com.build4all.catalog.repository.*;
import com.build4all.features.ecommerce.domain.Product;
import com.build4all.features.ecommerce.domain.ProductType;
import com.build4all.features.ecommerce.repository.ProductRepository;
import com.build4all.importer.dto.SeedDataset;
import com.build4all.importer.model.ExcelImportResult;
import com.build4all.importer.service.ExistingTenantResolver;
import com.build4all.promo.domain.Coupon;
import com.build4all.promo.domain.CouponDiscountType;
import com.build4all.promo.repository.CouponRepository;
import com.build4all.shipping.domain.ShippingMethod;
import com.build4all.shipping.domain.ShippingMethodType;
import com.build4all.shipping.repository.ShippingMethodRepository;
import com.build4all.tax.domain.TaxRule;
import com.build4all.tax.repository.TaxRuleRepository;
import  com.build4all.catalog.domain.Currency;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
public class DatasetImporterImpl implements DatasetImporter {

    private final AdminUserProjectRepository aupRepo;

    private final CategoryRepository categoryRepo;
    private final ItemTypeRepository itemTypeRepo;
    private final CurrencyRepository currencyRepo;
    private final ProductRepository productRepo;

    private final TaxRuleRepository taxRuleRepo;
    private final CountryRepository countryRepo;
    private final RegionRepository regionRepo;

    private final ShippingMethodRepository shippingRepo;
    private final CouponRepository couponRepo;

    public DatasetImporterImpl(
            AdminUserProjectRepository aupRepo,
            CategoryRepository categoryRepo,
            ItemTypeRepository itemTypeRepo,
            CurrencyRepository currencyRepo,
            ProductRepository productRepo,
            TaxRuleRepository taxRuleRepo,
            CountryRepository countryRepo,
            RegionRepository regionRepo,
            ShippingMethodRepository shippingRepo,
            CouponRepository couponRepo
    ) {
        this.aupRepo = aupRepo;
        this.categoryRepo = categoryRepo;
        this.itemTypeRepo = itemTypeRepo;
        this.currencyRepo = currencyRepo;
        this.productRepo = productRepo;
        this.taxRuleRepo = taxRuleRepo;
        this.countryRepo = countryRepo;
        this.regionRepo = regionRepo;
        this.shippingRepo = shippingRepo;
        this.couponRepo = couponRepo;
    }

    @Override
    public ExcelImportResult importAll(SeedDataset data, ExistingTenantResolver.Resolved resolved) {

        AdminUserProject aup = aupRepo.findById(resolved.ownerProjectId())
                .orElseThrow(() -> new IllegalStateException("AdminUserProject not found: " + resolved.ownerProjectId()));

        ExcelImportResult res = ExcelImportResult.ok("Imported successfully");

        // ✅ Currency: SETUP removed. We must get currency from DB or use a fallback.
        Currency currency = resolveTenantCurrencyOrDefault(aup);

        // 1) Categories (project-scoped)
        Map<String, Category> categoriesByName = new HashMap<>();
        for (var cs : data.categories) {
            if (cs == null || blank(cs.name)) continue;

            Category c = categoryRepo.findByNameIgnoreCaseAndProject_Id(cs.name, aup.getProject().getId())
                    .orElseGet(() -> {
                        Category created = new Category();
                        created.setProject(aup.getProject());
                        created.setName(cs.name);
                        created.setIconName(cs.iconName);
                        created.setIconLibrary(cs.iconLibrary);
                        return categoryRepo.save(created);
                    });

            categoriesByName.put(cs.name.trim().toUpperCase(), c);
            res.insertedCategories++;
        }

        // 2) ItemTypes
        Map<String, ItemType> itemTypesByName = new HashMap<>();
        for (var its : data.itemTypes) {
            if (its == null || blank(its.name)) continue;

            Category cat = categoriesByName.get(its.categoryName.trim().toUpperCase());
            if (cat == null) throw new IllegalStateException("Missing category for itemType: " + its.name);

            ItemType t = itemTypeRepo
            	    .findByNameIgnoreCaseAndCategory_Project_Id(its.name.trim(), aup.getProject().getId())
            	    .orElseGet(() -> {
            	        ItemType created = new ItemType();
            	        created.setName(its.name.trim());
            	        created.setIcon(its.iconName);
            	        created.setIconLibrary(its.iconLibrary);
            	        created.setCategory(cat);
            	        created.setDefaultForCategory(Boolean.TRUE.equals(its.defaultForCategory));
            	        return itemTypeRepo.save(created);
            	    });


            boolean dirty = false;
            if (t.getCategory() == null || !Objects.equals(t.getCategory().getId(), cat.getId())) {
                t.setCategory(cat);
                dirty = true;
            }
            if (t.isDefaultForCategory() != Boolean.TRUE.equals(its.defaultForCategory)) {
                t.setDefaultForCategory(Boolean.TRUE.equals(its.defaultForCategory));
                dirty = true;
            }
            if (dirty) itemTypeRepo.save(t);

            itemTypesByName.put(its.name.trim().toLowerCase(), t);
            res.insertedItemTypes++;
        }

        // 3) Products (tenant-scoped)
        Set<String> existingSkus = new HashSet<>();
        for (Product p : productRepo.findByOwnerProject_Id(aup.getId())) {
            if (p.getSku() != null) existingSkus.add(p.getSku().trim().toUpperCase());
        }

        for (var ps : data.products) {
            if (ps == null || blank(ps.name)) continue;

            String skuKey = ps.sku != null ? ps.sku.trim().toUpperCase() : null;
         
            

            ItemType itemType = itemTypesByName.get(ps.itemTypeName.trim().toLowerCase());
            if (itemType == null) throw new IllegalStateException("Missing itemType for product: " + ps.name);

            String finalSku = ensureUniqueSku(ps.sku, ps.name, existingSkus);
            
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
            p.setSku(finalSku);
            p.setProductType(ps.productType != null
                    ? ProductType.valueOf(ps.productType.trim().toUpperCase())
                    : ProductType.SIMPLE);

            p.setVirtualProduct(Boolean.TRUE.equals(ps.virtualProduct));
            p.setDownloadable(Boolean.TRUE.equals(ps.downloadable));

            productRepo.save(p);
            res.insertedProducts++;
            if (skuKey != null) existingSkus.add(skuKey);
        }

        // 4) Tax rules
        Set<String> existingRuleNames = new HashSet<>();
        for (TaxRule r : taxRuleRepo.findByOwnerProject_Id(aup.getId())) {
            if (r.getName() != null) existingRuleNames.add(r.getName().trim().toLowerCase());
        }

        for (var ts : data.taxRules) {
            if (ts == null || blank(ts.name)) continue;
            String key = ts.name.trim().toLowerCase();
            if (existingRuleNames.contains(key)) continue;

            TaxRule rule = new TaxRule();
            rule.setOwnerProject(aup);
            rule.setName(ts.name.trim());
            rule.setRate(ts.rate != null ? ts.rate : BigDecimal.ZERO);
            rule.setAppliesToShipping(Boolean.TRUE.equals(ts.appliesToShipping));
            rule.setEnabled(ts.enabled == null || ts.enabled);

            Country c = resolveCountry(ts.countryIso2, ts.countryIso3);
            if (c != null) rule.setCountry(c);

            Region region = resolveRegion(c, ts.regionCode);
            if (region != null) rule.setRegion(region);

            taxRuleRepo.save(rule);
            res.insertedTaxRules++;
            existingRuleNames.add(key);
        }

        // 5) Shipping methods
        Set<String> existingShipNames = new HashSet<>();
        for (ShippingMethod m : shippingRepo.findByOwnerProject_Id(aup.getId())) {
            if (m.getName() != null) existingShipNames.add(m.getName().trim().toLowerCase());
        }

        for (var sm : data.shippingMethods) {
            if (sm == null || blank(sm.name)) continue;
            String key = sm.name.trim().toLowerCase();
            if (existingShipNames.contains(key)) continue;

            ShippingMethod m = new ShippingMethod();
            m.setOwnerProject(aup);
            m.setName(sm.name.trim());
            m.setDescription(sm.description);

            ShippingMethodType type = ShippingMethodType.FLAT_RATE;
            if (!blank(sm.type)) type = ShippingMethodType.valueOf(sm.type.trim().toUpperCase());
            m.setType(type);

            if (sm.flatRate != null) m.setFlatRate(sm.flatRate);
            if (sm.pricePerKg != null) m.setPricePerKg(sm.pricePerKg);
            if (sm.freeShippingThreshold != null) m.setFreeShippingThreshold(sm.freeShippingThreshold);

            m.setEnabled(sm.enabled == null || sm.enabled);

            Country c = resolveCountry(sm.countryIso2, sm.countryIso3);
            if (c != null) m.setCountry(c);

            Region r = resolveRegion(c, sm.regionCode);
            if (r != null) m.setRegion(r);

            shippingRepo.save(m);
            res.insertedShippingMethods++;
            existingShipNames.add(key);
        }

        // 6) Coupons
        Set<String> existingCouponCodes = new HashSet<>();
        for (Coupon c : couponRepo.findByOwnerProjectId(aup.getId())) {
            if (c.getCode() != null) existingCouponCodes.add(c.getCode().trim().toLowerCase());
        }

        for (var cs : data.coupons) {
            if (cs == null || blank(cs.code)) continue;
            String key = cs.code.trim().toLowerCase();
            if (existingCouponCodes.contains(key)) continue;

            Coupon c = new Coupon();
            c.setOwnerProjectId(aup.getId());
            c.setCode(cs.code.trim().toUpperCase());
            c.setDescription(cs.description);

            CouponDiscountType t = CouponDiscountType.PERCENT;
            if (!blank(cs.type)) t = CouponDiscountType.valueOf(cs.type.trim().toUpperCase());
            c.setType(t);

            c.setValue(cs.value);
            c.setGlobalUsageLimit(cs.globalUsageLimit);
            c.setMaxDiscountAmount(cs.maxDiscountAmount);
            c.setMinOrderAmount(cs.minOrderAmount);
            c.setValidFrom(cs.validFrom);
            c.setValidTo(cs.validTo);
            c.setActive(cs.active == null || cs.active);

            couponRepo.save(c);
            res.insertedCoupons++;
            existingCouponCodes.add(key);
        }

        return res;
    }

    /**
     * Currency resolution strategy:
     * 1) If you store currency on AUP somewhere => use it here (preferred)
     * 2) else fallback to USD
     */
    private Currency resolveTenantCurrencyOrDefault(AdminUserProject aup) {
        // TODO: If you have a field like aup.getCurrencyCode() or aup.getCurrency(), use it.

        // Fallback:
        return currencyRepo.findByCodeIgnoreCase("USD")
                .orElseThrow(() -> new IllegalStateException("Default currency USD not found in DB"));
    }

    private Country resolveCountry(String iso2, String iso3) {
        if (!blank(iso2)) return countryRepo.findByIso2CodeIgnoreCase(iso2.trim()).orElse(null);
        if (!blank(iso3)) return countryRepo.findByIso3CodeIgnoreCase(iso3.trim()).orElse(null);
        return null;
    }

    private Region resolveRegion(Country c, String regionCode) {
        if (c == null || blank(regionCode)) return null;
        return regionRepo.findByCountryAndCodeIgnoreCase(c, regionCode.trim()).orElse(null);
    }

    private static boolean blank(String s) { return s == null || s.trim().isEmpty(); }
    
    
    private String ensureUniqueSku(String rawSku, String name, Set<String> used) {
        String base = (rawSku != null && !rawSku.trim().isEmpty())
                ? rawSku.trim()
                : slugSku(name);

        String candidate = base.toUpperCase();
        if (!used.contains(candidate)) return candidate;

        int i = 2;
        while (used.contains((candidate + "-" + i))) i++;
        return candidate + "-" + i;
    }

    private String slugSku(String name) {
        if (name == null) return "SKU";
        String s = name.trim().toUpperCase();
        s = s.replaceAll("[^A-Z0-9]+", "-");
        s = s.replaceAll("(^-+|-+$)", "");
        return s.isBlank() ? "SKU" : s;
    }

}
