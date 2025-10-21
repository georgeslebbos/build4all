package com.build4all.user.service;

import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.repository.AdminUsersRepository;
import com.build4all.admin.service.AdminUserService;
import com.build4all.catalog.domain.Category;
import com.build4all.catalog.repository.CategoryRepository;
import com.build4all.notifications.service.EmailService;
import com.build4all.project.domain.Project;
import com.build4all.project.repository.ProjectRepository;
import com.build4all.social.service.FriendshipService;
import com.build4all.user.domain.*;
import com.build4all.user.dto.UserDto;
import com.build4all.user.repository.*;
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

@Service
public class UserService {

    @Autowired private UsersRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserCategoriesRepository userCategoriesRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private PendingUserRepository pendingUserRepository;
    @Autowired private FriendshipService friendshipService;
    @Autowired private AdminUserService adminUserService;
    @Autowired private UserStatusRepository userStatusRepository;

    // app-scoped infra
    @Autowired private AdminUsersRepository adminUsersRepository;
    @Autowired private ProjectRepository projectRepository;

    @Autowired
    private final EmailService emailService;

    public UserService(EmailService emailService) { this.emailService = emailService; }

    /* -------------------- Status helper -------------------- */
    public UserStatus getStatus(String name) {
        return userStatusRepository.findByName(name.toUpperCase())
            .orElseThrow(() -> new RuntimeException("UserStatus " + name + " not found"));
    }

    /* =========================================================
       NEW: REGISTRATION (APP-SCOPED) — require adminId & projectId
       ========================================================= */
    public boolean sendVerificationCodeForRegistration(Map<String, String> userData, Long adminId, Long projectId) {
        String email = userData.get("email");
        String phone = userData.get("phoneNumber");
        String password = userData.get("password");

        if (adminId == null || projectId == null)
            throw new RuntimeException("adminId and projectId are required");

        boolean emailProvided = email != null && !email.isBlank();
        boolean phoneProvided = phone != null && !phone.isBlank();
        if (!emailProvided && !phoneProvided) throw new IllegalArgumentException("Provide email or phone");
        if (emailProvided && phoneProvided)    throw new IllegalArgumentException("Provide only one: email OR phone");

        // per-app uniqueness
        if (emailProvided && userRepository.existsByEmailAndOwner_AdminIdAndProject_Id(email, adminId, projectId))
            throw new IllegalArgumentException("Email already in use in this app");
        if (phoneProvided && userRepository.existsByPhoneNumberAndOwner_AdminIdAndProject_Id(phone, adminId, projectId))
            throw new IllegalArgumentException("Phone already in use in this app");

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

    @Transactional
    public boolean completeUserProfile(Long pendingId,
                                       String username,
                                       String firstName,
                                       String lastName,
                                       MultipartFile profileImage,
                                       Boolean isPublicProfile,
                                       Long adminId,
                                       Long projectId) throws IOException {
        if (adminId == null || projectId == null)
            throw new RuntimeException("adminId and projectId are required");

        PendingUser pending = pendingUserRepository.findById(pendingId)
                .orElseThrow(() -> new RuntimeException("Pending user not found."));

        if (userRepository.existsByUsernameIgnoreCaseAndOwner_AdminIdAndProject_Id(username, adminId, projectId)) {
            throw new RuntimeException("Username already in use in this app.");
        }

        AdminUser owner = adminUsersRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Owner not found: " + adminId));
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

        Users user = new Users();
        user.setOwner(owner);
        user.setProject(project);
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

    /* =========================================================
       LOOKUPS – APP-SCOPED
       ========================================================= */
    public Users findByEmail(String email, Long adminId, Long projectId) {
        return userRepository.findByEmailAndOwner_AdminIdAndProject_Id(email, adminId, projectId);
    }
    public Users findByPhoneNumber(String phone, Long adminId, Long projectId) {
        return userRepository.findByPhoneNumberAndOwner_AdminIdAndProject_Id(phone, adminId, projectId);
    }
    public Users findByUsername(String username, Long adminId, Long projectId) {
        return userRepository.findByUsernameAndOwner_AdminIdAndProject_Id(username, adminId, projectId);
    }
    public Users getUserByEmaill(String identifier, Long adminId, Long projectId) {
        Users user = identifier != null && identifier.contains("@")
                ? findByEmail(identifier, adminId, projectId)
                : findByPhoneNumber(identifier, adminId, projectId);
        if (user == null) throw new RuntimeException("User not found in this app: " + identifier);
        return user;
    }
    public Users getUserById(Long userId, Long adminId, Long projectId) {
        return userRepository.findByIdAndOwner_AdminIdAndProject_Id(userId, adminId, projectId)
                .orElseThrow(() -> new RuntimeException("User not found in this app: " + userId));
    }

    /* =========================================================
       PASSWORD RESET – APP-SCOPED
       ========================================================= */
    private final Map<String, String> resetCodes = new ConcurrentHashMap<>();

    public boolean resetPassword(String email, Long adminId, Long projectId) {
        Users user = findByEmail(email, adminId, projectId);
        if (user == null) return false;

        String code = String.format("%06d", new Random().nextInt(999999));
        resetCodes.put(email + "|" + adminId + "|" + projectId, code);

        String html = """
            <html><body style="font-family: Arial; text-align:center; padding:20px">
            <h2>Reset Your Password</h2>
            <p>Use this code to proceed:</p><h1>%s</h1>
            <p style="color:#777">Expires in 10 minutes.</p></body></html>
        """.formatted(code);
        emailService.sendHtmlEmail(email, "Password Reset Code", html);
        return true;
    }
    public boolean verifyResetCode(String email, String code, Long adminId, Long projectId) {
        String key = email + "|" + adminId + "|" + projectId;
        return code != null && code.equals(resetCodes.get(key));
    }
    public boolean updatePassword(String email, String code, String newPassword, Long adminId, Long projectId) {
        if (!verifyResetCode(email, code, adminId, projectId)) return false;
        Users user = findByEmail(email, adminId, projectId);
        if (user == null) return false;
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        resetCodes.remove(email + "|" + adminId + "|" + projectId);
        return true;
    }

    /* =========================================================
       LISTS / DELETES (app-filtered)
       ========================================================= */
    public List<UserDto> getAllUserDtos(Long adminId, Long projectId) {
        return userRepository.findAll().stream()
            .filter(u -> u.getOwner()!=null && u.getProject()!=null
                      && Objects.equals(u.getOwner().getAdminId(), adminId)
                      && Objects.equals(u.getProject().getId(), projectId))
            .filter(u -> "ACTIVE".equals(u.getStatus().getName()))
            .filter(Users::isPublicProfile)
            .map(UserDto::new)
            .toList();
    }

    public boolean deleteUserById(Long id) {
        return userRepository.findById(id).map(u -> { userRepository.delete(u); return true; }).orElse(false);
    }

    public boolean deleteUserByIdWithPassword(Long id, String inputPassword) {
        Optional<Users> optionalUser = userRepository.findById(id);
        if (optionalUser.isEmpty()) return false;
        Users user = optionalUser.get();
        if (!passwordEncoder.matches(inputPassword, user.getPasswordHash())) return false;

        if (user.getEmail() != null) {
            List<AdminUser> admins = adminUserService.findAllByUserEmail(user.getEmail());
            for (AdminUser admin : admins) adminUserService.deleteManagerById(admin.getAdminId());
        }
        userRepository.delete(user);
        return true;
    }

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

    /* =========================================================
       SOCIAL LOGIN – APP-SCOPED
       ========================================================= */
    public Users handleGoogleUser(String email, String fullName, String pictureUrl, String googleId,
                                  AtomicBoolean wasInactive, AtomicBoolean isNewUser,
                                  Long adminId, Long projectId) {
        Users existingUser = findByEmail(email, adminId, projectId);
        if (existingUser != null) {
            if ("INACTIVE".equals(existingUser.getStatus().getName())) {
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

        AdminUser owner = adminUsersRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Owner not found: " + adminId));
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

        Users newUser = new Users();
        newUser.setOwner(owner);
        newUser.setProject(project);
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
                                    Long adminId, Long projectId) {

        Users user = null;
        try { user = userRepository.findByFacebookIdAndOwner_AdminIdAndProject_Id(facebookId, adminId, projectId); }
        catch (Exception ignored) {}

        if (user == null && email != null && !email.isBlank()) {
            user = findByEmail(email, adminId, projectId);
        }

        if (user != null) {
            if ("INACTIVE".equalsIgnoreCase(user.getStatus().getName())) {
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

        if (userRepository.existsByUsernameIgnoreCaseAndOwner_AdminIdAndProject_Id(candidate, adminId, projectId)) {
            int suffix = 1;
            while (userRepository.existsByUsernameIgnoreCaseAndOwner_AdminIdAndProject_Id(candidate + suffix, adminId, projectId)) {
                suffix++;
            }
            candidate = candidate + suffix;
        }

        AdminUser owner = adminUsersRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Owner not found: " + adminId));
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

        Users newUser = new Users();
        newUser.setOwner(owner);
        newUser.setProject(project);
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

    public List<Users> getAllUsers(Long adminId, Long projectId) {
        return userRepository.findAll().stream()
            .filter(u -> u.getOwner()!=null && u.getProject()!=null
                      && Objects.equals(u.getOwner().getAdminId(), adminId)
                      && Objects.equals(u.getProject().getId(), projectId))
            .filter(u -> "ACTIVE".equals(u.getStatus().getName()))
            .filter(Users::isPublicProfile)
            .toList();
    }

    /* =========================================================
       SCHEDULED CLEANUPS
       ========================================================= */
    @Scheduled(cron = "0 0 2 * * *")
    public void softDeleteInactiveUsersAfter30Days() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        userRepository.findAll().stream()
            .filter(u -> "INACTIVE".equals(u.getStatus().getName()))
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
            .filter(u -> "DELETED".equals(u.getStatus().getName()))
            .filter(u -> u.getUpdatedAt() != null && u.getUpdatedAt().isBefore(cutoff))
            .forEach(userRepository::delete);
    }

    /* =========================================================
       LEGACY / GLOBAL OVERLOADS (to not break old controllers)
       ========================================================= */

    /** Legacy: global (no app scope) */
    public boolean sendVerificationCodeForRegistration(Map<String, String> userData) {
        String email = userData.get("email");
        String phone = userData.get("phoneNumber");
        String password = userData.get("password");

        boolean emailProvided = email != null && !email.isBlank();
        boolean phoneProvided = phone != null && !phone.isBlank();
        if (!emailProvided && !phoneProvided) throw new IllegalArgumentException("Provide email or phone");
        if (emailProvided && phoneProvided)    throw new IllegalArgumentException("Provide only one: email OR phone");

        // global uniqueness (legacy)
        if (emailProvided && userRepository.findByEmail(email) != null)
            throw new IllegalArgumentException("Email already in use");
        if (phoneProvided && userRepository.findByPhoneNumber(phone) != null)
            throw new IllegalArgumentException("Phone already in use");

        UserStatus status = getStatus(Optional.ofNullable(userData.get("status")).orElse("PENDING"));

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

    /** Legacy helper used by /resend-user-code */
    public void resendVerificationCode(String contact) {
        boolean isEmail = contact != null && contact.contains("@");
        PendingUser pending = isEmail
                ? pendingUserRepository.findByEmail(contact)
                : pendingUserRepository.findByPhoneNumber(contact);

        if (pending == null) throw new RuntimeException("No pending registration found");

        String code = isEmail ? String.format("%06d", new Random().nextInt(999999)) : "123456";
        pending.setVerificationCode(code);
        pending.setCreatedAt(LocalDateTime.now());
        pendingUserRepository.save(pending);

        if (isEmail) {
            String htmlMessage = """
                <html><body style="font-family: Arial; text-align:center; padding:20px">
                <h2>Verification code</h2>
                <h1>%s</h1>
                <p style="color:#777">Expires in 10 minutes.</p></body></html>
            """.formatted(code);
            emailService.sendHtmlEmail(contact, "Verification Code", htmlMessage);
        }
    }

    /** Legacy: global profile completion (no app scope) */
    @Transactional
    public boolean completeUserProfile(Long pendingId,
                                       String username,
                                       String firstName,
                                       String lastName,
                                       MultipartFile profileImage,
                                       Boolean isPublicProfile) throws IOException {

        PendingUser pending = pendingUserRepository.findById(pendingId)
                .orElseThrow(() -> new RuntimeException("Pending user not found."));

        // global username uniqueness
        if (findByUsername(username) != null) {
            throw new RuntimeException("Username already in use.");
        }

        Users user = new Users();
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

    /** Legacy: existsByUsername for AuthController check */
    public boolean existsByUsername(String username) {
        return findByUsername(username) != null;
    }

    /** Legacy: Optional findById (used by /reactivate) */
    public Optional<Users> findById(Long id) {
        return userRepository.findById(id);
    }

    /* -------------------- Legacy global lookups -------------------- */
    public Users getUserById(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found: " + userId));
    }
    public Users getUserByEmaill(String identifier) {
        Users user = (identifier != null && identifier.contains("@"))
                ? userRepository.findByEmail(identifier)
                : userRepository.findByPhoneNumber(identifier);
        if (user == null) throw new RuntimeException("User not found: " + identifier);
        return user;
    }
    public Users findByUsername(String username) { return userRepository.findByUsername(username); }
    public Users findByEmail(String email) { return userRepository.findByEmail(email); }
    public Users findByPhoneNumber(String phone) { return userRepository.findByPhoneNumber(phone); }

    /* -------------------- Misc helpers -------------------- */
    public Users save(Users user) { return userRepository.save(user); }

    public List<String> getUserCategories(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return userCategoriesRepository.findById_User_Id(userId).stream()
                .map(ui -> ui.getId().getCategory().getName())
                .toList();
    }

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
    
 // ===== Legacy, non-app-scoped social login overloads (keeps old controllers working) =====
    public Users handleGoogleUser(String email, String fullName, String pictureUrl, String googleId,
                                  java.util.concurrent.atomic.AtomicBoolean wasInactive,
                                  java.util.concurrent.atomic.AtomicBoolean isNewUser) {
        Users existingUser = findByEmail(email);
        if (existingUser != null) {
            if (existingUser.getStatus() != null && "INACTIVE".equals(existingUser.getStatus().getName())) {
                existingUser.setStatus(getStatus("ACTIVE"));
                existingUser.setUpdatedAt(java.time.LocalDateTime.now());
                wasInactive.set(true);
            }
            existingUser.setGoogleId(googleId);
            existingUser.setLastLogin(java.time.LocalDateTime.now());
            return userRepository.save(existingUser);
        }

        String firstName = "Google", lastName = "User";
        if (fullName != null && !fullName.trim().isEmpty()) {
            String[] parts = fullName.trim().split(" ", 2);
            firstName = parts[0];
            if (parts.length > 1) lastName = parts[1];
        }

        Users newUser = new Users();
        newUser.setEmail(email);
        newUser.setGoogleId(googleId);
        newUser.setUsername(email != null && email.contains("@") ? email.substring(0, email.indexOf('@')) : ("google." + System.currentTimeMillis()));
        newUser.setFirstName(firstName);
        newUser.setLastName(lastName);
        newUser.setProfilePictureUrl(pictureUrl);
        newUser.setIsPublicProfile(true);
        newUser.setStatus(getStatus("ACTIVE"));
        newUser.setPasswordHash("");
        newUser.setCreatedAt(java.time.LocalDateTime.now());
        newUser.setLastLogin(java.time.LocalDateTime.now());

        isNewUser.set(true);
        return userRepository.save(newUser);
    }

    public Users handleFacebookUser(String email, String facebookId, String firstName, String lastName, String picture,
                                    java.util.concurrent.atomic.AtomicBoolean wasInactive,
                                    java.util.concurrent.atomic.AtomicBoolean isNewUser) {
        Users user = (email != null && !email.isBlank()) ? findByEmail(email) : null;

        if (user != null) {
            if (user.getStatus() != null && "INACTIVE".equalsIgnoreCase(user.getStatus().getName())) {
                user.setStatus(getStatus("ACTIVE"));
                wasInactive.set(true);
            }
            user.setFacebookId(facebookId);
            if (firstName != null && !firstName.isBlank()) user.setFirstName(firstName);
            if (lastName  != null && !lastName.isBlank())  user.setLastName(lastName);
            if (picture   != null && !picture.isBlank())   user.setProfilePictureUrl(picture);
            user.setLastLogin(java.time.LocalDateTime.now());
            user.setUpdatedAt(java.time.LocalDateTime.now());
            return userRepository.save(user);
        }

        String baseUsername = (email != null && email.contains("@"))
                ? email.substring(0, email.indexOf('@'))
                : ((firstName != null ? firstName : "fb") + "." + (lastName != null ? lastName : "user")).toLowerCase().replaceAll("[^a-z0-9._-]", "");
        if (baseUsername.isBlank()) baseUsername = "fb.user";

        String candidate = baseUsername.toLowerCase().replaceAll("\\s+",".");

        // ensure global uniqueness using existing repository methods
        if (findByUsername(candidate) != null) {
            int suffix = 1;
            while (findByUsername(candidate + suffix) != null) suffix++;
            candidate = candidate + suffix;
        }

        Users newUser = new Users();
        newUser.setUsername(candidate);
        newUser.setFirstName(firstName != null ? firstName : "Facebook");
        newUser.setLastName(lastName  != null ? lastName  : "User");
        newUser.setEmail(email != null && !email.isBlank() ? email : null);
        newUser.setFacebookId(facebookId);
        newUser.setProfilePictureUrl(picture);
        newUser.setIsPublicProfile(true);
        newUser.setStatus(getStatus("ACTIVE"));
        newUser.setPasswordHash("");
        newUser.setCreatedAt(java.time.LocalDateTime.now());
        newUser.setLastLogin(java.time.LocalDateTime.now());
        newUser.setUpdatedAt(java.time.LocalDateTime.now());

        isNewUser.set(true);
        return userRepository.save(newUser);
    }

}
