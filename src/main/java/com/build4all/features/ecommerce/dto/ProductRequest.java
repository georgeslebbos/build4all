package com.build4all.features.ecommerce.dto;

import com.build4all.features.ecommerce.domain.ProductType;

import java.math.BigDecimal;
import java.util.List;

public class ProductRequest {

    private Long ownerProjectId;  // aup_id
    private Long itemTypeId;
    private Long currencyId;      // optional; if null use default

    private String name;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private String status;        // Upcoming, Published...

    private String imageUrl;
    private String sku;
    private ProductType productType = ProductType.SIMPLE;

    private boolean virtualProduct;
    private boolean downloadable;
    private String downloadUrl;
    private String externalUrl;
    private String buttonText;

    private BigDecimal salePrice;
    private String saleStart;   // ISO string
    private String saleEnd;

    private List<AttributeValueDTO> attributes; // brand, model, color, etc.

    // ===== Getters & Setters =====

    public Long getOwnerProjectId() {
        return ownerProjectId;
    }

    public Long getItemTypeId() {
        return itemTypeId;
    }

    public Long getCurrencyId() {
        return currencyId;
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

    public String getSaleStart() {
        return saleStart;
    }

    public String getSaleEnd() {
        return saleEnd;
    }

    public List<AttributeValueDTO> getAttributes() {
        return attributes;
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

    public void setSaleStart(String saleStart) {
        this.saleStart = saleStart;
    }

    public void setSaleEnd(String saleEnd) {
        this.saleEnd = saleEnd;
    }

    public void setAttributes(List<AttributeValueDTO> attributes) {
        this.attributes = attributes;
    }
}