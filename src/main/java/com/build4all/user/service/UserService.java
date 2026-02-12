// src/main/java/com/build4all/user/service/UserService.java
package com.build4all.user.service;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.catalog.domain.Category;
import com.build4all.catalog.repository.CategoryRepository;
import com.build4all.notifications.service.EmailService;
import com.build4all.role.domain.Role;
import com.build4all.role.repository.RoleRepository;
import com.build4all.user.domain.PendingUser;
import com.build4all.user.domain.UserCategories;
import com.build4all.user.domain.UserStatus;
import com.build4all.user.domain.Users;
import com.build4all.user.dto.UserDto;
import com.build4all.user.repository.PendingUserRepository;
import com.build4all.user.repository.UserCategoriesRepository;
import com.build4all.user.repository.UserStatusRepository;
import com.build4all.user.repository.UsersRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import jakarta.mail.internet.InternetAddress;


import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * UserService (tenant-aware)
 *
 * ✅ Tenant key in your schema is: Users.aup_id (FK to AdminUserProject)
 * We pass it as: ownerProjectLinkId (AdminUserProject.id)
 *
 * Important SQL note:
 * - Any repository call below will translate to SQL.
 * - I’m adding the “equivalent SQL” as comments right above the exact call.
 *
 * ✅ SECURITY NOTE (important):
 * - For authentication/login: ALWAYS use tenant-scoped lookups:
 *   findByEmailOptional / findByPhoneOptional / findByUsernameOptional.
 * - Never use global findByEmail(email) for login (it breaks tenancy and leaks cross-tenant).
 */
@Service
public class UserService {

    @Autowired private UsersRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserCategoriesRepository userCategoriesRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private PendingUserRepository pendingUserRepository;
    @Autowired private UserStatusRepository userStatusRepository;
    @Autowired private RoleRepository roleRepository;

    /** Tenant link repository: AdminUserProject table (often called admin_user_project / aup). */
    @Autowired private AdminUserProjectRepository aupRepo;

    private final EmailService emailService;
    public UserService(EmailService emailService) { this.emailService = emailService; }
    
    
    private void validateEmailOrThrow(String email) {
        try {
            InternetAddress addr = new InternetAddress(email, true); // strict
            addr.validate();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid email format");
        }
    }


    /* ============================ Helpers ============================ */

    /**
     * Resolve a UserStatus by name (throws if missing).
     *
     * SQL (conceptually):
     *   SELECT *
     *   FROM user_status
     *   WHERE name = :NAME_UPPER
     *   LIMIT 1;
     */
    public UserStatus getStatus(String name) {
        return userStatusRepository.findByName(name.toUpperCase())
                .orElseThrow(() -> new RuntimeException("UserStatus " + name + " not found"));
    }

    /**
     * Resolve Role by name ignoring case.
     *
     * SQL (portable concept):
     *   SELECT *
     *   FROM role
     *   WHERE LOWER(name) = LOWER(:name)
     *   LIMIT 1;
     */
    private Role getRoleOrThrow(String name) {
        return roleRepository.findByNameIgnoreCase(name)
                .orElseThrow(() -> new RuntimeException("Role " + name + " not found"));
    }

    /**
     * Ensure the tenant link exists and return it.
     *
     * SQL:
     *   SELECT *
     *   FROM admin_user_project
     *   WHERE id = :ownerProjectLinkId
     *   LIMIT 1;
     */
    private AdminUserProject linkById(Long ownerProjectLinkId) {
        if (ownerProjectLinkId == null) throw new IllegalArgumentException("ownerProjectLinkId is required");
        return aupRepo.findById(ownerProjectLinkId)
                .orElseThrow(() -> new RuntimeException("AdminUserProject not found: " + ownerProjectLinkId));
    }

    /* ====================== LOGIN (tenant-safe OPTIONAL) ===================== */

    /**
     * ✅ Tenant-safe lookup by email for login (case-insensitive).
     *
     * Returns Optional.empty() if:
     * - user does not exist in this tenant
     * - OR email exists but in another tenant (we MUST NOT leak)
     *
     * SQL:
     *   SELECT * FROM users
     *   WHERE LOWER(email)=LOWER(:email)
     *     AND aup_id=:ownerProjectLinkId
     *   LIMIT 1;
     */
    public Optional<Users> findByEmailOptional(String email, Long ownerProjectLinkId) {
        if (ownerProjectLinkId == null) return Optional.empty();
        if (email == null || email.isBlank()) return Optional.empty();

        // NOTE: this method must exist in UsersRepository:
        // Optional<Users> findByEmailIgnoreCaseAndOwnerProject_Id(String email, Long ownerProjectId);
        return userRepository.findByEmailIgnoreCaseAndOwnerProject_Id(email.trim(), ownerProjectLinkId);
    }

    /**
     * ✅ Tenant-safe lookup by phone for login.
     *
     * SQL:
     *   SELECT * FROM users
     *   WHERE phone_number=:phoneNumber
     *     AND aup_id=:ownerProjectLinkId
     *   LIMIT 1;
     */
    public Optional<Users> findByPhoneOptional(String phoneNumber, Long ownerProjectLinkId) {
        if (ownerProjectLinkId == null) return Optional.empty();
        if (phoneNumber == null || phoneNumber.isBlank()) return Optional.empty();

        // Your repo already uses the non-Optional version; we wrap it into Optional.
        Users u = userRepository.findByPhoneNumberAndOwnerProject_Id(phoneNumber.trim(), ownerProjectLinkId);
        return Optional.ofNullable(u);
    }

    /**
     * ✅ Tenant-safe lookup by username for login (case-insensitive).
     *
     * SQL:
     *   SELECT * FROM users
     *   WHERE LOWER(username)=LOWER(:username)
     *     AND aup_id=:ownerProjectLinkId
     *   LIMIT 1;
     */
    public Optional<Users> findByUsernameOptional(String username, Long ownerProjectLinkId) {
        if (ownerProjectLinkId == null) return Optional.empty();
        if (username == null || username.isBlank()) return Optional.empty();

        // NOTE: this method must exist in UsersRepository:
        // Optional<Users> findByUsernameIgnoreCaseAndOwnerProject_Id(String username, Long ownerProjectId);
        return userRepository.findByUsernameIgnoreCaseAndOwnerProject_Id(username.trim(), ownerProjectLinkId);
    }

    /* ====================== Registration (tenant) ===================== */

    /**
     * Start registration:
     * - Validate tenant exists
     * - Tenant-scoped uniqueness (email/phone) against table users
     * - Create/update PendingUser in table pending_users
     * - Send email code (if email)
     */
    public boolean sendVerificationCodeForRegistration(Map<String, String> userData, Long ownerProjectLinkId) {

        linkById(ownerProjectLinkId);

        String email = userData.get("email");
        String phone = userData.get("phoneNumber");
        String password = userData.get("password");

        boolean emailProvided = email != null && !email.isBlank();
        boolean phoneProvided = phone != null && !phone.isBlank();

        if (!emailProvided && !phoneProvided) throw new IllegalArgumentException("Provide email or phone");
        if (emailProvided && phoneProvided)    throw new IllegalArgumentException("Provide only one: email OR phone");

        // ✅ normalize
        if (emailProvided) email = email.trim();
        if (phoneProvided) phone = phone.trim();

        // ✅ format validation (BEFORE any send)
        if (emailProvided) {
            validateEmailOrThrow(email);

            if (userRepository.existsByEmailAndOwnerProject_Id(email, ownerProjectLinkId)) {
                throw new IllegalArgumentException("Email already in use in this app");
            }
        }

        if (phoneProvided) {
            if (userRepository.existsByPhoneNumberAndOwnerProject_Id(phone, ownerProjectLinkId)) {
                throw new IllegalArgumentException("Phone already in use in this app");
            }
        }

        String statusStr = Optional.ofNullable(userData.get("status")).orElse("PENDING");
        UserStatus status = getStatus(statusStr);

        PendingUser pending = emailProvided
                ? pendingUserRepository.findByEmail(email)
                : pendingUserRepository.findByPhoneNumber(phone);

        String code = phoneProvided ? "123456" : String.format("%06d", new Random().nextInt(999999));

        if (pending != null) {
            if (Boolean.TRUE.equals(pending.isVerified())) return true;

            pending.setPasswordHash(passwordEncoder.encode(password));
            pending.setVerificationCode(code);
            pending.setCreatedAt(LocalDateTime.now());
            pending.setStatus(status);

        } else {
            pending = new PendingUser();
            pending.setEmail(emailProvided ? email : null);
            pending.setPhoneNumber(phoneProvided ? phone : null);
            pending.setPasswordHash(passwordEncoder.encode(password));
            pending.setVerificationCode(code);
            pending.setCreatedAt(LocalDateTime.now());
            pending.setStatus(status);
            pending.setIsPublicProfile(true);
            pending.setIsVerified(false);
        }

        pendingUserRepository.save(pending);

        if (emailProvided) {
            String htmlMessage = """
                <html><body style="font-family: Arial; text-align:center; padding:20px">
                <h2>Welcome to build4all</h2>
                <p>Use this code to verify your email:</p>
                <h1>%s</h1>
                <p style="color:#777">Expires in 10 minutes.</p>
                </body></html>
            """.formatted(code);

            emailService.sendHtmlEmail(email, "Email Verification Code", htmlMessage);
        }

        return true;
    }

    // src/main/java/com/build4all/user/service/UserService.java

    @Transactional
    public Users updateUserProfile(
            Long userId,
            Long ownerProjectLinkId,
            Long tokenUserId,
            String username,
            String firstName,
            String lastName,
            Boolean isPublicProfile,
            MultipartFile profileImage,
            Boolean imageRemoved
    ) throws IOException {

        if (userId == null || ownerProjectLinkId == null)
            throw new IllegalArgumentException("userId and ownerProjectLinkId are required");

        Users user = getUserById(userId, ownerProjectLinkId); // tenant-safe

        // ✅ self-only security by token id
        if (tokenUserId == null || !userId.equals(tokenUserId)) {
            throw new RuntimeException("Access denied");
        }

        // ✅ username uniqueness per tenant (only if changed)
        if (username != null && !username.isBlank()) {
            String newU = username.trim();
            if (!newU.equalsIgnoreCase(user.getUsername())
                    && userRepository.existsByUsernameIgnoreCaseAndOwnerProject_Id(newU, ownerProjectLinkId)) {
                throw new RuntimeException("Username already in use in this app.");
            }
            user.setUsername(newU);
        }

        user.setFirstName(firstName);
        user.setLastName(lastName);

        if (isPublicProfile != null) user.setIsPublicProfile(isPublicProfile);

        // ✅ image remove
        if (Boolean.TRUE.equals(imageRemoved)) {
            user.setProfilePictureUrl(null);
        }

        // ✅ image upload overrides
        if (profileImage != null && !profileImage.isEmpty()) {
            String url = saveProfileImage(profileImage); // you already have this helper
            user.setProfilePictureUrl(url);
        }

        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    /**
     * Resend verification code (email or phone).
     * For SMS we keep a fixed code "123456" for testing/dev.
     */
    public boolean resendVerificationCode(String emailOrPhone) {
        boolean isEmail = emailOrPhone != null && emailOrPhone.contains("@");

        PendingUser pending = isEmail
                ? (
                // SQL:
                //   SELECT * FROM pending_users WHERE email = :emailOrPhone LIMIT 1;
                pendingUserRepository.findByEmail(emailOrPhone)
        )
                : (
                // SQL:
                //   SELECT * FROM pending_users WHERE phone_number = :emailOrPhone LIMIT 1;
                pendingUserRepository.findByPhoneNumber(emailOrPhone)
        );

        if (pending == null) throw new RuntimeException("No pending user found");

        if (isEmail) {
            String code = String.format("%06d", new Random().nextInt(999999));

            // UPDATE pending_users ...
            pending.setVerificationCode(code);
            pending.setCreatedAt(LocalDateTime.now());

            // SQL:
            //   UPDATE pending_users SET verification_code=?, created_at=? WHERE id=?;
            pendingUserRepository.save(pending);

            String html = """
                <html><body style="font-family: Arial; padding: 20px;">
                    <h2>build4all Verification</h2>
                    <p>Your new verification code is:</p>
                    <h1>%s</h1>
                </body></html>
            """.formatted(code);

            emailService.sendHtmlEmail(emailOrPhone, "New Verification Code", html);

        } else {
            // UPDATE pending_users ...
            pending.setVerificationCode("123456");
            pending.setCreatedAt(LocalDateTime.now());

            // SQL:
            //   UPDATE pending_users SET verification_code='123456', created_at=? WHERE id=?;
            pendingUserRepository.save(pending);
        }

        return true;
    }

    public Long verifyEmailCodeAndRegister(String email, String code) {

        // SQL:
        //   SELECT * FROM pending_users WHERE email = :email LIMIT 1;
        PendingUser pending = pendingUserRepository.findByEmail(email);

        if (pending == null) throw new RuntimeException("No pending user for this email");
        if (!Objects.equals(pending.getVerificationCode(), code)) throw new RuntimeException("Invalid code");

        // UPDATE pending_users SET is_verified=true WHERE id=?;
        pending.setIsVerified(true);

        // SQL:
        //   UPDATE pending_users SET is_verified=true WHERE id=:id;
        pendingUserRepository.save(pending);

        return pending.getId();
    }

    public Long verifyPhoneCodeAndRegister(String phoneNumber, String code) {
        if (!"123456".equals(code)) throw new RuntimeException("Invalid code");

        // SQL:
        //   SELECT * FROM pending_users WHERE phone_number = :phoneNumber LIMIT 1;
        PendingUser pending = pendingUserRepository.findByPhoneNumber(phoneNumber);

        if (pending == null) throw new RuntimeException("Pending user not found");

        // UPDATE pending_users SET is_verified=true WHERE id=?;
        pending.setIsVerified(true);

        // SQL:
        //   UPDATE pending_users SET is_verified=true WHERE id=:id;
        pendingUserRepository.save(pending);

        return pending.getId();
    }

    /**
     * Finish registration: create the real user in the right tenant.
     *
     * @Transactional:
     * - Ensures that INSERT into users + DELETE from pending_users happen atomically.
     * - If any exception occurs, both DB operations rollback.
     */
    @Transactional
    public boolean completeUserProfile(Long pendingId,
                                       String username,
                                       String firstName,
                                       String lastName,
                                       MultipartFile profileImage,
                                       Boolean isPublicProfile,
                                       Long ownerProjectLinkId) throws IOException {

        // SQL:
        //   SELECT * FROM admin_user_project WHERE id=:ownerProjectLinkId LIMIT 1;
        AdminUserProject link = linkById(ownerProjectLinkId);

        // SQL:
        //   SELECT * FROM pending_users WHERE id=:pendingId LIMIT 1;
        PendingUser pending = pendingUserRepository.findById(pendingId)
                .orElseThrow(() -> new RuntimeException("Pending user not found."));

        // SQL (portable):
        //   SELECT EXISTS(
        //     SELECT 1 FROM users
        //     WHERE LOWER(username)=LOWER(:username) AND aup_id=:ownerProjectLinkId
        //   );
        if (userRepository.existsByUsernameIgnoreCaseAndOwnerProject_Id(username, ownerProjectLinkId)) {
            throw new RuntimeException("Username already in use in this app.");
        }

        Users user = new Users();
        user.setOwnerProject(link);
        user.setUsername(username);
        user.setFirstName(firstName);
        user.setLastName(lastName);

        // password hash already saved in pending_users
        user.setPasswordHash(pending.getPasswordHash());

        // SQL:
        //   SELECT * FROM user_status WHERE name='ACTIVE' LIMIT 1;
        user.setStatus(getStatus("ACTIVE"));

        user.setCreatedAt(LocalDateTime.now());

        if (pending.getEmail() != null) user.setEmail(pending.getEmail());
        if (pending.getPhoneNumber() != null) user.setPhoneNumber(pending.getPhoneNumber());

        user.setIsPublicProfile(isPublicProfile != null ? isPublicProfile : true);

        // SQL:
        //   SELECT * FROM role WHERE LOWER(name)=LOWER('USER') LIMIT 1;
        user.setRole(getRoleOrThrow("USER"));

        // File system write (not SQL)
        if (profileImage != null && !profileImage.isEmpty()) {
            String filename = UUID.randomUUID() + "_" + profileImage.getOriginalFilename();
            Path path = Paths.get("uploads");
            if (!Files.exists(path)) Files.createDirectories(path);
            Files.copy(profileImage.getInputStream(), path.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            user.setProfilePictureUrl("/uploads/" + filename);
        }

        user.setUpdatedAt(LocalDateTime.now());

        // SQL:
        //   INSERT INTO users (...) VALUES (...);
        // or UPDATE if it had an id (it doesn’t here)
        userRepository.save(user);

        // SQL:
        //   DELETE FROM pending_users WHERE id=:pendingId;
        pendingUserRepository.delete(pending);

        return true;
    }

    /* ====================== Lookups (tenant) ====================== */

    /**
     * Tenant-scoped lookup (EMAIL).
     *
     * ✅ For login, prefer findByEmailOptional(...) to avoid any leaking behavior.
     */
    public Users findByEmail(String email, Long ownerProjectLinkId) {
        // SQL:
        //   SELECT * FROM users WHERE email=:email AND aup_id=:ownerProjectLinkId LIMIT 1;
        return userRepository.findByEmailAndOwnerProject_Id(email, ownerProjectLinkId);
    }

    /**
     * Tenant-scoped lookup (PHONE).
     *
     * ✅ For login, prefer findByPhoneOptional(...) to avoid any leaking behavior.
     */
    public Users findByPhoneNumber(String phone, Long ownerProjectLinkId) {
        // SQL:
        //   SELECT * FROM users WHERE phone_number=:phone AND aup_id=:ownerProjectLinkId LIMIT 1;
        return userRepository.findByPhoneNumberAndOwnerProject_Id(phone, ownerProjectLinkId);
    }

    /**
     * Tenant-scoped lookup (USERNAME).
     *
     * ✅ For login, prefer findByUsernameOptional(...) to avoid any leaking behavior.
     */
    public Users findByUsername(String username, Long ownerProjectLinkId) {
        // SQL:
        //   SELECT * FROM users WHERE username=:username AND aup_id=:ownerProjectLinkId LIMIT 1;
        return userRepository.findByUsernameAndOwnerProject_Id(username, ownerProjectLinkId);
    }

    public Users getUserByEmaill(String identifier, Long ownerProjectLinkId) {
        Users user = (identifier != null && identifier.contains("@"))
                ? (
                // SQL:
                //   SELECT * FROM users WHERE email=:identifier AND aup_id=:ownerProjectLinkId LIMIT 1;
                userRepository.findByEmailAndOwnerProject_Id(identifier, ownerProjectLinkId)
        )
                : (
                // SQL:
                //   SELECT * FROM users WHERE phone_number=:identifier AND aup_id=:ownerProjectLinkId LIMIT 1;
                userRepository.findByPhoneNumberAndOwnerProject_Id(identifier, ownerProjectLinkId)
        );

        if (user == null) throw new RuntimeException("User not found in this app: " + identifier);
        return user;
    }

    /**
     * Tenant-safe get by ID.
     * First try fetch-join query (JPQL) to avoid lazy issues.
     * If that fails (edge cases), fallback to native SQL with physical columns.
     */
    public Users getUserById(Long userId, Long ownerProjectLinkId) {
        if (userId == null || ownerProjectLinkId == null) {
            throw new IllegalArgumentException("userId and ownerProjectLinkId are required");
        }

        // 1) JPQL fetch join (idea):
        //   SELECT u, op, admin, project ...
        //   FROM users u
        //   JOIN admin_user_project op ON ...
        //   WHERE u.user_id=:userId AND op.id=:ownerProjectLinkId
        //
        // 2) Native fallback (exact):
        //   SELECT * FROM users WHERE user_id=:userId AND aup_id=:ownerProjectLinkId
        return userRepository.fetchByIdAndOwnerProjectId(userId, ownerProjectLinkId)
                .or(() -> userRepository.findByPkAndAupId(userId, ownerProjectLinkId))
                .orElseThrow(() -> new RuntimeException(
                        "User not found in this app: id=" + userId + ", ownerProjectLinkId=" + ownerProjectLinkId));
    }

    @Transactional(readOnly = true)
    public UserDto getUserDtoByIdAndOwnerProject(Long userId, Long ownerProjectLinkId) {

        // JPQL fetch join (see above)
        Users u = userRepository.fetchByIdAndOwnerProjectId(userId, ownerProjectLinkId)
                .orElseThrow(() -> new RuntimeException(
                        "User not found in this app: id=" + userId + ", ownerProjectLinkId=" + ownerProjectLinkId));

        // No SQL here; just mapping object -> DTO
        return new UserDto(u);
    }

    /* ==================== Password reset (tenant) ==================== */

    /**
     * resetCodes is in-memory (NOT DB). If server restarts, codes disappear.
     * If you want persistent reset flow, store codes in a table.
     */
    private final Map<String, String> resetCodes = new ConcurrentHashMap<>();

    public boolean resetPassword(String email, Long ownerProjectLinkId) {

        // SQL:
        //   SELECT * FROM users WHERE email=:email AND aup_id=:ownerProjectLinkId LIMIT 1;
        Users user = userRepository.findByEmailAndOwnerProject_Id(email, ownerProjectLinkId);

        if (user == null) return false;

        // Not SQL: store in memory
        String code = String.format("%06d", new Random().nextInt(999999));
        resetCodes.put(email + "|" + ownerProjectLinkId, code);

        // Not SQL: send email
        String html = """
            <html><body style="font-family: Arial; text-align:center; padding:20px">
            <h2>Reset Your Password</h2>
            <p>Use this code to proceed:</p><h1>%s</h1>
            <p style="color:#777">Expires in 10 minutes.</p></body></html>
        """.formatted(code);
        emailService.sendHtmlEmail(email, "Password Reset Code", html);

        return true;
    }

    public boolean verifyResetCode(String email, String code, Long ownerProjectLinkId) {
        // Not SQL: in-memory validation only
        String key = email + "|" + ownerProjectLinkId;
        return code != null && code.equals(resetCodes.get(key));
    }

    public boolean updatePassword(String email, String code, String newPassword, Long ownerProjectLinkId) {
        if (!verifyResetCode(email, code, ownerProjectLinkId)) return false;

        // SQL:
        //   SELECT * FROM users WHERE email=:email AND aup_id=:ownerProjectLinkId LIMIT 1;
        Users user = findByEmail(email, ownerProjectLinkId);

        if (user == null) return false;

        // UPDATE users SET password_hash=? WHERE user_id=? AND aup_id=? (effectively)
        user.setPasswordHash(passwordEncoder.encode(newPassword));

        // SQL:
        //   UPDATE users SET password_hash=:hash, updated_at=... WHERE user_id=:id;
        userRepository.save(user);

        // Not SQL: cleanup code
        resetCodes.remove(email + "|" + ownerProjectLinkId);
        return true;
    }

    /* =================== Lists / Deletes (tenant) =================== */

    /**
     * ⚠️ Performance note:
     * This uses userRepository.findAll() then filters in-memory.
     *
     * SQL executed:
     *   SELECT * FROM users;
     *
     * Then Java filters by tenant/status/public.
     *
     * Better (future):
     * - create a repository query like:
     *   SELECT * FROM users WHERE aup_id=? AND status='ACTIVE' AND is_public_profile=true;
     */
    public List<UserDto> getAllUserDtos(Long ownerProjectLinkId) {
        var link = linkById(ownerProjectLinkId);

        // SQL:
        //   SELECT * FROM users;
        return userRepository.findAll().stream()
                .filter(u -> u.getOwnerProject() != null && u.getOwnerProject().equals(link))
                .filter(u -> u.getStatus() != null && "ACTIVE".equalsIgnoreCase(u.getStatus().getName()))
                .filter(Users::isPublicProfile)
                .map(UserDto::new)
                .toList();
    }

    public boolean deleteUserById(Long id) {

        // SQL:
        //   SELECT * FROM users WHERE user_id=:id LIMIT 1;
        return userRepository.findById(id)
                .map(u -> {
                    // SQL:
                    //   DELETE FROM users WHERE user_id=:id;
                    userRepository.delete(u);
                    return true;
                })
                .orElse(false);
    }

    public boolean deleteUserByIdWithPassword(Long id, String inputPassword) {

        // SQL:
        //   SELECT * FROM users WHERE user_id=:id LIMIT 1;
        Optional<Users> optionalUser = userRepository.findById(id);

        if (optionalUser.isEmpty()) return false;

        Users user = optionalUser.get();

        // Not SQL: compare hash
        if (!passwordEncoder.matches(inputPassword, user.getPasswordHash())) return false;

        // SQL:
        //   DELETE FROM users WHERE user_id=:id;
        userRepository.delete(user);
        return true;
    }

    /** Save a profile image into /uploads and return its public URL path. (No SQL) */
    public String saveProfileImage(MultipartFile file) throws IOException {
        String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path path = Paths.get("uploads");
        if (!Files.exists(path)) Files.createDirectories(path);
        Files.copy(file.getInputStream(), path.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        return "/uploads/" + filename;
    }

    /** Password hash check (no SQL). */
    public boolean checkPassword(Users user, String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    /* =================== Social login (tenant) =================== */

    public Users handleGoogleUser(String email, String fullName, String pictureUrl, String googleId,
                                  AtomicBoolean wasInactive, AtomicBoolean isNewUser,
                                  Long ownerProjectLinkId) {

        AdminUserProject link = linkById(ownerProjectLinkId);

        // SQL:
        //   SELECT * FROM users WHERE email=:email AND aup_id=:ownerProjectLinkId LIMIT 1;
        Users existingUser = userRepository.findByEmailAndOwnerProject_Id(email, ownerProjectLinkId);

        if (existingUser != null) {

            // If inactive, we update status -> ACTIVE then save
            if (existingUser.getStatus() != null
                    && "INACTIVE".equalsIgnoreCase(existingUser.getStatus().getName())) {

                // SQL:
                //   SELECT * FROM user_status WHERE name='ACTIVE' LIMIT 1;
                existingUser.setStatus(getStatus("ACTIVE"));
                existingUser.setUpdatedAt(LocalDateTime.now());
                wasInactive.set(true);
            }

            existingUser.setLastLogin(LocalDateTime.now());

            // SQL:
            //   UPDATE users SET status_id=?, updated_at=?, last_login=? WHERE user_id=?;
            return userRepository.save(existingUser);
        }

        // Build new user (INSERT)
        String firstName = "Google", lastName = "User";
        if (fullName != null && !fullName.trim().isEmpty()) {
            String[] parts = fullName.trim().split(" ", 2);
            firstName = parts[0];
            if (parts.length > 1) lastName = parts[1];
        }

        Users newUser = new Users();
        newUser.setOwnerProject(link);
        newUser.setEmail(email);
        newUser.setGoogleId(googleId);
        newUser.setUsername(email.split("@")[0]); // ⚠️ could conflict; you may want uniqueness handling
        newUser.setFirstName(firstName);
        newUser.setLastName(lastName);
        newUser.setProfilePictureUrl(pictureUrl);
        newUser.setIsPublicProfile(true);

        // SQL: SELECT * FROM user_status WHERE name='ACTIVE' LIMIT 1;
        newUser.setStatus(getStatus("ACTIVE"));

        // SQL: SELECT * FROM role WHERE LOWER(name)=LOWER('USER') LIMIT 1;
        newUser.setRole(getRoleOrThrow("USER"));

        newUser.setPasswordHash(""); // social users
        newUser.setCreatedAt(LocalDateTime.now());
        newUser.setLastLogin(LocalDateTime.now());

        isNewUser.set(true);

        // SQL:
        //   INSERT INTO users (...) VALUES (...);
        return userRepository.save(newUser);
    }

    public Users handleFacebookUser(String email,
                                    String facebookId,
                                    String firstName,
                                    String lastName,
                                    String picture,
                                    AtomicBoolean wasInactive,
                                    AtomicBoolean isNewUser,
                                    Long ownerProjectLinkId) {

        AdminUserProject link = linkById(ownerProjectLinkId);

        Users user = null;

        try {
            // SQL:
            //   SELECT * FROM users WHERE facebook_id=:facebookId AND aup_id=:ownerProjectLinkId LIMIT 1;
            user = userRepository.findByFacebookIdAndOwnerProject_Id(facebookId, ownerProjectLinkId);
        } catch (Exception ignored) {}

        if (user == null && email != null && !email.isBlank()) {
            // SQL:
            //   SELECT * FROM users WHERE email=:email AND aup_id=:ownerProjectLinkId LIMIT 1;
            user = userRepository.findByEmailAndOwnerProject_Id(email, ownerProjectLinkId);
        }

        if (user != null) {
            if (user.getStatus() != null && "INACTIVE".equalsIgnoreCase(user.getStatus().getName())) {
                // SQL: SELECT * FROM user_status WHERE name='ACTIVE' LIMIT 1;
                user.setStatus(getStatus("ACTIVE"));
                wasInactive.set(true);
            }

            if (user.getFacebookId() == null || !facebookId.equals(user.getFacebookId())) {
                user.setFacebookId(facebookId);
            }

            if (firstName != null && !firstName.isBlank()) user.setFirstName(firstName);
            if (lastName  != null && !lastName.isBlank())  user.setLastName(lastName);
            if (picture   != null && !picture.isBlank())   user.setProfilePictureUrl(picture);

            user.setLastLogin(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());

            // SQL:
            //   UPDATE users SET ... WHERE user_id=?;
            return userRepository.save(user);
        }

        // If no existing user -> create a new one (INSERT)
        String fn = (firstName != null && !firstName.isBlank()) ? firstName : "Facebook";
        String ln = (lastName  != null && !lastName.isBlank())  ? lastName  : "User";

        String baseUsername = (email != null && email.contains("@"))
                ? email.substring(0, email.indexOf('@'))
                : (fn + "." + ln).toLowerCase().replaceAll("[^a-z0-9._-]", "");
        if (baseUsername.isBlank()) baseUsername = "fb.user";

        String candidate = baseUsername.toLowerCase().replaceAll("\\s+", ".");

        // Ensure username unique in this tenant (loop executes multiple EXISTS queries)
        //
        // SQL each check:
        //   SELECT EXISTS(
        //     SELECT 1 FROM users
        //     WHERE LOWER(username)=LOWER(:candidate) AND aup_id=:ownerProjectLinkId
        //   );
        if (userRepository.existsByUsernameIgnoreCaseAndOwnerProject_Id(candidate, ownerProjectLinkId)) {
            int suffix = 1;
            while (userRepository.existsByUsernameIgnoreCaseAndOwnerProject_Id(candidate + suffix, ownerProjectLinkId)) {
                suffix++;
            }
            candidate = candidate + suffix;
        }

        Users newUser = new Users();
        newUser.setOwnerProject(link);
        newUser.setUsername(candidate);
        newUser.setFirstName(fn);
        newUser.setLastName(ln);
        newUser.setEmail(email != null && !email.isBlank() ? email : null);
        newUser.setFacebookId(facebookId);
        newUser.setProfilePictureUrl(picture);
        newUser.setIsPublicProfile(true);

        // SQL: SELECT * FROM user_status WHERE name='ACTIVE' LIMIT 1;
        newUser.setStatus(getStatus("ACTIVE"));

        // SQL: SELECT * FROM role WHERE LOWER(name)=LOWER('USER') LIMIT 1;
        newUser.setRole(getRoleOrThrow("USER"));

        newUser.setPasswordHash("");
        newUser.setCreatedAt(LocalDateTime.now());
        newUser.setLastLogin(LocalDateTime.now());
        newUser.setUpdatedAt(LocalDateTime.now());

        isNewUser.set(true);

        // SQL:
        //   INSERT INTO users (...) VALUES (...);
        return userRepository.save(newUser);
    }

    /* ================= Public list (tenant) ================= */

    /**
     * ⚠️ Same performance note as getAllUserDtos:
     * SQL executed:
     *   SELECT * FROM users;
     * Then Java filters by tenant/status/public.
     */
    public List<Users> getAllUsers(Long ownerProjectLinkId) {
        var link = linkById(ownerProjectLinkId);

        // SQL:
        //   SELECT * FROM users;
        return userRepository.findAll().stream()
                .filter(u -> u.getOwnerProject() != null && u.getOwnerProject().equals(link))
                .filter(u -> u.getStatus() != null && "ACTIVE".equalsIgnoreCase(u.getStatus().getName()))
                .filter(Users::isPublicProfile)
                .toList();
    }

    /* ================ Scheduled cleanups (global) ================ */

    /**
     * Soft-delete after 30 days:
     * - Loads ALL users in memory (SELECT * FROM users)
     * - For each match, updates status to DELETED (UPDATE users ...)
     *
     * Better (future):
     * - one SQL UPDATE with WHERE conditions.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void softDeleteInactiveUsersAfter30Days() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);

        // SQL:
        //   SELECT * FROM users;
        userRepository.findAll().stream()
                .filter(u -> u.getStatus() != null && "INACTIVE".equalsIgnoreCase(u.getStatus().getName()))
                .filter(u -> u.getUpdatedAt() != null && u.getUpdatedAt().isBefore(cutoff))
                .forEach(u -> {
                    // SQL:
                    //   SELECT * FROM user_status WHERE name='DELETED' LIMIT 1;
                    u.setStatus(getStatus("DELETED"));
                    u.setUpdatedAt(LocalDateTime.now());

                    // SQL:
                    //   UPDATE users SET status_id=?, updated_at=? WHERE user_id=?;
                    userRepository.save(u);
                });
    }

    /**
     * Hard-delete after 90 days:
     * - Loads ALL users (SELECT * FROM users)
     * - For each match, deletes row (DELETE FROM users WHERE user_id=?)
     *
     * Better (future):
     * - one SQL DELETE with WHERE conditions.
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void permanentlyDeleteUsersAfter90Days() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);

        // SQL:
        //   SELECT * FROM users;
        userRepository.findAll().stream()
                .filter(u -> u.getStatus() != null && "DELETED".equalsIgnoreCase(u.getStatus().getName()))
                .filter(u -> u.getUpdatedAt() != null && u.getUpdatedAt().isBefore(cutoff))
                // SQL per user:
                //   DELETE FROM users WHERE user_id=?;
                .forEach(userRepository::delete);
    }

    /* ============== Legacy/global convenience methods ============== */
    // These ignore tenant (ownerProjectLinkId). Keep only if you still have old clients.

    public boolean existsByUsername(String username) {
        // SQL:
        //   SELECT EXISTS(SELECT 1 FROM users WHERE LOWER(username)=LOWER(:username));
        return userRepository.existsByUsernameIgnoreCase(username);
    }

    public Optional<Users> findById(Long id) {
        // SQL:
        //   SELECT * FROM users WHERE user_id=:id LIMIT 1;
        return userRepository.findById(id);
    }

    public Users getUserById(Long userId) {
        // SQL:
        //   SELECT * FROM users WHERE user_id=:userId LIMIT 1;
        return userRepository.findById(userId).orElseThrow();
    }

    public Users getUserByEmaill(String identifier) {
        return (identifier != null && identifier.contains("@"))
                ? (
                // SQL:
                //   SELECT * FROM users WHERE email=:identifier LIMIT 1;
                userRepository.findByEmail(identifier)
        )
                : (
                // SQL:
                //   SELECT * FROM users WHERE phone_number=:identifier LIMIT 1;
                userRepository.findByPhoneNumber(identifier)
        );
    }

    public Users findByUsername(String username) {
        // SQL:
        //   SELECT * FROM users WHERE username=:username LIMIT 1;
        return userRepository.findByUsername(username);
    }

    public Users findByEmail(String email) {
        // SQL:
        //   SELECT * FROM users WHERE email=:email LIMIT 1;
        return userRepository.findByEmail(email);
    }

    public Users findByPhoneNumber(String phone) {
        // SQL:
        //   SELECT * FROM users WHERE phone_number=:phone LIMIT 1;
        return userRepository.findByPhoneNumber(phone);
    }

    public Users save(Users user) {
        // SQL:
        //   INSERT or UPDATE users ... (depends if user has id)
        return userRepository.save(user);
    }

    /**
     * Get category names for a user.
     *
     * SQL step 1:
     *   SELECT * FROM users WHERE user_id=:userId LIMIT 1;
     *
     * SQL step 2:
     *   SELECT * FROM UserCategories WHERE user_id=:userId;
     *
     * Then Java maps to category.name via entity relations (may trigger extra SELECTs if lazy).
     */
    public List<String> getUserCategories(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // SQL:
        //   SELECT * FROM UserCategories WHERE user_id=:userId;
        return userCategoriesRepository.findById_User_Id(userId).stream()
                // If Category is LAZY, ui.getId().getCategory().getName() can trigger extra SELECTs.
                .map(ui -> ui.getId().getCategory().getName())
                .toList();
    }

    /**
     * Swap a user's category mapping.
     * This method does:
     * - SELECT user
     * - SELECT old category
     * - SELECT new category by name
     * - EXISTS composite key
     * - DELETE old composite key row
     * - EXISTS new composite key row
     * - INSERT new composite key row (if missing)
     */
    @Transactional
    public boolean updateUserCategory(Long userId, Long oldCategoryId, String newCategoryName) {

        // SQL:
        //   SELECT * FROM users WHERE user_id=:userId LIMIT 1;
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // SQL:
        //   SELECT * FROM categories WHERE category_id=:oldCategoryId LIMIT 1;
        Category oldCategory = categoryRepository.findById(oldCategoryId)
                .orElseThrow(() -> new RuntimeException("Old category not found"));

        // SQL (portable concept):
        //   SELECT * FROM categories WHERE LOWER(name)=LOWER(:newCategoryName) LIMIT 1;
        Category newCategory = categoryRepository.findByNameIgnoreCase(newCategoryName)
                .orElseThrow(() -> new RuntimeException("New category not found"));

        UserCategories.UserCategoryId oldKey = new UserCategories.UserCategoryId(user, oldCategory);

        // SQL:
        //   SELECT EXISTS(
        //     SELECT 1 FROM UserCategories
        //     WHERE user_id=:userId AND category_id=:oldCategoryId
        //   );
        if (!userCategoriesRepository.existsById(oldKey)) return false;

        // SQL:
        //   DELETE FROM UserCategories WHERE user_id=:userId AND category_id=:oldCategoryId;
        userCategoriesRepository.deleteById(oldKey);

        UserCategories.UserCategoryId newKey = new UserCategories.UserCategoryId(user, newCategory);

        // SQL:
        //   SELECT EXISTS(
        //     SELECT 1 FROM UserCategories
        //     WHERE user_id=:userId AND category_id=:newCategoryId
        //   );
        if (!userCategoriesRepository.existsById(newKey)) {
            UserCategories newUserCategory = new UserCategories();
            newUserCategory.setId(newKey);
            newUserCategory.setCategory(newCategory);

            // SQL:
            //   INSERT INTO UserCategories (user_id, category_id, created_at, updated_at) VALUES (...);
            userCategoriesRepository.save(newUserCategory);
        }
        return true;
    }

    @Transactional
    public boolean deleteUserCategory(Long userId, Long categoryId) {

        // SQL:
        //   SELECT * FROM users WHERE user_id=:userId LIMIT 1;
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // SQL:
        //   SELECT * FROM categories WHERE category_id=:categoryId LIMIT 1;
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("Category not found"));

        UserCategories.UserCategoryId key = new UserCategories.UserCategoryId(user, category);

        // SQL:
        //   SELECT EXISTS(
        //     SELECT 1 FROM UserCategories WHERE user_id=:userId AND category_id=:categoryId
        //   );
        if (userCategoriesRepository.existsById(key)) {

            // SQL:
            //   DELETE FROM UserCategories WHERE user_id=:userId AND category_id=:categoryId;
            userCategoriesRepository.deleteById(key);
            return true;
        }
        return false;
    }

    @Transactional
    public void replaceUserCategories(Long userId, List<Long> newCategoryIds) {

        // SQL:
        //   SELECT * FROM users WHERE user_id=:userId LIMIT 1;
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // SQL:
        //   SELECT * FROM UserCategories WHERE user_id=:userId;
        List<UserCategories> existing = userCategoriesRepository.findById_User_Id(userId);

        // SQL (could be multiple deletes or batch depending on JPA settings):
        //   DELETE FROM UserCategories WHERE user_id=:userId;  (conceptually)
        userCategoriesRepository.deleteAll(existing);

        for (Long categoryId : newCategoryIds) {

            // SQL:
            //   SELECT * FROM categories WHERE category_id=:categoryId LIMIT 1;
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new RuntimeException("Category not found"));

            UserCategories.UserCategoryId compositeKey = new UserCategories.UserCategoryId(user, category);

            // SQL:
            //   SELECT EXISTS(
            //     SELECT 1 FROM UserCategories WHERE user_id=:userId AND category_id=:categoryId
            //   );
            if (!userCategoriesRepository.existsById(compositeKey)) {
                UserCategories userCategory = new UserCategories();
                userCategory.setId(compositeKey);
                userCategory.setCategory(category);

                // SQL:
                //   INSERT INTO UserCategories (user_id, category_id, created_at, updated_at) VALUES (...);
                userCategoriesRepository.save(userCategory);
            }
        }
    }

    /**
     * Suggest friends by shared categories.
     *
     * SQL pattern executed by repos:
     * 1) SELECT user (users)
     * 2) SELECT user categories (UserCategories)
     * 3) SELECT UserCategories where category_id IN (...)
     *
     * ⚠️ This approach can load a lot into memory.
     * Better: one JOIN query returning distinct users in SQL.
     */
    public List<Users> suggestFriendsByCategory(Long userId) {

        // SQL:
        //   SELECT * FROM users WHERE user_id=:userId LIMIT 1;
        Users currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // SQL:
        //   SELECT * FROM UserCategories WHERE user_id=:userId;
        List<Long> myCategoryIds = userCategoriesRepository.findById_User_Id(userId).stream()
                .map(ui -> ui.getId().getCategory().getId())
                .toList();

        if (myCategoryIds.isEmpty()) return List.of();

        // SQL:
        //   SELECT * FROM UserCategories WHERE category_id IN (:myCategoryIds);
        List<UserCategories> shared = userCategoriesRepository.findByCategory_IdIn(myCategoryIds);

        // In-memory filtering (no SQL)
        return shared.stream()
                .map(ui -> ui.getId().getUser())
                .filter(u -> !Objects.equals(u.getId(), userId))
                .filter(u -> u.getStatus() != null && "ACTIVE".equalsIgnoreCase(u.getStatus().getName()))
                .filter(Users::isPublicProfile)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Update visibility + status.
     *
     * SQL:
     * 1) SELECT * FROM users WHERE user_id=:userId LIMIT 1;
     * 2) UPDATE users SET is_public_profile=?, status_id=?, updated_at=? WHERE user_id=?;
     */
    public boolean updateVisibilityAndStatus(Long userId, boolean isPublicProfile, UserStatus newStatus) {

        // SQL:
        //   SELECT * FROM users WHERE user_id=:userId LIMIT 1;
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setIsPublicProfile(isPublicProfile);
        user.setStatus(newStatus);
        user.setUpdatedAt(LocalDateTime.now());

        // SQL:
        //   UPDATE users SET is_public_profile=?, status_id=?, updated_at=? WHERE user_id=?;
        userRepository.save(user);

        return true;
    }

    @Transactional
    public void addUserCategories(Long userId, List<Long> categoryIds) {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (categoryIds == null || categoryIds.isEmpty()) return;

        // SQL:
        //   SELECT * FROM users WHERE user_id=:userId LIMIT 1;
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // SQL:
        //   SELECT * FROM categories WHERE category_id IN (:categoryIds);
        List<Category> cats = categoryRepository.findAllById(categoryIds);

        // Java validation only
        if (cats.size() != new HashSet<>(categoryIds).size()) {
            throw new RuntimeException("One or more categories not found");
        }

        for (Category c : cats) {
            UserCategories.UserCategoryId key = new UserCategories.UserCategoryId(user, c);

            // SQL:
            //   SELECT EXISTS(
            //     SELECT 1 FROM UserCategories WHERE user_id=:userId AND category_id=:c.id
            //   );
            if (!userCategoriesRepository.existsById(key)) {

                UserCategories uc = new UserCategories();
                uc.setId(key);
                uc.setCategory(c);

                // SQL:
                //   INSERT INTO UserCategories (user_id, category_id, created_at, updated_at) VALUES (...);
                userCategoriesRepository.save(uc);
            }
        }
    }

    /**
     * Delete user profile image:
     * - DB: read user row
     * - FS: delete file from uploads
     * - DB: update profile_picture_url = NULL
     */
    public boolean deleteUserProfileImage(Long userId) {
        if (userId == null) throw new IllegalArgumentException("userId is required");

        // SQL:
        //   SELECT * FROM users WHERE user_id=:userId LIMIT 1;
        Optional<Users> opt = userRepository.findById(userId);
        if (opt.isEmpty()) return false;

        Users user = opt.get();
        String url = user.getProfilePictureUrl();
        if (url == null || url.isBlank()) return false;

        // File system delete (not SQL)
        try {
            if (url.startsWith("/uploads/")) {
                String fileName = url.substring("/uploads/".length()).replace("\\", "/");
                if (!fileName.isBlank()) {
                    Path uploads = Paths.get("uploads");
                    Path filePath = uploads.resolve(fileName).normalize();

                    // Security: prevent path traversal
                    if (filePath.startsWith(uploads.toAbsolutePath()) || filePath.startsWith(uploads)) {
                        Files.deleteIfExists(filePath);
                    }
                }
            }
        } catch (Exception ignore) {
            // If file delete fails, we still clear DB field.
        }

        user.setProfilePictureUrl(null);
        user.setUpdatedAt(LocalDateTime.now());

        // SQL:
        //   UPDATE users SET profile_picture_url=NULL, updated_at=? WHERE user_id=?;
        userRepository.save(user);

        return true;
    }
}
