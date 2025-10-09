package com.build4all.catalog.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ItemDetailsDTO {

    private Long id;
    private String itemName;
    private String description;
    private Long itemTypeId;
    private String itemTypeName;
    private String location;
    private Double latitude;
    private Double longitude;
    private LocalDateTime startDatetime;
    private LocalDateTime endDatetime;
    private BigDecimal price;
    private int maxParticipants;
    private String status;
    private String imageUrl;
    private String businessName;

    public ItemDetailsDTO(Long id, String itemName, String description,
                          Long itemTypeId, String itemTypeName,
                          String location, Double latitude, Double longitude,
                          LocalDateTime startDatetime, LocalDateTime endDatetime,
                          BigDecimal price, int maxParticipants, String status,
                          String imageUrl, String businessName) {
        this.id = id;
        this.itemName = itemName;
        this.description = description;
        this.itemTypeId = itemTypeId;
        this.itemTypeName = itemTypeName;
        this.location = location;
        this.latitude = latitude;
        this.longitude = longitude;
        this.startDatetime = startDatetime;
        this.endDatetime = endDatetime;
        this.price = price;
        this.maxParticipants = maxParticipants;
        this.status = status;
        this.imageUrl = imageUrl;
        this.businessName = businessName;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getItemTypeId() { return itemTypeId; }
    public void setItemTypeId(Long itemTypeId) { this.itemTypeId = itemTypeId; }

    public String getItemTypeName() { return itemTypeName; }
    public void setItemTypeName(String itemTypeName) { this.itemTypeName = itemTypeName; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public LocalDateTime getStartDatetime() { return startDatetime; }
    public void setStartDatetime(LocalDateTime startDatetime) { this.startDatetime = startDatetime; }

    public LocalDateTime getEndDatetime() { return endDatetime; }
    public void setEndDatetime(LocalDateTime endDatetime) { this.endDatetime = endDatetime; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public int getMaxParticipants() { return maxParticipants; }
    public void setMaxParticipants(int maxParticipants) { this.maxParticipants = maxParticipants; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
}
