package com.build4all.importer.parser.poi;

import com.build4all.importer.dto.SeedDataset;
import com.build4all.importer.parser.ExcelSeedDatasetParser;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Component
public class PoiExcelSeedDatasetParser implements ExcelSeedDatasetParser {

    private static final String SHEET_SETUP = "SETUP";
    private static final String SHEET_CATEGORIES = "CATEGORIES";
    private static final String SHEET_ITEM_TYPES = "ITEM_TYPES";
    private static final String SHEET_PRODUCTS = "PRODUCTS";
    private static final String SHEET_TAX = "TAX_RULES";
    private static final String SHEET_SHIPPING = "SHIPPING_METHODS";
    private static final String SHEET_COUPONS = "COUPONS";

    @Override
    public SeedDataset parse(InputStream in) throws Exception {
        try (Workbook wb = new XSSFWorkbook(in)) {
            SeedDataset data = new SeedDataset();

            parseSetup(wb.getSheet(SHEET_SETUP), data);
            parseCategories(wb.getSheet(SHEET_CATEGORIES), data);
            parseItemTypes(wb.getSheet(SHEET_ITEM_TYPES), data);
            parseProducts(wb.getSheet(SHEET_PRODUCTS), data);
            parseTaxRules(wb.getSheet(SHEET_TAX), data);
            parseShipping(wb.getSheet(SHEET_SHIPPING), data);
            parseCoupons(wb.getSheet(SHEET_COUPONS), data);

            return data;
        }
    }

    // ---------------- SETUP (Key/Value) ----------------
    private void parseSetup(Sheet sheet, SeedDataset data) {
        if (sheet == null) return;

        Map<String, String> kv = new HashMap<>();

        // ✅ Start from 0 (header is optional)
        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String rawK = s(row.getCell(0));
            String rawV = s(row.getCell(1));

            if (blank(rawK)) continue;

            String k = normalizeKey(rawK);

            // ✅ Skip typical header rows: Key | Value
            if (r == 0 && (k.equals("key") || k.equals("parameter") || k.equals("param"))) {
                continue;
            }

            kv.put(k, rawV != null ? rawV.trim() : null);
        }

        // Project
        data.projectName = first(kv, "projectname", "project_name", "project");
        data.projectDescription = first(kv, "projectdescription", "project_description", "description");
        data.projectType = first(kv, "projecttype", "project_type");

        // Owner
        data.owner.username  = first(kv, "owner_username", "ownerusername");
        data.owner.firstName = first(kv, "owner_firstname", "owner_first_name", "ownerfirstname");
        data.owner.lastName  = first(kv, "owner_lastname", "owner_last_name", "ownerlastname");
        data.owner.email     = first(kv, "owner_email", "owneremail", "email");
        data.owner.password  = first(kv, "owner_password", "ownerpassword", "password");
        data.owner.role      = defaultIfBlank(first(kv, "owner_role", "ownerrole", "role"), "OWNER");

        // Tenant
        data.tenant.slug         = first(kv, "tenant_slug", "tenantslug", "tenant");
        data.tenant.appName      = first(kv, "tenant_appname", "tenant_app_name", "tenantappname");
        data.tenant.status       = defaultIfBlank(first(kv, "tenant_status", "tenantstatus", "status"), "ACTIVE");
        data.tenant.currencyCode = first(kv, "tenant_currencycode", "tenant_currency_code", "tenantcurrencycode", "currencycode", "currency_code");
    }

    // ---------------- CATEGORIES ----------------
    private void parseCategories(Sheet sheet, SeedDataset data) {
        if (sheet == null) return;
        HeaderMap hm = HeaderMap.from(sheet);

        for (Row row : hm.dataRows()) {
            String name = hm.getString(row, "name");
            if (blank(name)) continue;

            SeedDataset.CategorySeed c = new SeedDataset.CategorySeed();
            c.name = name;
            c.iconName = hm.getString(row, "iconName");
            c.iconLibrary = hm.getString(row, "iconLibrary");
            c.includeInMenu = hm.getBool(row, "includeInMenu");
            c.weight = hm.getInt(row, "weight");
            data.categories.add(c);
        }
    }

    // ---------------- ITEM_TYPES ----------------
    private void parseItemTypes(Sheet sheet, SeedDataset data) {
        if (sheet == null) return;
        HeaderMap hm = HeaderMap.from(sheet);

        for (Row row : hm.dataRows()) {
            String name = hm.getString(row, "name");
            if (blank(name)) continue;

            SeedDataset.ItemTypeSeed t = new SeedDataset.ItemTypeSeed();
            t.name = name;
            t.categoryName = hm.getString(row, "categoryName");
            t.iconName = hm.getString(row, "iconName");
            t.iconLibrary = hm.getString(row, "iconLibrary");
            t.defaultForCategory = hm.getBool(row, "defaultForCategory");
            data.itemTypes.add(t);
        }
    }

    // ---------------- PRODUCTS ----------------
    private void parseProducts(Sheet sheet, SeedDataset data) {
        if (sheet == null) return;
        HeaderMap hm = HeaderMap.from(sheet);

        for (Row row : hm.dataRows()) {
            String name = hm.getString(row, "name");
            if (blank(name)) continue;

            SeedDataset.ProductSeed p = new SeedDataset.ProductSeed();
            p.name = name;
            p.description = hm.getString(row, "description");
            p.price = hm.getDecimal(row, "price");
            p.status = hm.getString(row, "status");
            p.stock = hm.getInt(row, "stock");
            p.sku = hm.getString(row, "sku");
            p.productType = hm.getString(row, "productType");
            p.virtualProduct = hm.getBool(row, "virtualProduct");
            p.downloadable = hm.getBool(row, "downloadable");
            p.imageUrl = hm.getString(row, "imageUrl");
            p.imageRemoteUrl = hm.getString(row, "imageRemoteUrl");
            p.categoryName = hm.getString(row, "categoryName");
            p.itemTypeName = hm.getString(row, "itemTypeName");
            data.products.add(p);
        }
    }

    // ---------------- TAX RULES ----------------
    private void parseTaxRules(Sheet sheet, SeedDataset data) {
        if (sheet == null) return;
        HeaderMap hm = HeaderMap.from(sheet);

        for (Row row : hm.dataRows()) {
            String name = hm.getString(row, "name");
            if (blank(name)) continue;

            SeedDataset.TaxRuleSeed t = new SeedDataset.TaxRuleSeed();
            t.name = name;
            t.rate = hm.getDecimal(row, "rate");
            t.appliesToShipping = hm.getBool(row, "appliesToShipping");
            t.enabled = hm.getBool(row, "enabled");
            t.countryIso2 = hm.getString(row, "countryIso2");
            t.countryIso3 = hm.getString(row, "countryIso3");
            t.regionCode = hm.getString(row, "regionCode");
            data.taxRules.add(t);
        }
    }

    // ---------------- SHIPPING ----------------
    private void parseShipping(Sheet sheet, SeedDataset data) {
        if (sheet == null) return;
        HeaderMap hm = HeaderMap.from(sheet);

        for (Row row : hm.dataRows()) {
            String name = hm.getString(row, "name");
            if (blank(name)) continue;

            SeedDataset.ShippingMethodSeed s = new SeedDataset.ShippingMethodSeed();
            s.name = name;
            s.description = hm.getString(row, "description");
            s.type = hm.getString(row, "type");
            s.flatRate = hm.getDecimal(row, "flatRate");
            s.pricePerKg = hm.getDecimal(row, "pricePerKg");
            s.freeShippingThreshold = hm.getDecimal(row, "freeShippingThreshold");
            s.enabled = hm.getBool(row, "enabled");
            s.countryIso2 = hm.getString(row, "countryIso2");
            s.countryIso3 = hm.getString(row, "countryIso3");
            s.regionCode = hm.getString(row, "regionCode");
            data.shippingMethods.add(s);
        }
    }

    // ---------------- COUPONS ----------------
    private void parseCoupons(Sheet sheet, SeedDataset data) {
        if (sheet == null) return;
        HeaderMap hm = HeaderMap.from(sheet);

        for (Row row : hm.dataRows()) {
            String code = hm.getString(row, "code");
            if (blank(code)) continue;

            SeedDataset.CouponSeed c = new SeedDataset.CouponSeed();
            c.code = code;
            c.description = hm.getString(row, "description");
            c.type = hm.getString(row, "type");
            c.value = hm.getDecimal(row, "value");
            c.globalUsageLimit = hm.getInt(row, "globalUsageLimit");
            c.maxDiscountAmount = hm.getDecimal(row, "maxDiscountAmount");
            c.minOrderAmount = hm.getDecimal(row, "minOrderAmount");
            c.validFrom = hm.getDateTime(row, "validFrom");
            c.validTo = hm.getDateTime(row, "validTo");
            c.active = hm.getBool(row, "active");
            data.coupons.add(c);
        }
    }

    // ---------------- helpers ----------------

    private static String first(Map<String, String> kv, String... keys) {
        for (String k : keys) {
            String v = kv.get(normalizeKey(k));
            if (!blank(v)) return v;
        }
        return null;
    }

    private static String defaultIfBlank(String v, String def) {
        return blank(v) ? def : v;
    }

    private static String normalizeKey(String raw) {
        if (raw == null) return "";
        String k = raw.trim().toLowerCase();
        k = k.replace(".", "_").replace("-", "_").replace(" ", "_");
        while (k.contains("__")) k = k.replace("__", "_");
        return k;
    }

    private static String s(Cell c) {
        if (c == null) return null;

        return switch (c.getCellType()) {
            case STRING -> c.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(c)
                    ? c.getDateCellValue().toString()
                    : String.valueOf(c.getNumericCellValue());
            case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
            case FORMULA -> {
                try {
                    FormulaEvaluator eval = c.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
                    CellValue cv = eval.evaluate(c);
                    yield cv != null ? cv.formatAsString() : c.getCellFormula();
                } catch (Exception e) {
                    yield c.getCellFormula();
                }
            }
            default -> null;
        };
    }

    private static boolean blank(String x) {
        return x == null || x.trim().isEmpty();
    }

    // ------------ Header map utility ------------
    static class HeaderMap {
        private final Sheet sheet;
        private final Map<String, Integer> idx = new HashMap<>();

        private HeaderMap(Sheet sheet) {
            this.sheet = sheet;
            Row h = sheet.getRow(0);
            if (h == null) return;
            for (Cell c : h) {
                String k = s(c);
                if (blank(k)) continue;
                idx.put(k.trim(), c.getColumnIndex());
            }
        }

        static HeaderMap from(Sheet sheet) {
            return new HeaderMap(sheet);
        }

        List<Row> dataRows() {
            List<Row> rows = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row != null) rows.add(row);
            }
            return rows;
        }

        String getString(Row row, String col) {
            Integer i = idx.get(col);
            if (i == null) return null;
            return s(row.getCell(i));
        }

        Integer getInt(Row row, String col) {
            String v = getString(row, col);
            if (blank(v)) return null;
            try {
                Double d = Double.parseDouble(v.trim());
                return d.intValue();
            } catch (Exception e) {
                try { return Integer.parseInt(v.trim()); } catch (Exception ex) { return null; }
            }
        }

        Boolean getBool(Row row, String col) {
            String v = getString(row, col);
            if (blank(v)) return null;
            v = v.trim().toLowerCase();
            return v.equals("true") || v.equals("yes") || v.equals("1");
        }

        BigDecimal getDecimal(Row row, String col) {
            String v = getString(row, col);
            if (blank(v)) return null;
            try { return new BigDecimal(v.trim()); } catch (Exception e) { return null; }
        }

        LocalDateTime getDateTime(Row row, String col) {
            Integer i = idx.get(col);
            if (i == null) return null;
            Cell c = row.getCell(i);
            if (c == null) return null;

            if (c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
                return c.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            }

            String s = PoiExcelSeedDatasetParser.s(c);
            if (blank(s)) return null;

            try { return LocalDateTime.parse(s.trim()); } catch (Exception e) { return null; }
        }
    }
}
