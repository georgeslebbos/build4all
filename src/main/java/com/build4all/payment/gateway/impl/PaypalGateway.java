package com.build4all.payment.gateway.impl;

import com.build4all.payment.gateway.PaymentGateway;
import com.build4all.payment.gateway.dto.CreatePaymentCommand;
import com.build4all.payment.gateway.dto.CreatePaymentResult;
import com.build4all.payment.gateway.dto.GatewayConfig;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class PaypalGateway implements PaymentGateway {

    private final RestTemplate restTemplate;

    public PaypalGateway(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override public String code() { return "PAYPAL"; }
    @Override public String displayName() { return "PayPal"; }

    @Override
    public Map<String, Object> configSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("title", "PayPal Settings");
        schema.put("fields", new Object[]{
                Map.of("key","clientId", "label","Client ID", "type","text", "required", true),
                Map.of("key","clientSecret", "label","Client Secret", "type","password", "required", true),
                Map.of(
                        "key", "mode",
                        "label", "Mode",
                        "type", "select",
                        "options", new String[]{"SANDBOX","LIVE"},
                        "default", "SANDBOX",
                        "required", true
                ),
                // ✅ Needed for redirect flow
                Map.of("key","returnUrl", "label","Return URL", "type","text", "required", true),
                Map.of("key","cancelUrl", "label","Cancel URL", "type","text", "required", true),
                Map.of("key","brandName", "label","Brand Name", "type","text", "required", false)
        });
        return schema;
    }

    @Override
    public Map<String, Object> publicCheckoutConfig(GatewayConfig config) {
        // Redirect flow does not require clientId on mobile, but it is safe if you use PayPal SDK later.
        String clientId = config.getString("clientId");
        String mode = Optional.ofNullable(config.getString("mode")).orElse("SANDBOX");
        return clientId == null ? Map.of("mode", mode) : Map.of("clientId", clientId, "mode", mode);
    }

    @Override
    public CreatePaymentResult createPayment(CreatePaymentCommand cmd, GatewayConfig config) {

        String clientId = must(config.getString("clientId"), "PayPal clientId missing");
        String clientSecret = must(config.getString("clientSecret"), "PayPal clientSecret missing");
        String mode = Optional.ofNullable(config.getString("mode")).orElse("SANDBOX");

        String returnUrl = must(config.getString("returnUrl"), "PayPal returnUrl missing");
        String cancelUrl = must(config.getString("cancelUrl"), "PayPal cancelUrl missing");
        String brandName = Optional.ofNullable(config.getString("brandName")).orElse("Build4All");

        String baseUrl = isLive(mode)
                ? "https://api-m.paypal.com"
                : "https://api-m.sandbox.paypal.com";

        // 1) OAuth token
        String accessToken = getAccessToken(baseUrl, clientId, clientSecret);

        // 2) Create order
        BigDecimal amount = cmd.getAmount() == null ? BigDecimal.ZERO : cmd.getAmount();
        String currency = (cmd.getCurrency() == null ? "USD" : cmd.getCurrency()).toUpperCase(Locale.ROOT);
        String value = amount.setScale(2, RoundingMode.HALF_UP).toPlainString();

        Map<String, Object> purchaseUnit = new LinkedHashMap<>();
        purchaseUnit.put("amount", Map.of("currency_code", currency, "value", value));
        purchaseUnit.put("custom_id", String.valueOf(cmd.getOrderId()));
        purchaseUnit.put("description", "Order #" + cmd.getOrderId());

        Map<String, Object> appContext = new LinkedHashMap<>();
        appContext.put("brand_name", brandName);
        appContext.put("return_url", returnUrl);
        appContext.put("cancel_url", cancelUrl);
        appContext.put("user_action", "PAY_NOW");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("intent", "CAPTURE");
        body.put("purchase_units", List.of(purchaseUnit));
        body.put("application_context", appContext);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> res = restTemplate.exchange(
                baseUrl + "/v2/checkout/orders",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
        );

        if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
            throw new IllegalStateException("PayPal create order failed: " + res.getStatusCode());
        }

        Map<String, Object> resp = res.getBody();
        String paypalOrderId = asString(resp.get("id"));
        String status = asString(resp.get("status"));
        String approvalUrl = extractApprovalUrl(resp);

        if (paypalOrderId == null || paypalOrderId.isBlank()) throw new IllegalStateException("PayPal response missing order id");
        if (approvalUrl == null || approvalUrl.isBlank()) throw new IllegalStateException("PayPal response missing approval link");

        return CreatePaymentResult.paypal(paypalOrderId, approvalUrl, status == null ? "CREATED" : status);
    }

    // -------- helpers --------

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

        String token = asString(res.getBody().get("access_token"));
        if (token == null || token.isBlank()) throw new IllegalStateException("PayPal token response missing access_token");
        return token;
    }

    private String extractApprovalUrl(Map<String, Object> resp) {
        Object linksObj = resp.get("links");
        if (!(linksObj instanceof List<?> links)) return null;

        for (Object o : links) {
            if (!(o instanceof Map<?, ?> m)) continue;
            String rel = asString(m.get("rel"));
            String href = asString(m.get("href"));
            if ("approve".equalsIgnoreCase(rel) || "payer-action".equalsIgnoreCase(rel)) return href;
        }
        return null;
    }

    private boolean isLive(String mode) {
        return "LIVE".equalsIgnoreCase(mode) || "PROD".equalsIgnoreCase(mode) || "PRODUCTION".equalsIgnoreCase(mode);
    }

    private String asString(Object o) { return o == null ? null : o.toString(); }

    private String must(String v, String err) {
        if (v == null || v.isBlank()) throw new IllegalStateException(err);
        return v;
    }
}
