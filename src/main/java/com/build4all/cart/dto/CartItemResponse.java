package com.build4all.cart.dto;

import java.math.BigDecimal;

/**
 * One line item returned inside CartResponse.items.
 *
 * Purpose:
 * - UI-friendly representation of a CartItem (DB entity) + basic Item info (name/image)
 * - Keeps the frontend simple (no need to traverse nested JPA entities)
 *
 * How it's filled (see CartServiceImpl.toResponse):
 * - cartItemId     <- CartItem.id
 * - itemId         <- CartItem.item.id
 * - itemName       <- resolved via reflection helper: getName/getItemName/getTitle
 * - imageUrl       <- resolved via reflection helper: getImageUrl/getImage/getPhotoUrl/getThumbnailUrl
 * - quantity       <- CartItem.quantity
 * - unitPrice      <- CartItem.unitPrice (captured when added to cart) or 0 if null
 * - lineTotal      <- unitPrice * quantity
 *
 * Notes:
 * - unitPrice is the "price at time of adding to cart" in your current design.
 *   If product price changes later, cart still preserves the previous unitPrice (unless you refresh it).
 * - lineTotal is calculated on the backend for convenience, but frontend can also recompute it.
 */
public class CartItemResponse {

    /** ID of the cart_items row (used for update/remove operations). */
    private Long cartItemId;

    /** ID of the referenced item/product/activity. */
    private Long itemId;

    /** Display name of the item (best-effort getter resolution). */
    private String itemName;

    /** Display image URL for the item (best-effort getter resolution). */
    private String imageUrl;

    /** Quantity chosen by the user. */
    private int quantity;

    /** Unit price at the time it was added to cart (or last updated). */
    private BigDecimal unitPrice;

    /** Computed: unitPrice * quantity. */
    private BigDecimal lineTotal;

 // CartItemResponse.java
    private Integer availableStock;       // null => stock not tracked (service/digital)
    private boolean outOfStock;           // stock == 0
    private boolean quantityExceedsStock; // qty > stock
    private Integer maxAllowedQuantity;   // equals stock when tracked
    private boolean disabled;             // true if cannot be checked out
    private String blockingReason;        // "Out of stock" / "Only X left" / "Removed"

    // getters & setters

    public Long getCartItemId() { return cartItemId; }
    public void setCartItemId(Long cartItemId) { this.cartItemId = cartItemId; }

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
	public Integer getAvailableStock() {
		return availableStock;
	}
	public void setAvailableStock(Integer availableStock) {
		this.availableStock = availableStock;
	}
	public boolean isOutOfStock() {
		return outOfStock;
	}
	public void setOutOfStock(boolean outOfStock) {
		this.outOfStock = outOfStock;
	}
	public boolean isQuantityExceedsStock() {
		return quantityExceedsStock;
	}
	public void setQuantityExceedsStock(boolean quantityExceedsStock) {
		this.quantityExceedsStock = quantityExceedsStock;
	}
	public Integer getMaxAllowedQuantity() {
		return maxAllowedQuantity;
	}
	public void setMaxAllowedQuantity(Integer maxAllowedQuantity) {
		this.maxAllowedQuantity = maxAllowedQuantity;
	}
	public boolean isDisabled() {
		return disabled;
	}
	public void setDisabled(boolean disabled) {
		this.disabled = disabled;
	}
	public String getBlockingReason() {
		return blockingReason;
	}
	public void setBlockingReason(String blockingReason) {
		this.blockingReason = blockingReason;
	}
    
    
}
