package com.build4all.features.ecommerce.dto;

import com.build4all.tax.domain.TaxClass;
import com.build4all.features.ecommerce.domain.ProductType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class ProductResponse {

    private Long id;
    private Long ownerProjectId;
    private Long itemTypeId;
    private Long currencyId;
    private Long categoryId; // from ItemType.category

    private String name;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private String status;
    private String imageUrl;

    private String sku;
    private ProductType productType;

    private boolean virtualProduct;
    private boolean downloadable;
    private String downloadUrl;
    private String externalUrl;
    private String buttonText;

    private BigDecimal salePrice;
    private LocalDateTime saleStart;
    private LocalDateTime saleEnd;

    // 🔥 discount info
    private BigDecimal effectivePrice; // price used in cart / display
    private boolean onSale;            // true if sale active now

    private List<AttributeValueDTO> attributes;

    // --- TAX ---
    private boolean taxable;
    private TaxClass taxClass;

    // --- SHIPPING ---
    private BigDecimal weightKg;
    private BigDecimal widthCm;
    private BigDecimal heightCm;
    private BigDecimal lengthCm;

    // ===== Getters & Setters =====

    public Long getId() {
        return id;
    }

    public Long getOwnerProjectId() {
        return ownerProjectId;
    }

    public Long getItemTypeId() {
        return itemTypeId;
    }

    public Long getCurrencyId() {
        return currencyId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getStock() {
        return stock;
    }

    public String getStatus() {
        return status;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getSku() {
        return sku;
    }

    public ProductType getProductType() {
        return productType;
    }

    public BigDecimal getEffectivePrice() {
        return effectivePrice;
    }

    public boolean isOnSale() {
        return onSale;
    }

    public boolean isVirtualProduct() {
        return virtualProduct;
    }

    public boolean isDownloadable() {
        return downloadable;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    public String getButtonText() {
        return buttonText;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }

    public LocalDateTime getSaleStart() {
        return saleStart;
    }

    public LocalDateTime getSaleEnd() {
        return saleEnd;
    }

    public List<AttributeValueDTO> getAttributes() {
        return attributes;
    }

    public boolean isTaxable() { return taxable; }
    public TaxClass getTaxClass() { return taxClass; }

    public BigDecimal getWeightKg() { return weightKg; }
    public BigDecimal getWidthCm() { return widthCm; }
    public BigDecimal getHeightCm() { return heightCm; }
    public BigDecimal getLengthCm() { return lengthCm; }

    public void setId(Long id) {
        this.id = id;
    }

    public void setOwnerProjectId(Long ownerProjectId) {
        this.ownerProjectId = ownerProjectId;
    }

    public void setItemTypeId(Long itemTypeId) {
        this.itemTypeId = itemTypeId;
    }

    public void setCurrencyId(Long currencyId) {
        this.currencyId = currencyId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public void setProductType(ProductType productType) {
        this.productType = productType;
    }

    public void setVirtualProduct(boolean virtualProduct) {
        this.virtualProduct = virtualProduct;
    }

    public void setDownloadable(boolean downloadable) {
        this.downloadable = downloadable;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public void setExternalUrl(String externalUrl) {
        this.externalUrl = externalUrl;
    }

    public void setButtonText(String buttonText) {
        this.buttonText = buttonText;
    }

    public void setSalePrice(BigDecimal salePrice) {
        this.salePrice = salePrice;
    }

    public void setSaleStart(LocalDateTime saleStart) {
        this.saleStart = saleStart;
    }

    public void setSaleEnd(LocalDateTime saleEnd) {
        this.saleEnd = saleEnd;
    }

    public void setAttributes(List<AttributeValueDTO> attributes) {
        this.attributes = attributes;
    }

    public void setEffectivePrice(BigDecimal effectivePrice) {
        this.effectivePrice = effectivePrice;
    }

    public void setOnSale(boolean onSale) {
        this.onSale = onSale;
    }

    public void setTaxable(boolean taxable) { this.taxable = taxable; }
    public void setTaxClass(TaxClass taxClass) { this.taxClass = taxClass; }

    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }
    public void setWidthCm(BigDecimal widthCm) { this.widthCm = widthCm; }
    public void setHeightCm(BigDecimal heightCm) { this.heightCm = heightCm; }
    public void setLengthCm(BigDecimal lengthCm) { this.lengthCm = lengthCm; }
}