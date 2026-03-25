package com.build4all.catalog.dto;

public class ItemImageDTO {

    private Long id;
    private String imageUrl;
    private Integer sortOrder;
    private boolean mainImage;

    public ItemImageDTO() {
    }

    public ItemImageDTO(Long id, String imageUrl, Integer sortOrder, boolean mainImage) {
        this.id = id;
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder;
        this.mainImage = mainImage;
    }

    public Long getId() {
        return id;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public boolean isMainImage() {
        return mainImage;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void setMainImage(boolean mainImage) {
        this.mainImage = mainImage;
    }
}