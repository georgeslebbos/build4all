package com.build4all.admin.dto;

/**
 * Request DTO used when registering/creating a new admin account from the API.
 *
 * Typical flow:
 * - Client sends username/firstName/lastName/email/password
 * - Service validates uniqueness (email/username), encodes the password, assigns a role, then saves AdminUser
 *
 * Note: This DTO carries the RAW password from the client.
 * The backend must ALWAYS hash/encode it (PasswordEncoder) before persisting.
 */
public class AdminRegisterRequest {

    // Desired username for the admin (may be used for login/display).
    private String username;

    // Admin's first and last names (profile info).
    private String firstName;
    private String lastName;

    // Email used for contact and often as a login identifier.
    private String email;

    // Raw password received from the client (must be encoded before storing in DB).
    private String password;

    // Getters and setters

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        // Set username provided by the client.
        this.username = username;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        // Set first name provided by the client.
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        // Set last name provided by the client.
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        // Set email provided by the client.
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        // Set raw password provided by the client (to be encoded later).
        this.password = password;
    }
}
