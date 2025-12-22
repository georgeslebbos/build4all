package com.build4all.payment.service;

import com.build4all.payment.domain.PaymentMethodConfig;
import com.build4all.payment.domain.PaymentTransaction;
import com.build4all.payment.gateway.dto.GatewayConfig;
import com.build4all.payment.repository.PaymentTransactionRepository;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

@Service
public class PaypalCaptureService {

    private final RestTemplate restTemplate;
    private final PaymentTransactionRepository txRepo;
    private final PaymentConfigService configService;

    public PaypalCaptureService(RestTemplate restTemplate,
                                PaymentTransactionRepository txRepo,
                                PaymentConfigService configService) {
        this.restTemplate = restTemplate;
        this.txRepo = txRepo;
        this.configService = configService;
    }

    @Transactional
    public PaymentTransaction capture(Long paypalOrderIdAsLongNotUsed, String paypalOrderId) {

        PaymentTransaction tx = txRepo
                .findFirstByProviderCodeIgnoreCaseAndProviderPaymentId("PAYPAL", paypalOrderId)
                .orElseThrow(() -> new IllegalArgumentException("PaymentTransaction not found for PayPal orderId: " + paypalOrderId));

        // Load PAYPAL config for the same ownerProject
        PaymentMethodConfig cfgEntity = configService.requireEnabled(tx.getOwnerProjectId(), "PAYPAL");
        GatewayConfig cfg = configService.parse(cfgEntity.getConfigJson());

        String clientId = must(cfg.getString("clientId"), "PayPal clientId missing");
        String clientSecret = must(cfg.getString("clientSecret"), "PayPal clientSecret missing");
        String mode = cfg.getString("mode");
        String baseUrl = ("LIVE".equalsIgnoreCase(mode) ? "https://api-m.paypal.com" : "https://api-m.sandbox.paypal.com");

        String accessToken = getAccessToken(baseUrl, clientId, clientSecret);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> res = restTemplate.exchange(
                baseUrl + "/v2/checkout/orders/" + paypalOrderId + "/capture",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(), headers),
                Map.class
        );

        // Update transaction status
        if (res.getStatusCode().is2xxSuccessful()) {
            tx.setStatus("PAID");
        } else {
            tx.setStatus("FAILED");
        }

        // Optional: store raw payload for audit
        tx.setRawProviderPayload(String.valueOf(res.getBody()));
        return txRepo.save(tx);
    }

    private String getAccessToken(String baseUrl, String clientId, String clientSecret) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String basic = Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8)
        );
        headers.set("Authorization", "Basic " + basic);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");

        ResponseEntity<Map> res = restTemplate.exchange(
                baseUrl + "/v1/oauth2/token",
                HttpMethod.POST,
                new HttpEntity<>(form, headers),
                Map.class
        );

        if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
            throw new IllegalStateException("PayPal token failed: " + res.getStatusCode());
        }

        Object token = res.getBody().get("access_token");
        String t = token == null ? null : token.toString();
        if (t == null || t.isBlank()) throw new IllegalStateException("PayPal token response missing access_token");
        return t;
    }

    private String must(String v, String err) {
        if (v == null || v.isBlank()) throw new IllegalStateException(err);
        return v.trim();
    }
}
