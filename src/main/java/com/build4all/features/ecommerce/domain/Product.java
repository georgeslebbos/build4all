package com.build4all.features.ecommerce.domain;

import com.build4all.catalog.domain.Item;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@PrimaryKeyJoinColumn(name = "item_id")  // FK to items.item_id
public class Product extends Item {

    // Business code (NOT the DB @Id)
    @Column(name = "sku", unique = true)
    private String sku;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false)
    private ProductType productType = ProductType.SIMPLE;

    @Column(name = "is_virtual")
    private boolean virtualProduct;   // no shipping (services, subscriptions)

    @Column(name = "is_downloadable")
    private boolean downloadable;     // digital goods

    @Column(name = "download_url")
    private String downloadUrl;

    @Column(name = "external_url")
    private String externalUrl;       // affiliate link

    @Column(name = "button_text")
    private String buttonText;        // optional: "Buy now", "Book now"

    @Column(name = "sale_price")
    private BigDecimal salePrice;

    @Column(name = "sale_start")
    private LocalDateTime saleStart;

    @Column(name = "sale_end")
    private LocalDateTime saleEnd;

    // ===== Getters & Setters =====

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public ProductType getProductType() { return productType; }
    public void setProductType(ProductType productType) { this.productType = productType; }

    public boolean isVirtualProduct() { return virtualProduct; }
    public void setVirtualProduct(boolean virtualProduct) { this.virtualProduct = virtualProduct; }

    public boolean isDownloadable() { return downloadable; }
    public void setDownloadable(boolean downloadable) { this.downloadable = downloadable; }

    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }

    public String getExternalUrl() { return externalUrl; }
    public void setExternalUrl(String externalUrl) { this.externalUrl = externalUrl; }

    public String getButtonText() { return buttonText; }
    public void setButtonText(String buttonText) { this.buttonText = buttonText; }

    public BigDecimal getSalePrice() { return salePrice; }
    public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice; }

    public LocalDateTime getSaleStart() { return saleStart; }
    public void setSaleStart(LocalDateTime saleStart) { this.saleStart = saleStart; }

    public LocalDateTime getSaleEnd() { return saleEnd; }
    public void setSaleEnd(LocalDateTime saleEnd) { this.saleEnd = saleEnd; }
}