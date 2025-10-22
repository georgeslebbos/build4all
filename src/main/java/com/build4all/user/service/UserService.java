package com.build4all.user.service;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.catalog.domain.Category;
import com.build4all.catalog.repository.CategoryRepository;
import com.build4all.notifications.service.EmailService;
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
    @Autowired private UserStatusRepository userStatusRepository;

    // unified tenant link repo
    @Autowired private AdminUserProjectRepository aupRepo;

    @Autowired
    private final EmailService emailService;

    public UserService(EmailService emailService) { this.emailService = emailService; }

    /* -------------------- Status helper -------------------- */
    public UserStatus getStatus(String name) {
        return userStatusRepository.findByName(name.toUpperCase())
            .orElseThrow(() -> new RuntimeException("UserStatus " + name + " not found"));
    }

    /* -------------------- Tenant helper -------------------- */
    private AdminUserProject linkById(Long ownerProjectLinkId) {
        if (ownerProjectLinkId == null) throw new RuntimeException("ownerProjectLinkId is required");
        return aupRepo.findById(ownerProjectLinkId)
                .orElseThrow(() -> new RuntimeException("AdminUserProject not found: " + ownerProjectLinkId));
    }

    /* =========================================================
       REGISTRATION – per tenant (by ownerProjectLinkId)
       ========================================================= */
    public boolean sendVerificationCodeForRegistration(Map<String, String> userData, Long ownerProjectLinkId) {
        AdminUserProject link = linkById(ownerProjectLinkId);

        String email = userData.get("email");
        String phone = userData.get("phoneNumber");
        String password = userData.get("password");

        boolean emailProvided = email != null && !email.isBlank();
        boolean phoneProvided = phone != null && !phone.isBlank();
        if (!emailProvided && !phoneProvided) throw new IllegalArgumentException("Provide email or phone");
        if (emailProvided && phoneProvided)    throw new IllegalArgumentException("Provide only one: email OR phone");

        // per-tenant uniqueness
        if (emailProvided && userRepository.existsByEmailAndOwnerProject(email, link))
            throw new IllegalArgumentException("Email already in use in this app");
        if (phoneProvided && userRepository.existsByPhoneNumberAndOwnerProject(phone, link))
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
                                       Long ownerProjectLinkId) throws IOException {

        AdminUserProject link = linkById(ownerProjectLinkId);

        PendingUser pending = pendingUserRepository.findById(pendingId)
                .orElseThrow(() -> new RuntimeException("Pending user not found."));

        if (userRepository.existsByUsernameIgnoreCaseAndOwnerProject(username, link)) {
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

    /* =========================================================
       LOOKUPS – tenant-scoped by ownerProjectLinkId
       ========================================================= */
    public Users findByEmail(String email, Long ownerProjectLinkId) {
        return userRepository.findByEmailAndOwnerProject(email, linkById(ownerProjectLinkId));
    }
    public Users findByPhoneNumber(String phone, Long ownerProjectLinkId) {
        return userRepository.findByPhoneNumberAndOwnerProject(phone, linkById(ownerProjectLinkId));
    }
    public Users findByUsername(String username, Long ownerProjectLinkId) {
        return userRepository.findByUsernameAndOwnerProject(username, linkById(ownerProjectLinkId));
    }
    public Users getUserByEmaill(String identifier, Long ownerProjectLinkId) {
        var link = linkById(ownerProjectLinkId);
        Users user = identifier != null && identifier.contains("@")
                ? userRepository.findByEmailAndOwnerProject(identifier, link)
                : userRepository.findByPhoneNumberAndOwnerProject(identifier, link);
        if (user == null) throw new RuntimeException("User not found in this app: " + identifier);
        return user;
    }
    public Users getUserById(Long userId, Long ownerProjectLinkId) {
        return userRepository.findByIdAndOwnerProject(userId, linkById(ownerProjectLinkId))
                .orElseThrow(() -> new RuntimeException("User not found in this app: " + userId));
    }

    /* =========================================================
       PASSWORD RESET – tenant-scoped (key uses linkId now)
       ========================================================= */
    private final Map<String, String> resetCodes = new ConcurrentHashMap<>();

    public boolean resetPassword(String email, Long ownerProjectLinkId) {
        var link = linkById(ownerProjectLinkId);
        Users user = userRepository.findByEmailAndOwnerProject(email, link);
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

    /* =========================================================
       LISTS / DELETES (tenant filtered)
       ========================================================= */
    public List<UserDto> getAllUserDtos(Long ownerProjectLinkId) {
        var link = linkById(ownerProjectLinkId);
        return userRepository.findAll().stream()
            .filter(u -> u.getOwnerProject()!=null && u.getOwnerProject().equals(link))
            .filter(u -> u.getStatus()!=null && "ACTIVE".equals(u.getStatus().getName()))
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
       SOCIAL LOGIN – tenant-scoped (ownerProjectLinkId)
       ========================================================= */
    public Users handleGoogleUser(String email, String fullName, String pictureUrl, String googleId,
                                  AtomicBoolean wasInactive, AtomicBoolean isNewUser,
                                  Long ownerProjectLinkId) {
        var link = linkById(ownerProjectLinkId);

        Users existingUser = userRepository.findByEmailAndOwnerProject(email, link);
        if (existingUser != null) {
            if (existingUser.getStatus()!=null && "INACTIVE".equals(existingUser.getStatus().getName())) {
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

        var link = linkById(ownerProjectLinkId);

        Users user = null;
        try { user = userRepository.findByFacebookIdAndOwnerProject(facebookId, link); }
        catch (Exception ignored) {}

        if (user == null && email != null && !email.isBlank()) {
            user = userRepository.findByEmailAndOwnerProject(email, link);
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

        if (userRepository.existsByUsernameIgnoreCaseAndOwnerProject(candidate, link)) {
            int suffix = 1;
            while (userRepository.existsByUsernameIgnoreCaseAndOwnerProject(candidate + suffix, link)) {
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

    /* =========================================================
       PUBLIC LIST (tenant filtered)
       ========================================================= */
    public List<Users> getAllUsers(Long ownerProjectLinkId) {
        var link = linkById(ownerProjectLinkId);
        return userRepository.findAll().stream()
            .filter(u -> u.getOwnerProject()!=null && u.getOwnerProject().equals(link))
            .filter(u -> u.getStatus()!=null && "ACTIVE".equals(u.getStatus().getName()))
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
            .filter(u -> u.getStatus()!=null && "INACTIVE".equals(u.getStatus().getName()))
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
            .filter(u -> u.getStatus()!=null && "DELETED".equals(u.getStatus().getName()))
            .filter(u -> u.getUpdatedAt() != null && u.getUpdatedAt().isBefore(cutoff))
            .forEach(userRepository::delete);
    }

    /* -------- (optional legacy) -------- */
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
}
