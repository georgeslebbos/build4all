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
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private String password;

    // ✅ optional
    private String phoneNumber;

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
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

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public void setPhoneNumber(String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}

   
}
