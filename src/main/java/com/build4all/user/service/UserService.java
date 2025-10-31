package com.build4all.user.service;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.catalog.domain.Category;
import com.build4all.catalog.repository.CategoryRepository;
import com.build4all.notifications.service.EmailService;
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

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * UserService (tenant-aware)
 * - All main flows are scoped by ownerProjectLinkId (AdminUserProject.id)
 * - Legacy/global helpers kept for backward compatibility with old controllers/clients
 */
@Service
public class UserService {

    @Autowired private UsersRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserCategoriesRepository userCategoriesRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private PendingUserRepository pendingUserRepository;
    @Autowired private UserStatusRepository userStatusRepository;

    /* Tenant link repository */
    @Autowired private AdminUserProjectRepository aupRepo;

    private final EmailService emailService;
    public UserService(EmailService emailService) { this.emailService = emailService; }

    /* ============================ Helpers ============================ */

    /** Resolve a UserStatus by name (throws if missing). */
    public UserStatus getStatus(String name) {
        return userStatusRepository.findByName(name.toUpperCase())
            .orElseThrow(() -> new RuntimeException("UserStatus " + name + " not found"));
    }

    /** Ensure the tenant link exists and return it. */
    private AdminUserProject linkById(Long ownerProjectLinkId) {
        if (ownerProjectLinkId == null) throw new IllegalArgumentException("ownerProjectLinkId is required");
        return aupRepo.findById(ownerProjectLinkId)
                .orElseThrow(() -> new RuntimeException("AdminUserProject not found: " + ownerProjectLinkId));
    }

    /* ====================== Registration (tenant) ===================== */

    /**
     * Start registration:
     * - Checks tenant-scoped email/phone uniqueness
     * - Creates/updates a PendingUser with a verification code
     * - Sends email code when email is provided
     */
    public boolean sendVerificationCodeForRegistration(Map<String, String> userData, Long ownerProjectLinkId) {
        linkById(ownerProjectLinkId); // validate tenant

        String email = userData.get("email");
        String phone = userData.get("phoneNumber");
        String password = userData.get("password");

        boolean emailProvided = email != null && !email.isBlank();
        boolean phoneProvided = phone != null && !phone.isBlank();
        if (!emailProvided && !phoneProvided) throw new IllegalArgumentException("Provide email or phone");
        if (emailProvided && phoneProvided)    throw new IllegalArgumentException("Provide only one: email OR phone");

        // tenant-scoped uniqueness
        if (emailProvided && userRepository.existsByEmailAndOwnerProject_Id(email, ownerProjectLinkId))
            throw new IllegalArgumentException("Email already in use in this app");
        if (phoneProvided && userRepository.existsByPhoneNumberAndOwnerProject_Id(phone, ownerProjectLinkId))
            throw new IllegalArgumentException("Phone already in use in this app");

        String statusStr = Optional.ofNullable(userData.get("status")).orElse("PENDING");
        UserStatus status = getStatus(statusStr);

        PendingUser pending = emailProvided
                ? pendingUserRepository.findByEmail(email)
                : pendingUserRepository.findByPhoneNumber(phone);

        String code = phoneProvided ? "123456" : String.format("%06d", new Random().nextInt(999999));

        if (pending != null) {
            if (Boolean.TRUE.equals(pending.isVerified())) return true; // already verified
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

    /**
     * Resend verification code (email or phone).
     * For SMS we keep a fixed code "123456" for testing/dev.
     */
    public boolean resendVerificationCode(String emailOrPhone) {
        boolean isEmail = emailOrPhone != null && emailOrPhone.contains("@");
        PendingUser pending = isEmail
                ? pendingUserRepository.findByEmail(emailOrPhone)
                : pendingUserRepository.findByPhoneNumber(emailOrPhone);
        if (pending == null) throw new RuntimeException("No pending user found");

        if (isEmail) {
            String code = String.format("%06d", new Random().nextInt(999999));
            pending.setVerificationCode(code);
            pending.setCreatedAt(LocalDateTime.now());
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
            pending.setVerificationCode("123456"); // SMS/dev path
            pending.setCreatedAt(LocalDateTime.now());
            pendingUserRepository.save(pending);
        }
        return true;
    }

    public Long verifyEmailCodeAndRegister(String email, String code) {
        PendingUser pending = pendingUserRepository.findByEmail(email);
        if (pending == null) throw new RuntimeException("No pending user for this email");
        if (!Objects.equals(pending.getVerificationCode(), code))
            throw new RuntimeException("Invalid code");
        pending.setIsVerified(true);
        pendingUserRepository.save(pending);
        return pending.getId();
    }

    public Long verifyPhoneCodeAndRegister(String phoneNumber, String code) {
        if (!"123456".equals(code)) throw new RuntimeException("Invalid code");
        PendingUser pending = pendingUserRepository.findByPhoneNumber(phoneNumber);
        if (pending == null) throw new RuntimeException("Pending user not found");
        pending.setIsVerified(true);
        pendingUserRepository.save(pending);
        return pending.getId();
    }

    /**
     * Finish registration: create the real user in the right tenant.
     */
    @Transactional
    public boolean completeUserProfile(Long pendingId,
                                       String username,
                                       String firstName,
                                       String lastName,
                                       MultipartFile profileImage,
                                       Boolean isPublicProfile,
                                       Long ownerProjectLinkId) throws IOException {

        AdminUserProject link = linkById(ownerProjectLinkId);
        PendingUser pending = pendingUserRepository.findById(pendingId)
                .orElseThrow(() -> new RuntimeException("Pending user not found."));

        if (userRepository.existsByUsernameIgnoreCaseAndOwnerProject_Id(username, ownerProjectLinkId)) {
            throw new RuntimeException("Username already in use in this app.");
        }

        Users user = new Users();
        user.setOwnerProject(link);
        user.setUsername(username);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPasswordHash(pending.getPasswordHash());
        user.setStatus(getStatus("ACTIVE"));
        user.setCreatedAt(LocalDateTime.now());
        if (pending.getEmail() != null) user.setEmail(pending.getEmail());
        if (pending.getPhoneNumber() != null) user.setPhoneNumber(pending.getPhoneNumber());
        user.setIsPublicProfile(isPublicProfile != null ? isPublicProfile : true);

        if (profileImage != null && !profileImage.isEmpty()) {
            String filename = UUID.randomUUID() + "_" + profileImage.getOriginalFilename();
            Path path = Paths.get("uploads");
            if (!Files.exists(path)) Files.createDirectories(path);
            Files.copy(profileImage.getInputStream(), path.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            user.setProfilePictureUrl("/uploads/" + filename);
        }

        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        pendingUserRepository.delete(pending);
        return true;
    }

    /* ====================== Lookups (tenant) ====================== */

    public Users findByEmail(String email, Long ownerProjectLinkId) {
        return userRepository.findByEmailAndOwnerProject_Id(email, ownerProjectLinkId);
    }
    public Users findByPhoneNumber(String phone, Long ownerProjectLinkId) {
        return userRepository.findByPhoneNumberAndOwnerProject_Id(phone, ownerProjectLinkId);
    }
    public Users findByUsername(String username, Long ownerProjectLinkId) {
        return userRepository.findByUsernameAndOwnerProject_Id(username, ownerProjectLinkId);
    }
    public Users getUserByEmaill(String identifier, Long ownerProjectLinkId) {
        Users user = (identifier != null && identifier.contains("@"))
                ? userRepository.findByEmailAndOwnerProject_Id(identifier, ownerProjectLinkId)
                : userRepository.findByPhoneNumberAndOwnerProject_Id(identifier, ownerProjectLinkId);
        if (user == null) throw new RuntimeException("User not found in this app: " + identifier);
        return user;
    }

    /** Tenant-safe get by ID (uses fetch-join to avoid lazy issues in DTOs). */
    public Users getUserById(Long userId, Long ownerProjectLinkId) {
        if (userId == null || ownerProjectLinkId == null) {
            throw new IllegalArgumentException("userId and ownerProjectLinkId are required");
        }
        return userRepository.fetchByIdAndOwnerProjectId(userId, ownerProjectLinkId)
                .or(() -> userRepository.findByPkAndAupId(userId, ownerProjectLinkId))
                .orElseThrow(() -> new RuntimeException(
                        "User not found in this app: id=" + userId + ", ownerProjectLinkId=" + ownerProjectLinkId));
    }

    /** Tenant-safe DTO fetch. */
    @Transactional(readOnly = true)
    public UserDto getUserDtoByIdAndOwnerProject(Long userId, Long ownerProjectLinkId) {
        Users u = userRepository.fetchByIdAndOwnerProjectId(userId, ownerProjectLinkId)
            .orElseThrow(() -> new RuntimeException(
                "User not found in this app: id=" + userId + ", ownerProjectLinkId=" + ownerProjectLinkId));
        return new UserDto(u);
    }

    /* ==================== Password reset (tenant) ==================== */

    private final Map<String, String> resetCodes = new ConcurrentHashMap<>();

    public boolean resetPassword(String email, Long ownerProjectLinkId) {
        Users user = userRepository.findByEmailAndOwnerProject_Id(email, ownerProjectLinkId);
        if (user == null) return false;

        String code = String.format("%06d", new Random().nextInt(999999));
        resetCodes.put(email + "|" + ownerProjectLinkId, code);

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
        String key = email + "|" + ownerProjectLinkId;
        return code != null && code.equals(resetCodes.get(key));
    }

    public boolean updatePassword(String email, String code, String newPassword, Long ownerProjectLinkId) {
        if (!verifyResetCode(email, code, ownerProjectLinkId)) return false;
        Users user = findByEmail(email, ownerProjectLinkId);
        if (user == null) return false;
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        resetCodes.remove(email + "|" + ownerProjectLinkId);
        return true;
    }

    /* =================== Lists / Deletes (tenant) =================== */

    /** Public profiles (ACTIVE) within tenant as DTOs. */
    public List<UserDto> getAllUserDtos(Long ownerProjectLinkId) {
        var link = linkById(ownerProjectLinkId);
        return userRepository.findAll().stream()
            .filter(u -> u.getOwnerProject()!=null && u.getOwnerProject().equals(link))
            .filter(u -> u.getStatus()!=null && "ACTIVE".equalsIgnoreCase(u.getStatus().getName()))
            .filter(Users::isPublicProfile)
            .map(UserDto::new)
            .toList();
    }

    public boolean deleteUserById(Long id) {
        return userRepository.findById(id)
                .map(u -> { userRepository.delete(u); return true; })
                .orElse(false);
    }

    /** Self-delete with password confirmation. */
    public boolean deleteUserByIdWithPassword(Long id, String inputPassword) {
        Optional<Users> optionalUser = userRepository.findById(id);
        if (optionalUser.isEmpty()) return false;
        Users user = optionalUser.get();
        if (!passwordEncoder.matches(inputPassword, user.getPasswordHash())) return false;

        userRepository.delete(user);
        return true;
    }

    /** Save a profile image into /uploads and return its public URL path. */
    public String saveProfileImage(MultipartFile file) throws IOException {
        String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path path = Paths.get("uploads");
        if (!Files.exists(path)) Files.createDirectories(path);
        Files.copy(file.getInputStream(), path.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        return "/uploads/" + filename;
    }

    public boolean checkPassword(Users user, String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    /* =================== Social login (tenant) =================== */

    public Users handleGoogleUser(String email, String fullName, String pictureUrl, String googleId,
                                  AtomicBoolean wasInactive, AtomicBoolean isNewUser,
                                  Long ownerProjectLinkId) {

        AdminUserProject link = linkById(ownerProjectLinkId);

        Users existingUser = userRepository.findByEmailAndOwnerProject_Id(email, ownerProjectLinkId);
        if (existingUser != null) {
            if (existingUser.getStatus()!=null && "INACTIVE".equalsIgnoreCase(existingUser.getStatus().getName())) {
                existingUser.setStatus(getStatus("ACTIVE"));
                existingUser.setUpdatedAt(LocalDateTime.now());
                wasInactive.set(true);
            }
            existingUser.setLastLogin(LocalDateTime.now());
            return userRepository.save(existingUser);
        }

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
        newUser.setUsername(email.split("@")[0]);
        newUser.setFirstName(firstName);
        newUser.setLastName(lastName);
        newUser.setProfilePictureUrl(pictureUrl);
        newUser.setIsPublicProfile(true);
        newUser.setStatus(getStatus("ACTIVE"));
        newUser.setPasswordHash("");
        newUser.setCreatedAt(LocalDateTime.now());
        newUser.setLastLogin(LocalDateTime.now());

        isNewUser.set(true);
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
        try { user = userRepository.findByFacebookIdAndOwnerProject_Id(facebookId, ownerProjectLinkId); }
        catch (Exception ignored) {}

        if (user == null && email != null && !email.isBlank()) {
            user = userRepository.findByEmailAndOwnerProject_Id(email, ownerProjectLinkId);
        }

        if (user != null) {
            if (user.getStatus()!=null && "INACTIVE".equalsIgnoreCase(user.getStatus().getName())) {
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
            return userRepository.save(user);
        }

        String fn = (firstName != null && !firstName.isBlank()) ? firstName : "Facebook";
        String ln = (lastName  != null && !lastName.isBlank())  ? lastName  : "User";

        String baseUsername = (email != null && email.contains("@"))
                ? email.substring(0, email.indexOf('@'))
                : (fn + "." + ln).toLowerCase().replaceAll("[^a-z0-9._-]", "");
        if (baseUsername.isBlank()) baseUsername = "fb.user";

        String candidate = baseUsername.toLowerCase().replaceAll("\\s+",".");

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
        newUser.setStatus(getStatus("ACTIVE"));
        newUser.setPasswordHash("");
        newUser.setCreatedAt(LocalDateTime.now());
        newUser.setLastLogin(LocalDateTime.now());
        newUser.setUpdatedAt(LocalDateTime.now());

        isNewUser.set(true);
        return userRepository.save(newUser);
    }

    /* ================= Public list (tenant) ================= */

    public List<Users> getAllUsers(Long ownerProjectLinkId) {
        var link = linkById(ownerProjectLinkId);
        return userRepository.findAll().stream()
            .filter(u -> u.getOwnerProject()!=null && u.getOwnerProject().equals(link))
            .filter(u -> u.getStatus()!=null && "ACTIVE".equalsIgnoreCase(u.getStatus().getName()))
            .filter(Users::isPublicProfile)
            .toList();
    }

    /* ================ Scheduled cleanups (global) ================ */

    @Scheduled(cron = "0 0 2 * * *")
    public void softDeleteInactiveUsersAfter30Days() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        userRepository.findAll().stream()
            .filter(u -> u.getStatus()!=null && "INACTIVE".equalsIgnoreCase(u.getStatus().getName()))
            .filter(u -> u.getUpdatedAt() != null && u.getUpdatedAt().isBefore(cutoff))
            .forEach(u -> {
                u.setStatus(getStatus("DELETED"));
                u.setUpdatedAt(LocalDateTime.now());
                userRepository.save(u);
            });
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void permanentlyDeleteUsersAfter90Days() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        userRepository.findAll().stream()
            .filter(u -> u.getStatus()!=null && "DELETED".equalsIgnoreCase(u.getStatus().getName()))
            .filter(u -> u.getUpdatedAt() != null && u.getUpdatedAt().isBefore(cutoff))
            .forEach(userRepository::delete);
    }

    /* ============== Legacy/global convenience methods ============== */

    public boolean existsByUsername(String username) { return userRepository.existsByUsernameIgnoreCase(username); }
    public Optional<Users> findById(Long id) { return userRepository.findById(id); }
    public Users getUserById(Long userId) { return userRepository.findById(userId).orElseThrow(); }
    public Users getUserByEmaill(String identifier) {
        return (identifier!=null && identifier.contains("@")) ? userRepository.findByEmail(identifier)
                                                              : userRepository.findByPhoneNumber(identifier);
    }
    public Users findByUsername(String username) { return userRepository.findByUsername(username); }
    public Users findByEmail(String email) { return userRepository.findByEmail(email); }
    public Users findByPhoneNumber(String phone) { return userRepository.findByPhoneNumber(phone); }
    public Users save(Users user) { return userRepository.save(user); }

    /** Simple string list of the user's categories (legacy/global). */
    public List<String> getUserCategories(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return userCategoriesRepository.findById_User_Id(userId).stream()
                .map(ui -> ui.getId().getCategory().getName())
                .toList();
    }

    /** Swap a user's category mapping (legacy/global). */
    @Transactional
    public boolean updateUserCategory(Long userId, Long oldCategoryId, String newCategoryName) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Category oldCategory = categoryRepository.findById(oldCategoryId)
                .orElseThrow(() -> new RuntimeException("Old category not found"));
        Category newCategory = categoryRepository.findByNameIgnoreCase(newCategoryName)
                .orElseThrow(() -> new RuntimeException("New category not found"));

        UserCategories.UserCategoryId oldKey = new UserCategories.UserCategoryId(user, oldCategory);
        if (!userCategoriesRepository.existsById(oldKey)) return false;

        userCategoriesRepository.deleteById(oldKey);

        UserCategories.UserCategoryId newKey = new UserCategories.UserCategoryId(user, newCategory);
        if (!userCategoriesRepository.existsById(newKey)) {
            UserCategories newUserCategory = new UserCategories();
            newUserCategory.setId(newKey);
            newUserCategory.setCategory(newCategory);
            userCategoriesRepository.save(newUserCategory);
        }
        return true;
    }

    /** Remove a category mapping (legacy/global). */
    @Transactional
    public boolean deleteUserCategory(Long userId, Long categoryId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("Category not found"));

        UserCategories.UserCategoryId key = new UserCategories.UserCategoryId(user, category);
        if (userCategoriesRepository.existsById(key)) {
            userCategoriesRepository.deleteById(key);
            return true;
        }
        return false;
    }

    /** Replace all user's categories (legacy/global). */
    @Transactional
    public void replaceUserCategories(Long userId, List<Long> newCategoryIds) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<UserCategories> existing = userCategoriesRepository.findById_User_Id(userId);
        userCategoriesRepository.deleteAll(existing);

        for (Long categoryId : newCategoryIds) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new RuntimeException("Category not found"));

            UserCategories.UserCategoryId compositeKey = new UserCategories.UserCategoryId(user, category);
            if (!userCategoriesRepository.existsById(compositeKey)) {
                UserCategories userCategory = new UserCategories();
                userCategory.setId(compositeKey);
                userCategory.setCategory(category);
                userCategoriesRepository.save(userCategory);
            }
        }
    }

    /* -------- Optional: simple friend suggestions by shared categories (legacy/global) -------- */
    public List<Users> suggestFriendsByCategory(Long userId) {
        Users currentUser = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        List<Long> myCategoryIds = userCategoriesRepository.findById_User_Id(userId).stream()
            .map(ui -> ui.getId().getCategory().getId())
            .toList();

        if (myCategoryIds.isEmpty()) return List.of();

        List<UserCategories> shared = userCategoriesRepository.findByCategory_IdIn(myCategoryIds);

        return shared.stream()
            .map(ui -> ui.getId().getUser())
            .filter(u -> !Objects.equals(u.getId(), userId))
            .filter(u -> u.getStatus()!=null && "ACTIVE".equalsIgnoreCase(u.getStatus().getName()))
            .filter(Users::isPublicProfile)
            .distinct()
            .collect(Collectors.toList());
    }

    /* -------- Visibility + status together (legacy/global) -------- */
    public boolean updateVisibilityAndStatus(Long userId, boolean isPublicProfile, UserStatus newStatus) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setIsPublicProfile(isPublicProfile);
        user.setStatus(newStatus);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return true;
    }
    

    @Transactional
    public void addUserCategories(Long userId, List<Long> categoryIds) {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (categoryIds == null || categoryIds.isEmpty()) return; // nothing to add

        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Load all requested categories and validate they exist
        List<Category> cats = categoryRepository.findAllById(categoryIds);
        if (cats.size() != new HashSet<>(categoryIds).size()) {
            throw new RuntimeException("One or more categories not found");
        }

        // Add missing links only
        for (Category c : cats) {
            UserCategories.UserCategoryId key = new UserCategories.UserCategoryId(user, c);
            if (!userCategoriesRepository.existsById(key)) {
                UserCategories uc = new UserCategories();
                uc.setId(key);
                uc.setCategory(c);
                userCategoriesRepository.save(uc);
            }
        }
    }
    
 // UserService.java

    public boolean deleteUserProfileImage(Long userId) {
        if (userId == null) throw new IllegalArgumentException("userId is required");

        Optional<Users> opt = userRepository.findById(userId);
        if (opt.isEmpty()) return false;

        Users user = opt.get();
        String url = user.getProfilePictureUrl();

        if (url == null || url.isBlank()) return false; // nothing to delete

        // Only delete local files we manage (security/safety)
        // Expecting "/uploads/<filename>"
        try {
            if (url.startsWith("/uploads/")) {
                String fileName = url.substring("/uploads/".length()).replace("\\", "/");
                if (!fileName.isBlank()) {
                    Path uploads = Paths.get("uploads");
                    Path filePath = uploads.resolve(fileName).normalize();
                    // Ensure we don't escape the uploads dir
                    if (filePath.startsWith(uploads.toAbsolutePath()) || filePath.startsWith(uploads)) {
                        Files.deleteIfExists(filePath);
                    }
                }
            }
        } catch (Exception ignore) {
            // We still proceed to null the DB field even if the physical delete failed
            // (e.g., file already gone). Swallowing is acceptable for this legacy route.
        }

        user.setProfilePictureUrl(null);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        return true;
    }

}