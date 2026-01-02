// File: src/main/java/com/build4all/feeders/importer/ExcelImportResult.java
package com.build4all.importer.model;

import java.util.ArrayList;
import java.util.List;

public class ExcelImportResult {
    public boolean success;
    public String message;

    public long projectId;
    public long ownerProjectId;
    public String slug;

    public int insertedCategories;
    public int insertedItemTypes;
    public int insertedProducts;
    public int insertedTaxRules;
    public int insertedShippingMethods;
    public int insertedCoupons;

    public List<String> errors = new ArrayList<>();
    public List<String> warnings = new ArrayList<>();

    public static ExcelImportResult ok(String msg) {
        ExcelImportResult r = new ExcelImportResult();
        r.success = true;
        r.message = msg;
        return r;
    }

    public static ExcelImportResult fail(String msg, List<String> errors) {
        ExcelImportResult r = new ExcelImportResult();
        r.success = false;
        r.message = msg;
        if (errors != null) r.errors.addAll(errors);
        return r;
    }
}
