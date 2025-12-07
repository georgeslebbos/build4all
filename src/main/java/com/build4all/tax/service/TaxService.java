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
 */
public interface TaxService {

    // ===== CRUD on TaxRule (used by TaxController for OWNER/ADMIN) =====

    /**
     * Create a new TaxRule.
     * The caller must set ownerProject on the entity (or at least its id).
     */
    TaxRule createRule(TaxRule rule);

    /**
     * Update an existing TaxRule.
     */
    TaxRule updateRule(Long id, TaxRule updates);

    /**
     * Delete a TaxRule by id.
     */
    void deleteRule(Long id);

    /**
     * Get a single TaxRule.
     */
    TaxRule getRule(Long id);

    /**
     * List all tax rules for a given ownerProject.
     */
    List<TaxRule> listRulesByOwnerProject(Long ownerProjectId);

    // ===== Calculation methods used by Order / TaxController / Checkout =====

    /**
     * Calculate tax on items in the cart.
     */
    BigDecimal calculateItemTax(Long ownerProjectId,
                                ShippingAddressDTO address,
                                List<CartLine> lines);

    /**
     * Calculate tax on shipping amount.
     */
    BigDecimal calculateShippingTax(Long ownerProjectId,
                                    ShippingAddressDTO address,
                                    BigDecimal shippingAmount);
}
