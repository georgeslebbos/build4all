package com.build4all.tax.service;

import com.build4all.order.dto.CartLine;
import com.build4all.order.dto.ShippingAddressDTO;

import java.math.BigDecimal;
import java.util.List;

public interface TaxService {

    BigDecimal calculateItemTax(Long ownerProjectId,
                                ShippingAddressDTO address,
                                List<CartLine> items);

    BigDecimal calculateShippingTax(Long ownerProjectId,
                                    ShippingAddressDTO address,
                                    BigDecimal shippingTotal);
}
