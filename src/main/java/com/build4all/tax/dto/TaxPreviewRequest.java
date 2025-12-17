package com.build4all.tax.dto;

import com.build4all.order.dto.CartLine;
import com.build4all.order.dto.ShippingAddressDTO;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO used by the /api/tax/preview endpoint to simulate
 * how tax would be calculated for a given cart + address.
 *
 * Why this DTO exists:
 * - Frontend (or Postman) can "preview" taxes before placing an order.
 * - It does NOT create an Order; it only returns calculated values.
 * - Helps UI show: item tax, shipping tax, totals, etc.
 *
 * Typical flow:
 * 1) Client sends ownerProjectId + address + cart lines + shippingTotal
 * 2) Backend runs TaxService.calculateItemTax(...) and calculateShippingTax(...)
 * 3) Backend returns the breakdown (e.g., itemTaxTotal, shippingTaxTotal)
 */
public class TaxPreviewRequest {

    /**
     * Tenant/app identifier (AdminUserProject.id).
     * Needed because tax rules are configured per ownerProject.
     */
    private Long ownerProjectId;

    /**
     * Optional shipping address used to select the best matching TaxRule
     * (by country/region filters if they exist).
     *
     * If address is null or country/region are null, tax selection may fall back
     * to a "default" rule (if you have one configured).
     */
    private ShippingAddressDTO address;

    /**
     * Cart lines used to compute item subtotal and item-level tax.
     * Each line typically includes:
     * - itemId
     * - quantity
     * - unitPrice (important for preview)
     */
    private List<CartLine> lines;

    /**
     * Shipping amount that the client wants to preview tax on.
     * - If you calculate shipping on the backend, pass the result here.
     * - If shippingTotal is 0 or null, shipping tax usually becomes 0.
     */
    private BigDecimal shippingTotal;

    // -------------------- getters & setters --------------------

    public Long getOwnerProjectId() {
        return ownerProjectId;
    }

    public void setOwnerProjectId(Long ownerProjectId) {
        this.ownerProjectId = ownerProjectId;
    }

    public ShippingAddressDTO getAddress() {
        return address;
    }

    public void setAddress(ShippingAddressDTO address) {
        this.address = address;
    }

    public List<CartLine> getLines() {
        return lines;
    }

    public void setLines(List<CartLine> lines) {
        this.lines = lines;
    }

    public BigDecimal getShippingTotal() {
        return shippingTotal;
    }

    public void setShippingTotal(BigDecimal shippingTotal) {
        this.shippingTotal = shippingTotal;
    }
}
