package com.build4all.tax.service;

import com.build4all.order.dto.CartLine;
import com.build4all.order.dto.ShippingAddressDTO;
import com.build4all.tax.domain.TaxRule;

import java.math.BigDecimal;
import java.util.List;

/**
 * Public contract for tax logic:
 * - Managing tax rules
 * - Calculating item tax and shipping tax
 *
 * How this interface is used in your checkout flow:
 * - CheckoutPricingServiceImpl calls:
 *   1) calculateItemTax(...)   -> tax on itemsSubtotal (based on address + rules)
 *   2) calculateShippingTax(...) -> tax on shippingTotal (based on address + appliesToShipping)
 *
 * Notes / conventions (recommended):
 * - TaxRule.rate is a percentage (10.00 = 10%), not a fraction (0.10).
 * - address can be null (in that case you typically use "global" rule only).
 * - lines can include many item types (activities / ecommerce), so tax logic should be generic.
 */
public interface TaxService {

    // ===== CRUD on TaxRule (used by TaxController for OWNER/ADMIN) =====

    /**
     * Create a new TaxRule.
     *
     * Expected behavior:
     * - Validate required fields: ownerProject, name, rate.
     * - Default enabled = true if not provided.
     *
     * Important:
     * The caller must set ownerProject on the entity (or at least its id),
     * because tax rules are per ownerProject (multi-tenant).
     */
    TaxRule createRule(TaxRule rule);

    /**
     * Update an existing TaxRule.
     *
     * Typical behavior:
     * - Load existing rule by id.
     * - Apply only allowed updates (name/rate/enabled/country/region/appliesToShipping).
     * - Save and return the updated rule.
     *
     * Important:
     * - Usually you should not allow changing ownerProject (tenant boundary).
     */
    TaxRule updateRule(Long id, TaxRule updates);

    /**
     * Delete a TaxRule by id.
     *
     * Implementation choices:
     * - Hard delete (repository.deleteById)
     * - OR soft delete (set enabled=false) if you want history/audit.
     */
    void deleteRule(Long id);

    /**
     * Get a single TaxRule by id.
     *
     * Usually throws IllegalArgumentException if not found.
     */
    TaxRule getRule(Long id);

    /**
     * List all tax rules for a given ownerProject.
     *
     * Implementation notes:
     * - Most APIs return only enabled rules for calculations,
     *   but the admin listing may return both enabled/disabled.
     */
    List<TaxRule> listRulesByOwnerProject(Long ownerProjectId);

    // ===== Calculation methods used by Order / CheckoutPricingService / Checkout =====

    /**
     * Calculate tax on items in the cart.
     *
     * Inputs:
     * - ownerProjectId: identifies the tenant/app rules
     * - address: used to select best matching rule (region -> country -> global)
     * - lines: each line contains itemId, quantity, unitPrice, lineSubtotal (already enriched in OrderService)
     *
     * Output:
     * - Total tax amount for all items combined (BigDecimal, typically rounded to currency scale).
     */
    BigDecimal calculateItemTax(Long ownerProjectId,
                                ShippingAddressDTO address,
                                List<CartLine> lines);

    /**
     * Calculate tax on shipping amount.
     *
     * Inputs:
     * - ownerProjectId: identifies the tenant/app rules
     * - address: used to pick the best matching rule
     * - shippingAmount: the shipping cost computed by ShippingService
     *
     * Output:
     * - Shipping tax amount (usually 0 if no rule applies or rule.appliesToShipping=false).
     */
    BigDecimal calculateShippingTax(Long ownerProjectId,
                                    ShippingAddressDTO address,
                                    BigDecimal shippingAmount);
}
