package com.build4all.catalog.dto;

import java.time.LocalDateTime;

public class ItemSummaryDTO {
    private Long id;
    private String itemName;
    private String businessName;
    private LocalDateTime date;
    private int participants;

    public ItemSummaryDTO(Long id, String itemName, String businessName, LocalDateTime date, int participants) {
        this.id = id;
        this.itemName = itemName;
        this.businessName = businessName;
        this.date = date;
        this.participants = participants;
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    public int getParticipants() {
        return participants;
    }

    public void setParticipants(int participants) {
        this.participants = participants;
    }
}
