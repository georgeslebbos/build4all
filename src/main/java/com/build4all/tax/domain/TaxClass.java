package com.build4all.tax.domain;

/**
 * High-level tax class that you can later use
 * to differentiate rules or product tax behavior.
 *
 * For now it's just an enum; you can attach it to
 * products or to TaxRule if needed.
 */
public enum TaxClass {
    NONE,       // no tax
    STANDARD,   // e.g. VAT 11%
    REDUCED,    // e.g. food, books
    ZERO        // zero-rated
}
