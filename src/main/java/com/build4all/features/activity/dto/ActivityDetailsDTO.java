package com.build4all.features.activity.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ActivityDetailsDTO {
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
    private Integer maxParticipants;
    private String status;
    private String imageUrl;
    private String businessName;

    public ActivityDetailsDTO() {}

    public ActivityDetailsDTO(Long id, String itemName, String description, Long itemTypeId, String itemTypeName,
                              String location, Double latitude, Double longitude, LocalDateTime startDatetime,
                              LocalDateTime endDatetime, BigDecimal price, Integer maxParticipants, String status,
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

    public Long getId() { return id; }
    public String getItemName() { return itemName; }
    public String getDescription() { return description; }
    public Long getItemTypeId() { return itemTypeId; }
    public String getItemTypeName() { return itemTypeName; }
    public String getLocation() { return location; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public LocalDateTime getStartDatetime() { return startDatetime; }
    public LocalDateTime getEndDatetime() { return endDatetime; }
    public BigDecimal getPrice() { return price; }
    public Integer getMaxParticipants() { return maxParticipants; }
    public String getStatus() { return status; }
    public String getImageUrl() { return imageUrl; }
    public String getBusinessName() { return businessName; }

    public void setId(Long id) { this.id = id; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public void setDescription(String description) { this.description = description; }
    public void setItemTypeId(Long itemTypeId) { this.itemTypeId = itemTypeId; }
    public void setItemTypeName(String itemTypeName) { this.itemTypeName = itemTypeName; }
    public void setLocation(String location) { this.location = location; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public void setStartDatetime(LocalDateTime startDatetime) { this.startDatetime = startDatetime; }
    public void setEndDatetime(LocalDateTime endDatetime) { this.endDatetime = endDatetime; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public void setMaxParticipants(Integer maxParticipants) { this.maxParticipants = maxParticipants; }
    public void setStatus(String status) { this.status = status; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
}
