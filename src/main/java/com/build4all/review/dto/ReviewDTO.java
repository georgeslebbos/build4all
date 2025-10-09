package com.build4all.review.dto;

import java.time.LocalDateTime;

public class ReviewDTO {
    private Long id;
    private Long itemId;

    private Integer rating;
    private String feedback;
    private LocalDateTime date;

    public ReviewDTO() {}

    public ReviewDTO(Long id, Long itemId, Integer rating, String feedback, LocalDateTime date) {
        this.id = id;
        this.itemId = itemId;
        this.rating = rating;
        this.feedback = feedback;
        this.date = date;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }
}
