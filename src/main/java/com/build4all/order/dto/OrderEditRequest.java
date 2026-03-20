package com.build4all.order.dto;

import java.util.List;

public class OrderEditRequest {
    private List<OrderEditLineRequest> lines;
    private ShippingAddressDTO shippingAddress;
	public List<OrderEditLineRequest> getLines() {
		return lines;
	}
	public void setLines(List<OrderEditLineRequest> lines) {
		this.lines = lines;
	}
	public ShippingAddressDTO getShippingAddress() {
		return shippingAddress;
	}
	public void setShippingAddress(ShippingAddressDTO shippingAddress) {
		this.shippingAddress = shippingAddress;
	}
    
    
}