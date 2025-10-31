package com.build4all.features.activity.dto;

import java.time.LocalDateTime;

public class AdminActivityDTO {
    private Long id;
    private String itemName;
    private String businessName;
    private LocalDateTime startDatetime;
    private Integer maxParticipants;
    private String description;

    // JPQL constructor expression requires this exact constructor (order + types)
    public AdminActivityDTO(Long id,
                            String itemName,
                            String businessName,
                            LocalDateTime startDatetime,
                            Integer maxParticipants,
                            String description) {
        this.id = id;
        this.itemName = itemName;
        this.businessName = businessName;
        this.startDatetime = startDatetime;
        this.maxParticipants = maxParticipants;
        this.description = description;
    }

    // Getters (needed by Jackson / templates)
    public Long getId() { return id; }
    public String getItemName() { return itemName; }
    public String getBusinessName() { return businessName; }
    public LocalDateTime getStartDatetime() { return startDatetime; }
    public Integer getMaxParticipants() { return maxParticipants; }
    public String getDescription() { return description; }
}
