package com.build4all.payment.gateway.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * CreatePaymentCommand is the INPUT that your PaymentOrchestratorService sends
 * to any PaymentGateway plugin (Stripe, Cash, PayPal...).
 *
 * Think of it like a "standard contract" so checkout logic doesn't care
 * which provider is used.
 */
public class CreatePaymentCommand {

    /**
     * Which project/store is making the payment.
     * Important because config is per ownerProjectId (different Stripe keys per project).
     */
    private Long ownerProjectId;

    /**
     * Your internal order id (Build4All order id).
     * Used for linking payment -> order, and for metadata sent to providers.
     */
    private Long orderId;

    /**
     * Amount to charge (in major currency unit: e.g. 49.99).
     * Each gateway decides how to convert to provider format (Stripe uses cents).
     */
    private BigDecimal amount;

    /**
     * Currency code (usually ISO): "usd", "eur", "lbp", ...
     * In your orchestrator you normalized it to lowercase before setting.
     */
    private String currency;

    /**
     * Optional: Used for marketplace flows (Stripe Connect destination charge).
     * If provided, StripeGateway will:
     * - charge customer
     * - transfer funds to destination account
     * - keep application fee for platform
     *
     * Other gateways may ignore this field.
     */
    private String destinationAccountId;

    /**
     * Optional extra metadata you want to store in the provider.
     * Example:
     * - userId
     * - cartId
     * - businessId
     * - anything useful for debugging or webhook reconciliation
     *
     * StripeGateway merges this with orderId/ownerProjectId.
     */
    private Map<String, String> metadata;

    // ---- Getters / Setters ----

    public Long getOwnerProjectId() { return ownerProjectId; }
    public void setOwnerProjectId(Long ownerProjectId) { this.ownerProjectId = ownerProjectId; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getDestinationAccountId() { return destinationAccountId; }
    public void setDestinationAccountId(String destinationAccountId) { this.destinationAccountId = destinationAccountId; }

    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
}
