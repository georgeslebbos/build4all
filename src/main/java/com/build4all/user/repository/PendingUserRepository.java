package com.build4all.user.repository;

import com.build4all.user.domain.PendingUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * PendingUserRepository = Spring Data JPA repository for the PendingUser table.
 *
 * What is PendingUser?
 * - It represents a “user not fully activated yet” (e.g., after signup but before OTP/email verification).
 * - So you can store temporary registration data + verificationCode + isVerified
 *   without creating a real Users record.
 *
 * Why JpaRepository<PendingUser, Long>?
 * - Entity type: PendingUser
 * - Primary key type: Long (the "id" field in PendingUser)
 *
 * You automatically get CRUD methods like:
 * - save(pendingUser), findById(id), findAll(), deleteById(id), etc.
 *
 * The extra methods below are “derived queries”:
 * Spring generates the SQL automatically based on the method name.
 */
@Repository
public interface PendingUserRepository extends JpaRepository<PendingUser, Long> {

    /**
     * SELECT * FROM pending_users WHERE email = ?
     * Returns the PendingUser if found, otherwise returns null (because return type is not Optional).
     */
    PendingUser findByEmail(String email);

    /**
     * SELECT COUNT(*) > 0 FROM pending_users WHERE email = ?
     * Used to prevent duplicate pending registrations with the same email.
     */
    boolean existsByEmail(String email);

    /**
     * SELECT COUNT(*) > 0 FROM pending_users WHERE username = ?
     * Used to check if the username is already taken in pending registrations.
     */
    boolean existsByUsername(String username);

    /**
     * SELECT COUNT(*) > 0 FROM pending_users WHERE phone_number = ?
     * Used to prevent duplicates on phone.
     */
    boolean existsByPhoneNumber(String phoneNumber);

    /**
     * SELECT * FROM pending_users WHERE phone_number = ?
     * Returns the PendingUser if found, otherwise null.
     */
    PendingUser findByPhoneNumber(String phoneNumber);
}
