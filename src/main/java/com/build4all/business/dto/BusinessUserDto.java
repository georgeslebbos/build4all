package com.build4all.business.dto;

/**
 * BusinessUserDto
 * ------------------------------------------------------------
 * A DTO (Data Transfer Object) used to move BusinessUser data
 * between layers (Controller <-> Service <-> Client) without
 * exposing the full JPA entity.
 *
 * Why DTO here?
 * - Prevents exposing JPA relations (like Business, Role) accidentally
 * - Avoids lazy-loading / serialization issues in JSON responses
 * - Lets you control exactly what fields the API returns/accepts
 *
 * ⚠️ Security note:
 * - Having passwordHash inside a DTO is risky.
 *   - You should NOT return passwordHash in API responses.
 *   - If you need it for internal create/update, consider separating:
 *     * BusinessUserCreateRequest (includes raw password)
 *     * BusinessUserResponseDto (never contains passwordHash)
 */
public class BusinessUserDto {

    /**
     * Primary key of business_user table (BusinessUser.id).
     * Equivalent SQL: business_user.id
     */
    private Long id; // 👈 primary key

    /** Manager/employee first name (NOT NULL in entity). */
    private String firstName;

    /** Manager/employee last name (NOT NULL in entity). */
    private String lastName;

    /**
     * Email of the business user (could be null in entity).
     * Used often as a login identifier (depending on your auth flow).
     */
    private String email;

    // Optional fields (can be null)

    /**
     * Username chosen by the business user (optional).
     * If you authenticate by username, ensure it’s unique (per business or globally).
     */
    private String username;

    /**
     * Stored password hash (BCrypt, etc).
     * ⚠️ Should not be exposed to the frontend in responses.
     */
    private String passwordHash;

    /** Phone number (optional). */
    private String phoneNumber;

    /** Social login id (Google) if linked; null otherwise. */
    private String googleId;

    /** URL/path of profile picture (e.g. "/uploads/xyz.png"). */
    private String profilePictureUrl;

    /** Profile visibility flag for public listing (optional). */
    private Boolean isPublicProfile;

    /**
     * Status reference.
     * This DTO keeps only the ID of the status instead of the full UserStatus entity.
     *
     * Equivalent SQL idea:
     * - business_user.status -> user_status.id (or your referenced PK)
     *
     * Note: In your entity, status is:
     *   @ManyToOne @JoinColumn(name="status")
     * So the column stored in business_user is "status" (FK).
     */
    private Long statusId;

    // --- Constructors ---

    public BusinessUserDto() {
        // Default constructor required for JSON deserialization (Jackson)
    }

    /**
     * Full constructor (useful for manual mapping or JPQL projections).
     */
    public BusinessUserDto(Long id, String firstName, String lastName, String email, String username,
                           String passwordHash, String phoneNumber, String googleId,
                           String profilePictureUrl, Boolean isPublicProfile, Long statusId) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.username = username;
        this.passwordHash = passwordHash;
        this.phoneNumber = phoneNumber;
        this.googleId = googleId;
        this.profilePictureUrl = profilePictureUrl;
        this.isPublicProfile = isPublicProfile;
        this.statusId = statusId;
    }

    // --- Getters and Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getGoogleId() {
        return googleId;
    }

    public void setGoogleId(String googleId) {
        this.googleId = googleId;
    }

    public String getProfilePictureUrl() {
        return profilePictureUrl;
    }

    public void setProfilePictureUrl(String profilePictureUrl) {
        this.profilePictureUrl = profilePictureUrl;
    }

    public Boolean getIsPublicProfile() {
        return isPublicProfile;
    }

    public void setIsPublicProfile(Boolean isPublicProfile) {
        this.isPublicProfile = isPublicProfile;
    }

    public Long getStatusId() {
        return statusId;
    }

    public void setStatusId(Long statusId) {
        this.statusId = statusId;
    }
}
