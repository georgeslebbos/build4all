package com.build4all.user.dto;

/**
 * UserSummaryDTO = lightweight “list view” DTO.
 *
 * Purpose:
 * - Used when you need to show a table/list of people (users + admins) without loading the full profile.
 * - Keeps the response small and stable: id + full name + email/identifier + role.
 *
 * In your project it’s used by AdminUserService.getAllUserSummaries() to merge:
 * - normal app users (role forced to "USER")
 * - admin users (role from DB: SUPER_ADMIN / OWNER / MANAGER ...)
 */
public class UserSummaryDTO {
    private Long id;          // user_id (Users) OR admin_id (AdminUser)
    private String fullName;  // firstName + " " + lastName
    private String email;     // for Users: email OR phoneNumber, for AdminUser: email
    private String role;      // "USER" or admin role name

    public UserSummaryDTO(Long id, String fullName, String email, String role) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
    }

    // Getters only: DTO is read-only for the API response
    public Long getId() { return id; }

    public String getFullName() { return fullName; }

    public String getEmail() { return email; }

    public String getRole() { return role; }
}
