package com.build4all.shipping.dto;

import com.build4all.order.dto.CartLine;
import com.build4all.order.dto.ShippingAddressDTO;

import java.util.List;

public class ShippingQuoteRequest {

    private Long ownerProjectId;
    private ShippingAddressDTO address;
    private List<CartLine> lines;

    public ShippingQuoteRequest() {
    }

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
}
