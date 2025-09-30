package com.build4all.dto;

public class LowRatedBusinessDTO {
    private Long id;
    private String name;
    private String status; // ✅ just store the status name
    private double averageRating;

    public LowRatedBusinessDTO(Long id, String name, String status, double averageRating) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.averageRating = averageRating;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public double getAverageRating() {
        return averageRating;
    }
}
