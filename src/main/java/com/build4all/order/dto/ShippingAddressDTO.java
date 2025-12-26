package com.build4all.order.dto;

/**
 * ShippingAddressDTO
 *
 * Purpose:
 * - Carries the shipping/delivery address (and chosen shipping method) from the client (Flutter)
 *   to the backend during checkout.
 *
 * Used by:
 * - POST /api/orders/checkout  (inside CheckoutRequest)
 *
 * Typical flow:
 * 1) Client selects address (country/region/city/postalCode)
 * 2) Client selects a shipping method (shippingMethodId) from shipping quotes
 * 3) Client calls /checkout with ShippingAddressDTO
 * 4) Backend uses this data for:
 *    - Shipping cost calculation (ShippingService)
 *    - Tax calculation (TaxService)
 *    - Persisting shipping details onto Order header (Order entity fields)
 */
public class ShippingAddressDTO {

    /* =========================================================
       ADDRESS FIELDS (where to deliver)
       ========================================================= */

    /**
     * Country id (FK reference to catalog.country).
     * - Used to compute shipping availability/cost and taxes.
     * - Saved on Order.shippingCountry (ManyToOne) if provided.
     */
    private Long countryId;

    /**
     * Region id (FK reference to catalog.region).
     * - Used for more specific shipping/tax rules.
     * - Saved on Order.shippingRegion (ManyToOne) if provided.
     */
    private Long regionId;

    /**
     * City free text (e.g., "Beirut", "Riyadh").
     * - Saved on Order.shippingCity.
     */
    private String city;

    /**
     * Postal/ZIP code (optional depending on country).
     * - Saved on Order.shippingPostalCode.
     */
    private String postalCode;

    /* =========================================================
       SHIPPING METHOD (what delivery option was chosen)
       ========================================================= */

    /**
     * Selected shipping method id (nullable).
     * - Comes from your ShippingService / ShippingMethod table (depends on your design).
     * - If null => either no shipping needed (digital/service) or shipping not selected yet.
     *
     * Backend usage:
     * - ShippingService.getQuote(ownerProjectId, address, lines) uses it to price shipping.
     * - Persisted on Order.shippingMethodId so the order can be fulfilled later.
     */
    private Long shippingMethodId;

    /**
     * Display name of the selected shipping method (nullable).
     * - Example: "Standard Delivery", "Express", "Pickup".
     * - Mainly for convenience so you can display it in invoices/admin screens without extra joins.
     * - Persisted on Order.shippingMethodName.
     *
     * Note:
     * - Some teams prefer not trusting client-provided names, and instead derive the name from DB.
     * - If you do trust it, keep it; otherwise overwrite it server-side after lookup.
     */
    private String shippingMethodName;

    /**
     * Full address line (street/building/apartment, etc.)
     * - Saved on Order.shippingAddress
     */
    private String addressLine;

    /**
     * Delivery contact phone
     * - Saved on Order.shippingPhone
     */
    private String phone;

    /** No-arg constructor (needed by Jackson) */
    public ShippingAddressDTO() { }

    // ===== Getters & Setters =====

    public Long getCountryId() { return countryId; }
    public void setCountryId(Long countryId) { this.countryId = countryId; }

    public Long getRegionId() { return regionId; }
    public void setRegionId(Long regionId) { this.regionId = regionId; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    public Long getShippingMethodId() { return shippingMethodId; }
    public void setShippingMethodId(Long shippingMethodId) { this.shippingMethodId = shippingMethodId; }

    public String getShippingMethodName() { return shippingMethodName; }
    public void setShippingMethodName(String shippingMethodName) { this.shippingMethodName = shippingMethodName; }

    public String getAddressLine() { return addressLine; }
    public void setAddressLine(String addressLine) { this.addressLine = addressLine; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
