package com.build4all.business.dto;

/**
 * LowRatedBusinessDTO
 * ------------------------------------------------------------
 * Lightweight DTO used for analytics/admin dashboards.
 *
 * Purpose:
 * - Return businesses with low average ratings (e.g., below 2.5)
 * - Avoid returning full JPA entities (smaller payload + fewer lazy-loading / serialization issues)
 *
 * Notes:
 * - `status` is stored as a String (status name) instead of returning the full BusinessStatus entity.
 *   This makes it easier for the front-end and avoids lazy / recursion problems.
 * - `averageRating` is typically computed by an aggregate query AVG(...) over the reviews table.
 */
public class LowRatedBusinessDTO {

    /** Primary key from businesses table (business_id). */
    private Long id;

    /** Business display name (usually businesses.business_name). */
    private String name;

    /**
     * Status name only (e.g., "ACTIVE", "INACTIVE", "SUSPENDED").
     * Using String here avoids returning BusinessStatus as an entity.
     */
    private String status;

    /**
     * Average rating value (usually AVG(review.rating)).
     * Scale depends on your rating system (commonly 1.0 .. 5.0).
     */
    private double averageRating;

    /**
     * Constructor commonly used by JPQL "select new ..." projections or service-level mapping.
     *
     * Example JPQL:
     * SELECT new com.build4all.business.dto.LowRatedBusinessDTO(
     *     b.id, b.businessName, bs.name, COALESCE(AVG(r.rating), 0)
     * )
     * FROM Businesses b
     * JOIN b.status bs
     * LEFT JOIN b.reviews r
     * GROUP BY b.id, b.businessName, bs.name
     * HAVING COALESCE(AVG(r.rating), 0) < :threshold
     */
    public LowRatedBusinessDTO(Long id, String name, String status, double averageRating) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.averageRating = averageRating;
    }

    // --- Getters only (immutable DTO) ---

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
