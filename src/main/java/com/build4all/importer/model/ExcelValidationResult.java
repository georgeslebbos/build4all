// File: src/main/java/com/build4all/feeders/importer/ExcelValidationResult.java
package com.build4all.importer.model;

import java.util.ArrayList;
import java.util.List;

public class ExcelValidationResult {
    public boolean valid;
    public List<String> errors = new ArrayList<>();
    public List<String> warnings = new ArrayList<>();

    public int categories;
    public int itemTypes;
    public int products;
    public int taxRules;
    public int shippingMethods;
    public int coupons;

    public static ExcelValidationResult ok() {
        ExcelValidationResult r = new ExcelValidationResult();
        r.valid = true;
        return r;
    }

    public static ExcelValidationResult fail(List<String> errors, List<String> warnings) {
        ExcelValidationResult r = new ExcelValidationResult();
        r.valid = false;
        if (errors != null) r.errors.addAll(errors);
        if (warnings != null) r.warnings.addAll(warnings);
        return r;
    }
}
