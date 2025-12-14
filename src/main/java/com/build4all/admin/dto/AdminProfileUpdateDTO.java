package com.build4all.admin.dto;

/**
 * DTO used to update an admin user's basic profile information.
 *
 * Typical usage:
 * - Frontend sends this object in a PUT/PATCH request (profile edit screen).
 * - Backend applies non-null fields to the AdminUser entity (firstName/lastName/username/email).
 *
 * This DTO intentionally contains only editable fields (no role, no password, no business linkage).
 */
public class AdminProfileUpdateDTO {

    // Editable profile fields
    private String firstName;
    private String lastName;
    private String username;
    private String email;

    // Getters and Setters
    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        // New first name value from client.
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        // New last name value from client.
        this.lastName = lastName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        // New username value from client (often used for login/display).
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        // New email value from client (often used for login/contact).
        this.email = email;
    }
}
