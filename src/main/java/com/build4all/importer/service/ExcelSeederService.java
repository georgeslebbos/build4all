package com.build4all.importer.service;

import com.build4all.catalog.domain.Country;
import com.build4all.catalog.domain.Region;
import com.build4all.catalog.repository.CountryRepository;
import com.build4all.catalog.repository.RegionRepository;
import com.build4all.features.ecommerce.domain.ProductType;
import com.build4all.importer.dto.SeedDataset;
import com.build4all.importer.model.ExcelImportResult;
import com.build4all.importer.model.ExcelValidationResult;
import com.build4all.importer.model.ImportOptions;
import com.build4all.importer.parser.ExcelSeedDatasetParser;
import com.build4all.promo.domain.CouponDiscountType;
import com.build4all.shipping.domain.ShippingMethodType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * ✅ SETUP is NOT required (and can be removed from Excel completely).
 * Validation focuses on:
 * - FK integrity between sheets
 * - enums correctness
 * - country/region correctness
 */
@Service
public class ExcelSeederService {

    private final ExcelSeedDatasetParser parser;
    private final CountryRepository countryRepo;
    private final RegionRepository regionRepo;
    private final ExcelImportCore importCore;

    public ExcelSeederService(
            ExcelSeedDatasetParser parser,
            CountryRepository countryRepo,
            RegionRepository regionRepo,
            ExcelImportCore importCore
    ) {
        this.parser = parser;
        this.countryRepo = countryRepo;
        this.regionRepo = regionRepo;
        this.importCore = importCore;
    }

    public ExcelValidationResult validateExcel(MultipartFile file) throws Exception {
        SeedDataset data = parser.parse(file.getInputStream());

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // ✅ IMPORTANT CHANGE:
        // No SETUP requirements anymore.
        // req(errors, data.projectName, ...)  => removed
        // req(errors, data.tenant.slug, ...)  => removed
        // req(errors, data.owner.email, ...)  => removed

        // FK checks
        Set<String> categories = new HashSet<>();
        for (var c : data.categories) {
            if (blank(c.name)) continue;
            String key = c.name.trim().toUpperCase();
            if (!categories.add(key)) warnings.add("Duplicate category: " + c.name);
        }

        Set<String> itemTypes = new HashSet<>();
        for (int i = 0; i < data.itemTypes.size(); i++) {
            var it = data.itemTypes.get(i);
            if (blank(it.name)) continue;

            if (blank(it.categoryName)) errors.add("ITEM_TYPES.row#" + (i + 2) + ": categoryName is required");
            else if (!categories.contains(it.categoryName.trim().toUpperCase()))
                errors.add("ITEM_TYPES.row#" + (i + 2) + ": categoryName not found in CATEGORIES: " + it.categoryName);

            String k = it.name.trim().toLowerCase();
            if (!itemTypes.add(k)) warnings.add("Duplicate itemType: " + it.name);
        }

        for (int i = 0; i < data.products.size(); i++) {
            var p = data.products.get(i);
            if (blank(p.name)) continue;

            if (blank(p.itemTypeName)) errors.add("PRODUCTS.row#" + (i + 2) + ": itemTypeName is required");
            else if (!itemTypes.contains(p.itemTypeName.trim().toLowerCase()))
                errors.add("PRODUCTS.row#" + (i + 2) + ": itemTypeName not found in ITEM_TYPES: " + p.itemTypeName);

            checkEnum(errors, "PRODUCTS.row#" + (i + 2) + ".productType", p.productType, ProductType.class, false);
        }

        for (int i = 0; i < data.shippingMethods.size(); i++) {
            checkEnum(errors, "SHIPPING_METHODS.row#" + (i + 2) + ".type",
                    data.shippingMethods.get(i).type, ShippingMethodType.class, false);
        }

        for (int i = 0; i < data.coupons.size(); i++) {
            checkEnum(errors, "COUPONS.row#" + (i + 2) + ".type",
                    data.coupons.get(i).type, CouponDiscountType.class, false);
        }

        validateCountryRegion(errors, "TAX_RULES", data);
        validateCountryRegion(errors, "SHIPPING_METHODS", data);

        ExcelValidationResult res = errors.isEmpty()
                ? ExcelValidationResult.ok()
                : ExcelValidationResult.fail(errors, warnings);

        res.categories = data.categories.size();
        res.itemTypes = data.itemTypes.size();
        res.products = data.products.size();
        res.taxRules = data.taxRules.size();
        res.shippingMethods = data.shippingMethods.size();
        res.coupons = data.coupons.size();
        res.warnings.addAll(warnings);

        return res;
    }

    @Transactional
    public ExcelImportResult importExcel(MultipartFile file, ImportOptions opts, Long ownerProjectId) throws Exception {
        ExcelValidationResult vr = validateExcel(file);
        if (!vr.valid) {
            ExcelImportResult fail = ExcelImportResult.fail("Validation failed. No data imported.", vr.errors);
            fail.warnings.addAll(vr.warnings);
            return fail;
        }

        SeedDataset data = parser.parse(file.getInputStream());
        return importCore.importDataset(data, opts, ownerProjectId);
    }

    // ---------------- helpers ----------------

    private void validateCountryRegion(List<String> errors, String sheetName, SeedDataset data) {
        if ("TAX_RULES".equals(sheetName)) {
            for (int i = 0; i < data.taxRules.size(); i++) {
                var tr = data.taxRules.get(i);
                validateCountryRegionRow(errors, sheetName, i + 2, tr.countryIso2, tr.regionCode);
            }
        }
        if ("SHIPPING_METHODS".equals(sheetName)) {
            for (int i = 0; i < data.shippingMethods.size(); i++) {
                var sm = data.shippingMethods.get(i);
                validateCountryRegionRow(errors, sheetName, i + 2, sm.countryIso2, sm.regionCode);
            }
        }
    }

    private void validateCountryRegionRow(List<String> errors, String sheetName, int rowNum, String iso2, String regionCode) {
        if (blank(iso2)) return;

        Optional<Country> c = countryRepo.findByIso2CodeIgnoreCase(iso2.trim());
        if (c.isEmpty()) {
            errors.add(sheetName + ".row#" + rowNum + ": countryIso2 not found: " + iso2);
            return;
        }

        if (!blank(regionCode)) {
            Optional<Region> r = regionRepo.findByCountryAndCodeIgnoreCase(c.get(), regionCode.trim());
            if (r.isEmpty()) {
                errors.add(sheetName + ".row#" + rowNum + ": regionCode not found for " + iso2 + ": " + regionCode);
            }
        }
    }

    private static boolean blank(String s) { return s == null || s.trim().isEmpty(); }

    private static <E extends Enum<E>> void checkEnum(
            List<String> errors, String field, String val, Class<E> enumClass, boolean required
    ) {
        if (blank(val)) {
            if (required) errors.add(field + " is required");
            return;
        }
        try {
            Enum.valueOf(enumClass, val.trim().toUpperCase());
        } catch (Exception e) {
            errors.add(field + " invalid value: " + val + " (allowed: " + Arrays.toString(enumClass.getEnumConstants()) + ")");
        }
    }
}
