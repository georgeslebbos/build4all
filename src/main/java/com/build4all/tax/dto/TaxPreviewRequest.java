package com.build4all.tax.dto;

import com.build4all.order.dto.CartLine;
import com.build4all.order.dto.ShippingAddressDTO;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO used by the /api/tax/preview endpoint to simulate
 * how tax would be calculated for a given cart + address.
 */
public class TaxPreviewRequest {

    private Long ownerProjectId;
    private ShippingAddressDTO address;
    private List<CartLine> lines;
    private BigDecimal shippingTotal;

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
