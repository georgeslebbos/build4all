package com.build4all.shipping.service;

import com.build4all.order.dto.CartLine;
import com.build4all.order.dto.ShippingAddressDTO;
import com.build4all.shipping.dto.ShippingQuote;

import java.util.List;

public interface ShippingService {

    /**
     * Return the chosen shipping quote for a given project, address and cart.
     * Typically used during checkout.
     */
    ShippingQuote getQuote(Long ownerProjectId,
                           ShippingAddressDTO address,
                           List<CartLine> cartLines);

    /**
     * Return all available shipping methods (with their computed prices)
     * for a given project, address and cart.
     * Useful for showing a list of options to the user.
     */
    List<ShippingQuote> getAvailableMethods(Long ownerProjectId,
                                            ShippingAddressDTO address,
                                            List<CartLine> cartLines);
}
