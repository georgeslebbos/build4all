package com.build4all.catalog.dto;

public class AdminItemDTO {
  private Long id;
  private String itemName;
  private String businessName;
  private String description;

  public AdminItemDTO(Long id, String itemName, String businessName, String description) {
    this.id = id;
    this.itemName = itemName;
    this.businessName = businessName;
    this.description = description;
  }
  // getters/setters
}
