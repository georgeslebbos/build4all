package com.build4all.payment.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.EphemeralKey;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.EphemeralKeyCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class StripeService {

	public PaymentIntent createPaymentIntentWithCommission(double price, String currency, String stripeAccountId) throws StripeException {
	    long amount = (long) (price * 100);
	    long fee = (long) (amount * 0.10);

	    PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
	        .setAmount(amount)
	        .setCurrency(currency) // 👈 dynamic currency (e.g. "usd", "eur")
	        .setApplicationFeeAmount(fee)
	        .setTransferData(
	            PaymentIntentCreateParams.TransferData.builder()
	                .setDestination(stripeAccountId)
	                .build()
	        )
	        .build();

	    return PaymentIntent.create(params);
	}
	
	
	public Refund refundPayment(String paymentIntentId) throws StripeException {
	    RefundCreateParams params = RefundCreateParams.builder()
	        .setPaymentIntent(paymentIntentId)
	        .setReverseTransfer(true) // 🔁 Takes back the 90% from the business
	        .setRefundApplicationFee(false) // ❌ Keeps the 10% fee with your platform
	        .build();

	    return Refund.create(params);
	}




 
}
