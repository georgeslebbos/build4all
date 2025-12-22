package com.build4all.payment.service;

import com.build4all.payment.domain.PaymentTransaction;
import com.build4all.payment.repository.PaymentTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;

@Service
public class OrderPaymentWriteService {

    private final PaymentTransactionRepository txRepo;

    public OrderPaymentWriteService(PaymentTransactionRepository txRepo) {
        this.txRepo = txRepo;
    }

    @Transactional
    public PaymentTransaction recordManualPaid(Long ownerProjectId,
                                               Long orderId,
                                               BigDecimal amount,
                                               String currencyCode,
                                               String providerCode,
                                               String providerPaymentId) {

        if (ownerProjectId == null) throw new IllegalArgumentException("ownerProjectId required");
        if (orderId == null) throw new IllegalArgumentException("orderId required");
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("amount must be > 0");
        if (currencyCode == null || currencyCode.isBlank()) currencyCode = "usd";
        if (providerCode == null || providerCode.isBlank()) providerCode = "CASH";
        if (providerPaymentId == null || providerPaymentId.isBlank()) providerPaymentId = "MANUAL_ORDER_" + orderId;

        PaymentTransaction tx = new PaymentTransaction();
        tx.setOwnerProjectId(ownerProjectId);
        tx.setOrderId(orderId);
        tx.setProviderCode(providerCode.trim().toUpperCase(Locale.ROOT));
        tx.setProviderPaymentId(providerPaymentId.trim());
        tx.setAmount(amount);
        tx.setCurrency(currencyCode.trim().toLowerCase(Locale.ROOT));
        tx.setStatus("PAID"); // ✅ THIS is what your read service sums
        return txRepo.save(tx);
    }
}
