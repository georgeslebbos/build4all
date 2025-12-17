package com.build4all.tax.domain;

/**
 * High-level tax class that you can later use
 * to differentiate rules or product tax behavior.
 *
 * For now it's just an enum; you can attach it to
 * products or to TaxRule if needed.
 *
 * Typical usages (future-friendly):
 * - Product/Item has a TaxClass field (STANDARD/REDUCED/...)
 * - TaxService chooses a different TaxRule rate depending on TaxClass
 * - Some categories may be ZERO-rated while others are STANDARD VAT
 *
 * Notes:
 * - This enum is intentionally simple and stable.
 * - Avoid renaming values after production, because enum names are often stored in DB as strings.
 */
public enum TaxClass {

    /**
     * No tax should be applied at all.
     * Example: internal transfers, donations, or explicitly exempt items.
     */
    NONE,

    /**
     * Default tax class used for most products/services.
     * Example: VAT 11% (your platform default).
     */
    STANDARD,

    /**
     * Reduced-rate items (if the country supports it).
     * Example: food, books, medical items, etc.
     */
    REDUCED,

    /**
     * Zero-rated: tax rate = 0%, but still considered "taxable" for reporting in some systems.
     * (Different from NONE in real tax systems; NONE often means exempt.)
     */
    ZERO
}
