package com.build4all.ai.dto;

import com.build4all.catalog.domain.Currency;
import com.build4all.catalog.domain.Item;
import com.build4all.features.activity.domain.Activity;
import com.build4all.features.ecommerce.domain.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AiItemContextDTO {

    // ---------- Item (base) ----------
    public Long id;
    public Long aupId;

    public String name;
    public String description;
    public String itemTypeName;

    public String status;

    public BigDecimal price;
    public BigDecimal salePrice;
    public LocalDateTime saleStart;
    public LocalDateTime saleEnd;

    public Integer stock;
    public String imageUrl;

    public Boolean taxable;
    public String taxClass; // keep string so AI reads it easily

    // ---------- Currency (relation) ----------
    public Long currencyId;
    public String currencyCode;
    public String currencySymbol;

    // ---------- Business ----------
    public Long businessId;
    public String businessName;

    // ---------- Activity-only ----------
    public String location;
    public Double latitude;
    public Double longitude;
    public LocalDateTime startDatetime;
    public LocalDateTime endDatetime;
    public Integer maxParticipants;

    // ---------- Product-only ----------
    public String sku;
    public String productType;
    public Boolean virtualProduct;
    public Boolean downloadable;
    public String downloadUrl;
    public String externalUrl;
    public String buttonText;

    public BigDecimal weightKg;
    public BigDecimal widthCm;
    public BigDecimal heightCm;
    public BigDecimal lengthCm;

    // Helper flags
    public Boolean isProduct = false;
    public Boolean isActivity = false;

    public AiItemContextDTO() {}

    // ✅ Build from Item base fields only
    public static AiItemContextDTO fromItem(Item i) {
        AiItemContextDTO dto = new AiItemContextDTO();

        dto.id = i.getId();
        dto.aupId = (i.getOwnerProject() != null) ? i.getOwnerProject().getId() : null;

        dto.name = i.getName(); // ✅ real field
        dto.description = i.getDescription();
        dto.itemTypeName = (i.getItemType() != null) ? i.getItemType().getName() : null;

        dto.status = i.getStatus();

        dto.price = i.getPrice();
        dto.salePrice = i.getSalePrice();
        dto.saleStart = i.getSaleStart();
        dto.saleEnd = i.getSaleEnd();

        dto.stock = i.getStock();
        dto.imageUrl = i.getImageUrl();

        dto.taxable = i.isTaxable();
        dto.taxClass = (i.getTaxClass() != null) ? i.getTaxClass().name() : null;

        if (i.getBusiness() != null) {
            dto.businessId = i.getBusiness().getId();
            dto.businessName = i.getBusiness().getBusinessName(); // ✅ assuming field name
        }

        Currency c = i.getCurrency();
        if (c != null) {
            dto.currencyId = c.getId();
            // ⚠️ adjust these 2 lines to your Currency fields
            dto.currencyCode = c.getCode();
            dto.currencySymbol = c.getSymbol();
        }

        return dto;
    }

    // ✅ Attach Product fields
    public void applyProduct(Product p) {
        this.isProduct = true;

        this.sku = p.getSku();
        this.productType = (p.getProductType() != null) ? p.getProductType().name() : null;
        this.virtualProduct = p.isVirtualProduct();
        this.downloadable = p.isDownloadable();
        this.downloadUrl = p.getDownloadUrl();
        this.externalUrl = p.getExternalUrl();
        this.buttonText = p.getButtonText();

        this.weightKg = p.getWeightKg();
        this.widthCm = p.getWidthCm();
        this.heightCm = p.getHeightCm();
        this.lengthCm = p.getLengthCm();
    }

    // ✅ Attach Activity fields
    public void applyActivity(Activity a) {
        this.isActivity = true;

        this.location = a.getLocation();
        this.latitude = a.getLatitude();
        this.longitude = a.getLongitude();
        this.startDatetime = a.getStartDatetime();
        this.endDatetime = a.getEndDatetime();
        this.maxParticipants = a.getMaxParticipants();
    }

    //  Effective price computed WITHOUT transient reliance
    public BigDecimal effectivePriceNow() {
        if (price == null) return null;
        if (salePrice == null) return price;
        if (salePrice.compareTo(BigDecimal.ZERO) <= 0) return price;
        if (salePrice.compareTo(price) >= 0) return price;

        LocalDateTime now = LocalDateTime.now();
        if (saleStart != null && now.isBefore(saleStart)) return price;
        if (saleEnd != null && now.isAfter(saleEnd)) return price;

        return salePrice;
    }
}
