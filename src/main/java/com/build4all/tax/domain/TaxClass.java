package com.build4all.tax.domain;

public enum TaxClass {
    NONE,       // no tax
    STANDARD,   // e.g. VAT 11%
    REDUCED,    // e.g. food, books
    ZERO        // zero-rated
}
