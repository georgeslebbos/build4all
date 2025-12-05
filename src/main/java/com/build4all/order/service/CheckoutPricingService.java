package com.build4all.order.service;

import com.build4all.order.dto.CheckoutRequest;
import com.build4all.order.dto.CheckoutSummaryResponse;

public interface CheckoutPricingService {

    CheckoutSummaryResponse priceCheckout(Long ownerProjectId,
                                          Long currencyId,
                                          CheckoutRequest request);
}
