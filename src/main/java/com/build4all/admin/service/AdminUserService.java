package com.build4all.admin.service;

import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.domain.PendingAdminEmailChange;
import com.build4all.admin.domain.PendingAdminPhoneChange;
import com.build4all.admin.dto.AdminUserProfileDTO;
import com.build4all.admin.dto.AdminUserUpdateProfileRequest;
import com.build4all.admin.repository.AdminUsersRepository;
import com.build4all.admin.repository.PendingAdminEmailChangeRepository;
import com.build4all.admin.repository.PendingAdminPhoneChangeRepository;
import com.build4all.business.domain.Businesses;
import com.build4all.common.util.PhoneNumberValidator;
import com.build4all.notifications.service.EmailService;
import com.build4all.order.repository.OrderItemRepository;
import com.build4all.review.repository.ReviewRepository;
import com.build4all.role.domain.Role;
import com.build4all.role.repository.RoleRepository;
import com.build4all.user.domain.Users;
import com.build4all.user.dto.UserSummaryDTO;
import com.build4all.user.repository.UsersRepository;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
/**
 * Service that manages AdminUser accounts and related admin operations.
 *
 * Main responsibilities:
 * - Create admin users (with specific role)
 * - Promote regular Users to AdminUser (MANAGER)
 * - List users/admins in unified format (UserSummaryDTO)
 * - Delete users/managers and their dependent records (orders/reviews)
 * - Provide helper lookups (findByEmail, hasSuperAdmin, requireById, etc.)
 */
public class AdminUserService {

    @Autowired
    private AdminUsersRepository adminUserRepository;

    @Autowired
    private OrderItemRepository OrderItemRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private PendingAdminEmailChangeRepository pendingAdminEmailRepo;

    @Autowired
    private EmailService emailService;
    
    @Autowired
    private PendingAdminPhoneChangeRepository pendingAdminPhoneRepo;

    private static final int ADMIN_EMAIL_CHANGE_TTL_MIN = 15;
    private static final int ADMIN_EMAIL_CHANGE_MAX_ATTEMPTS = 5;
    private static final int ADMIN_EMAIL_CHANGE_RESEND_COOLDOWN_SEC = 60;
    private static final int PASSWORD_MIN_LEN = 6;
    
    private static final int ADMIN_PHONE_CHANGE_TTL_MIN = 15;
    private static final int ADMIN_PHONE_CHANGE_MAX_ATTEMPTS = 5;
    private static final int ADMIN_PHONE_CHANGE_RESEND_COOLDOWN_SEC = 60;

    private final SecureRandom secureRandom = new SecureRandom();

    private String otp6() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    private void validateEmailOrThrow(String email) {
        try {
            InternetAddress addr = new InternetAddress(email, true);
            addr.validate();
        } catch (Exception ex) {
            throw new RuntimeException("Invalid email format");
        }
    }
    
    private String normalizePhoneOrThrow(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new RuntimeException("New phone number is required");
        }

        String value = raw.trim()
                .replace(" ", "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "")
                .replace(".", "");

        if (value.startsWith("00")) {
            value = "+" + value.substring(2);
        }

        // Lebanese local format -> normalize to +961
        if (!value.startsWith("+")) {
            if (value.matches("^(3|70|71|76|78|79|81)\\d{6}$")) {
                value = "+961" + value;
            } else {
                throw new RuntimeException("Invalid phone number format");
            }
        }

        if (value.startsWith("+961")) {
            String national = value.substring(4);
            if (national.startsWith("0")) {
                national = national.substring(1);
            }

            if (!national.matches("^(3|70|71|76|78|79|81)\\d{6}$")) {
                throw new RuntimeException("Invalid Lebanese phone number");
            }

            return "+961" + national;
        }

        if (!value.matches("^\\+[1-9]\\d{7,14}$")) {
            throw new RuntimeException("Invalid phone number format");
        }

        return value;
    }

    private void sendAdminPhoneChangeOtp(String to, String code) {
        // Dev path for now. Replace with real SMS provider later.
        System.out.println("📱 Admin phone change OTP to " + to + ": code " + code);
    }

    private void sendAdminEmailChangeOtp(String to, String code) {
        String html = """
            <html><body style="font-family: Arial; text-align:center; padding:20px">
              <h2>Confirm your new email</h2>
              <p>Use this code to verify your new email:</p>
              <h1 style="letter-spacing:6px">%s</h1>
              <p>This code expires in <b>%d minutes</b>.</p>
            </body></html>
        """.formatted(code, ADMIN_EMAIL_CHANGE_TTL_MIN);

        emailService.sendHtmlEmail(to, "Confirm your new email", html);
    }

    @Transactional
    public void requestAdminEmailChange(Long adminId, String newEmail) {
        if (newEmail == null || newEmail.trim().isEmpty()) {
            throw new RuntimeException("New email is required");
        }

        String normalized = newEmail.trim().toLowerCase();
        validateEmailOrThrow(normalized);

        AdminUser admin = requireById(adminId);

        // same email? nothing to do
        if (admin.getEmail() != null && admin.getEmail().trim().equalsIgnoreCase(normalized)) {
            return;
        }

        // unique across admins (excluding self)
        if (adminUserRepository.existsByEmailIgnoreCaseAndAdminIdNot(normalized, adminId)) {
            throw new RuntimeException("Email already in use");
        }

        PendingAdminEmailChange pending = pendingAdminEmailRepo.findByAdmin_AdminId(adminId)
                .orElseGet(PendingAdminEmailChange::new);

        String code = otp6();
        LocalDateTime now = LocalDateTime.now();

        pending.setAdmin(admin);
        pending.setNewEmail(normalized);
        pending.setCodeHash(passwordEncoder.encode(code));
        pending.setAttempts(0);
        pending.setCreatedAt(now);
        pending.setExpiresAt(now.plusMinutes(ADMIN_EMAIL_CHANGE_TTL_MIN));
        pending.setLastSentAt(now);

        pendingAdminEmailRepo.save(pending);
        sendAdminEmailChangeOtp(normalized, code);
    }

    @Transactional
    public void verifyAdminEmailChange(Long adminId, String code) {
        code = code == null ? null : code.trim();

        if (code == null || code.isEmpty()) {
            throw new RuntimeException("Verification code is required");
        }

        PendingAdminEmailChange pending = pendingAdminEmailRepo.findByAdmin_AdminId(adminId)
                .orElseThrow(() -> new RuntimeException("No pending email change request"));

        LocalDateTime now = LocalDateTime.now();

        if (pending.getExpiresAt() != null && pending.getExpiresAt().isBefore(now)) {
            pendingAdminEmailRepo.delete(pending);
            throw new RuntimeException("Verification code expired. Request again.");
        }

        pending.setAttempts(pending.getAttempts() + 1);

        if (pending.getAttempts() > ADMIN_EMAIL_CHANGE_MAX_ATTEMPTS) {
            pendingAdminEmailRepo.delete(pending);
            throw new RuntimeException("Too many attempts. Request a new code.");
        }

        if (!passwordEncoder.matches(code, pending.getCodeHash())) {
            pendingAdminEmailRepo.save(pending);
            throw new RuntimeException("Invalid verification code");
        }

        AdminUser admin = requireById(adminId);
        admin.setEmail(pending.getNewEmail());
        adminUserRepository.save(admin);

        pendingAdminEmailRepo.delete(pending);
    }

    @Transactional
    public void resendAdminEmailChangeCode(Long adminId) {
        PendingAdminEmailChange pending = pendingAdminEmailRepo.findByAdmin_AdminId(adminId)
                .orElseThrow(() -> new RuntimeException("No pending email change request"));

        LocalDateTime now = LocalDateTime.now();

        if (pending.getLastSentAt() != null
                && pending.getLastSentAt().plusSeconds(ADMIN_EMAIL_CHANGE_RESEND_COOLDOWN_SEC).isAfter(now)) {
            throw new RuntimeException("Please wait before resending the code");
        }

        String code = otp6();
        pending.setCodeHash(passwordEncoder.encode(code));
        pending.setAttempts(0);
        pending.setCreatedAt(now);
        pending.setExpiresAt(now.plusMinutes(ADMIN_EMAIL_CHANGE_TTL_MIN));
        pending.setLastSentAt(now);

        pendingAdminEmailRepo.save(pending);
        sendAdminEmailChangeOtp(pending.getNewEmail(), code);
    }
    
    @Transactional
    public void requestAdminPhoneChange(Long adminId, String newPhone) {
        String normalized = normalizePhoneOrThrow(newPhone);

        AdminUser admin = requireById(adminId);

        // same phone? nothing to do
        if (admin.getPhoneNumber() != null && admin.getPhoneNumber().trim().equals(normalized)) {
            return;
        }

        // unique across admins (excluding self)
        if (adminUserRepository.existsByPhoneNumberAndAdminIdNot(normalized, adminId)) {
            throw new RuntimeException("Phone number already in use");
        }

        PendingAdminPhoneChange pending = pendingAdminPhoneRepo.findByAdmin_AdminId(adminId)
                .orElseGet(PendingAdminPhoneChange::new);

        String code = otp6();
        LocalDateTime now = LocalDateTime.now();

        pending.setAdmin(admin);
        pending.setNewPhone(normalized);
        pending.setCodeHash(passwordEncoder.encode(code));
        pending.setAttempts(0);
        pending.setCreatedAt(now);
        pending.setExpiresAt(now.plusMinutes(ADMIN_PHONE_CHANGE_TTL_MIN));
        pending.setLastSentAt(now);

        pendingAdminPhoneRepo.save(pending);
        sendAdminPhoneChangeOtp(normalized, code);
    }

    @Transactional
    public void verifyAdminPhoneChange(Long adminId, String code) {
        code = code == null ? null : code.trim();

        if (code == null || code.isEmpty()) {
            throw new RuntimeException("Verification code is required");
        }

        PendingAdminPhoneChange pending = pendingAdminPhoneRepo.findByAdmin_AdminId(adminId)
                .orElseThrow(() -> new RuntimeException("No pending phone change request"));

        LocalDateTime now = LocalDateTime.now();

        if (pending.getExpiresAt() != null && pending.getExpiresAt().isBefore(now)) {
            pendingAdminPhoneRepo.delete(pending);
            throw new RuntimeException("Verification code expired. Request again.");
        }

        pending.setAttempts(pending.getAttempts() + 1);

        if (pending.getAttempts() > ADMIN_PHONE_CHANGE_MAX_ATTEMPTS) {
            pendingAdminPhoneRepo.delete(pending);
            throw new RuntimeException("Too many attempts. Request a new code.");
        }

        if (!passwordEncoder.matches(code, pending.getCodeHash())) {
            pendingAdminPhoneRepo.save(pending);
            throw new RuntimeException("Invalid verification code");
        }

        AdminUser admin = requireById(adminId);
        admin.setPhoneNumber(pending.getNewPhone());
        adminUserRepository.save(admin);

        pendingAdminPhoneRepo.delete(pending);
    }

    @Transactional
    public void resendAdminPhoneChangeCode(Long adminId) {
        PendingAdminPhoneChange pending = pendingAdminPhoneRepo.findByAdmin_AdminId(adminId)
                .orElseThrow(() -> new RuntimeException("No pending phone change request"));

        LocalDateTime now = LocalDateTime.now();

        if (pending.getLastSentAt() != null
                && pending.getLastSentAt().plusSeconds(ADMIN_PHONE_CHANGE_RESEND_COOLDOWN_SEC).isAfter(now)) {
            throw new RuntimeException("Please wait before resending the code");
        }

        String code = otp6();
        pending.setCodeHash(passwordEncoder.encode(code));
        pending.setAttempts(0);
        pending.setCreatedAt(now);
        pending.setExpiresAt(now.plusMinutes(ADMIN_PHONE_CHANGE_TTL_MIN));
        pending.setLastSentAt(now);

        pendingAdminPhoneRepo.save(pending);
        sendAdminPhoneChangeOtp(pending.getNewPhone(), code);
    }

    /**
     * Finds an admin user by email.
     * Used commonly in login flows or account management.
     */
    public Optional<AdminUser> findByEmail(String email) {
        return adminUserRepository.findByEmail(email);
    }

    /**
     * Finds an admin user by username.
     */
    public Optional<AdminUser> findByUsername(String username) {
        return adminUserRepository.findByUsername(username);
    }

    /**
     * Finds an admin user by primary key (admin_id).
     */
    public Optional<AdminUser> findById(Long id) {
        return adminUserRepository.findById(id);
    }

    /**
     * Persists an admin user (create/update).
     */
    public void save(AdminUser adminUser) {
        adminUserRepository.save(adminUser);
    }

    /**
     * Creates a new AdminUser with the given role.
     *
     * Flow:
     * 1) Load Role by roleName (case-insensitive).
     * 2) Ensure email is not already used by an AdminUser.
     * 3) Encode the plain password.
     * 4) Create AdminUser and save it.
     */
    public AdminUser createAdminUser(String username, String firstName, String lastName,
                                     String email, String plainPassword, String roleName) {

        Role role = roleRepository.findByNameIgnoreCase(roleName)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleName));

        Optional<AdminUser> existingAdmin = adminUserRepository.findByEmail(email);
        if (existingAdmin.isPresent()) {
            throw new RuntimeException("Admin user with email " + email + " already exists");
        }

        if (!StringUtils.hasText(plainPassword)) {
            throw new RuntimeException("password is required");
        }

        String cleaned = plainPassword.trim();
        validateAdminPasswordOrThrow(cleaned);

        String encodedPassword = passwordEncoder.encode(cleaned);

        AdminUser admin = new AdminUser(username, firstName, lastName, email, encodedPassword, role);
        return adminUserRepository.save(admin);
    }

    /**
     * Finds an admin user by username or email using the same input for both.
     * This supports "login with username OR email" style input.
     */
    public Optional<AdminUser> findByUsernameOrEmail(String input) {
        return adminUserRepository.findByUsernameOrEmail(input, input);
    }

    /**
     * Promotes a regular Users entity to an AdminUser with role MANAGER.
     *
     * Flow:
     * 1) Load MANAGER role
     * 2) Ensure no AdminUser already exists with same email
     * 3) Create AdminUser based on user fields
     * 4) Save and return
     */
    public AdminUser promoteUserToManager(Users user) {
        Role managerRole = roleRepository.findByNameIgnoreCase("MANAGER")
                .orElseThrow(() -> new RuntimeException("Manager role not found"));

        Optional<AdminUser> existing = adminUserRepository.findByEmail(user.getEmail());
        if (existing.isPresent()) {
            throw new RuntimeException("User already promoted to admin");
        }

        AdminUser manager = new AdminUser(
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPasswordHash(),
                managerRole
        );

        return adminUserRepository.save(manager);
    }

    /**
     * Promotes a user to MANAGER and links the created AdminUser to a specific business.
     * Here, the business relation is stored via AdminUser.business (FK).
     */
    public AdminUser promoteUserToManager(Users user, Businesses business) {
        Role managerRole = roleRepository.findByNameIgnoreCase("MANAGER")
                .orElseThrow(() -> new RuntimeException("Role MANAGER not found"));

        AdminUser manager = new AdminUser();
        manager.setUsername(user.getUsername());
        manager.setFirstName(user.getFirstName());
        manager.setLastName(user.getLastName());
        manager.setEmail(user.getEmail());
        manager.setPasswordHash(user.getPasswordHash());
        manager.setRole(managerRole);
        manager.setBusiness(business);

        return adminUserRepository.save(manager);
    }

    /**
     * Returns a combined list of:
     * - normal app users (Users table) labeled as "USER"
     * - admin users (AdminUser table) labeled as their role name
     *
     * This is typically used in admin UI to show all accounts in one list.
     */
    public List<UserSummaryDTO> getAllUserSummaries() {
        List<UserSummaryDTO> result = new ArrayList<>();

        List<UserSummaryDTO> users = usersRepository.findAll().stream()
                .map(u -> new UserSummaryDTO(
                        u.getId(),
                        u.getFirstName() + " " + u.getLastName(),
                        u.getEmail() != null ? u.getEmail() : u.getPhoneNumber(),
                        "USER"))
                .collect(Collectors.toList());

        List<UserSummaryDTO> admins = adminUserRepository.findAll().stream()
                .map(a -> new UserSummaryDTO(
                        a.getAdminId(),
                        a.getFirstName() + " " + a.getLastName(),
                        a.getEmail(),
                        a.getRole().getName()))
                .collect(Collectors.toList());

        result.addAll(users);
        result.addAll(admins);

        return result;
    }

    /**
     * Deletes a user (Users table) and dependent records:
     * - Reviews created by that user
     * - OrderItems created by that user
     * - The user itself
     *
     * @Transactional ensures all operations run in one transaction.
     */
    @Transactional
    public void deleteUserAndDependencies(Long userId) {
        reviewRepository.deleteByCustomer_Id(userId);
        OrderItemRepository.deleteByUser_Id(userId);
        usersRepository.deleteById(userId);
    }

    /**
     * Deletes an AdminUser (manager) by adminId.
     * Old approach deleted rows from an association table (admin_user_business).
     * New approach: business is a FK field on AdminUser, so deleting AdminUser is enough.
     */
    @Transactional
    public void deleteManagerById(Long adminId) {
        adminUserRepository.findById(adminId).ifPresent(adminUserRepository::delete);
    }

    /**
     * Returns users filtered by role name.
     * - If role == USER => returns Users table
     * - Otherwise => returns AdminUser table filtered by that role name
     */
    public List<UserSummaryDTO> getUsersByRole(String role) {
        List<UserSummaryDTO> result = new ArrayList<>();

        if ("USER".equalsIgnoreCase(role)) {
            result = usersRepository.findAll().stream()
                    .map(u -> new UserSummaryDTO(
                            u.getId(),
                            u.getFirstName() + " " + u.getLastName(),
                            u.getEmail() != null ? u.getEmail() : u.getPhoneNumber(),
                            "USER"))
                    .collect(Collectors.toList());
        } else {
            result = adminUserRepository.findAll().stream()
                    .filter(a -> a.getRole().getName().equalsIgnoreCase(role))
                    .map(a -> new UserSummaryDTO(
                            a.getAdminId(),
                            a.getFirstName() + " " + a.getLastName(),
                            a.getEmail(),
                            a.getRole().getName()))
                    .collect(Collectors.toList());
        }

        return result;
    }

    /**
     * Checks whether the given user is already a manager within the given business.
     * It searches AdminUser by (email + business).
     */
    public boolean isUserAlreadyManager(Users user, Businesses business) {
        List<AdminUser> results = adminUserRepository.findByEmailAndBusiness(user.getEmail(), business);
        return !results.isEmpty();
    }

    /**
     * Deletes an admin user by email (if it exists).
     */
    public void deleteManagerByEmail(String email) {
        Optional<AdminUser> admin = adminUserRepository.findByEmail(email);
        admin.ifPresent(a -> adminUserRepository.deleteById(a.getAdminId()));
    }

    /**
     * Same as findByEmail; kept for naming clarity in some flows.
     */
    public Optional<AdminUser> findByUserEmail(String email) {
        return adminUserRepository.findByEmail(email);
    }

    /**
     * Returns all AdminUser entries matching an email (could span multiple businesses).
     */
    public List<AdminUser> findAllByUserEmail(String email) {
        return adminUserRepository.findAllByEmail(email);
    }

    /**
     * Checks if there is at least one SUPER_ADMIN in the system.
     * Uses a count query by role name.
     */
    public boolean hasSuperAdmin() {
        return adminUserRepository.countByRole_NameIgnoreCase("SUPER_ADMIN") > 0;
    }

    /**
     * Like findById but throws if missing.
     * Useful in service code when existence is mandatory.
     */
    public AdminUser requireById(Long adminId) {
        return adminUserRepository.findById(adminId)
                .orElseThrow(() -> new NoSuchElementException("Admin user not found: " + adminId));
    }

    /**
     * Converts an AdminUser entity into AdminUserProfileDTO.
     * This isolates API responses from exposing the whole entity graph.
     */
    public AdminUserProfileDTO toProfileDTO(AdminUser a) {
        return new AdminUserProfileDTO(
                a.getAdminId(),
                a.getUsername(),
                a.getFirstName(),
                a.getLastName(),
                a.getEmail(),
                a.getPhoneNumber(),
                a.getRole() != null ? a.getRole().getName() : null,
                a.getBusiness() != null ? a.getBusiness().getId() : null,
                a.getNotifyItemUpdates(),
                a.getNotifyUserFeedback(),
                a.getCreatedAt(),
                a.getUpdatedAt()
        );
    }

    @Transactional
    public AdminUserProfileDTO updateAdminProfile(Long adminId, AdminUserUpdateProfileRequest req) {
        AdminUser admin = requireById(adminId);

        // --- username ---
        if (StringUtils.hasText(req.getUsername())) {
            String newUsername = req.getUsername().trim();
            if (newUsername.length() < 3) {
                throw new RuntimeException("Username must be at least 3 characters");
            }
            if (adminUserRepository.existsByUsernameIgnoreCaseAndAdminIdNot(newUsername, adminId)) {
                throw new RuntimeException("Username already in use");
            }
            admin.setUsername(newUsername);
        }

        // --- first / last name ---
        if (StringUtils.hasText(req.getFirstName())) {
            String fn = req.getFirstName().trim();
            if (fn.length() < 3) throw new RuntimeException("First name must be at least 3 characters");
            admin.setFirstName(fn);
        }
        if (StringUtils.hasText(req.getLastName())) {
            String ln = req.getLastName().trim();
            if (ln.length() < 3) throw new RuntimeException("Last name must be at least 3 characters");
            admin.setLastName(ln);
        }

        // --- email --- (NO direct change anymore)
        if (StringUtils.hasText(req.getEmail())) {
            String newEmail = req.getEmail().trim();

            if (admin.getEmail() != null && !admin.getEmail().equalsIgnoreCase(newEmail)) {
                throw new RuntimeException("Email change requires verification. Use request-email-change.");
            }
        }
     // --- phone --- (NO direct change anymore)
        if (req.getPhoneNumber() != null) {
            String requested = req.getPhoneNumber().trim();
            String current = admin.getPhoneNumber() == null ? "" : admin.getPhoneNumber().trim();

            // same value coming back from frontend -> ignore
            if (!requested.equals(current)) {
                throw new RuntimeException("Phone change requires verification. Use request-phone-change.");
            }
        }
        
        // --- notification toggles ---
        if (req.getNotifyItemUpdates() != null) {
            admin.setNotifyItemUpdates(req.getNotifyItemUpdates());
        }
        if (req.getNotifyUserFeedback() != null) {
            admin.setNotifyUserFeedback(req.getNotifyUserFeedback());
        }

        // --- AI flag ---
        if (req.getAiEnabled() != null) {
            admin.setAiEnabled(req.getAiEnabled());
        }

        // --- optional password change ---
        boolean wantsPasswordChange = StringUtils.hasText(req.getNewPassword());
        if (wantsPasswordChange) {
            String newPw = req.getNewPassword().trim();
            validateAdminPasswordOrThrow(newPw);

            if (!StringUtils.hasText(req.getCurrentPassword())) {
                throw new RuntimeException("Current password is required to change password");
            }

            boolean ok = passwordEncoder.matches(req.getCurrentPassword(), admin.getPasswordHash());
            if (!ok) {
                throw new RuntimeException("Current password is incorrect");
            }

            admin.setPasswordHash(passwordEncoder.encode(newPw));
        }

        adminUserRepository.save(admin);
        return toProfileDTO(admin);
    }

    private void validateAdminPasswordOrThrow(String password) {
        if (password == null || password.isBlank()) {
            throw new RuntimeException("newPassword is required");
        }
        if (password.length() < PASSWORD_MIN_LEN) {
            throw new RuntimeException("newPassword must be at least " + PASSWORD_MIN_LEN + " characters");
        }
    }
    
    @Transactional(readOnly = true)
    public AdminUserProfileDTO getProfileDTOById(Long adminId) {
        AdminUser a = adminUserRepository.findById(adminId)
                .orElseThrow(() -> new NoSuchElementException("Admin user not found: " + adminId));

        return new AdminUserProfileDTO(
                a.getAdminId(),
                a.getUsername(),
                a.getFirstName(),
                a.getLastName(),
                a.getEmail(),
                a.getPhoneNumber(),
                a.getRole() != null ? a.getRole().getName() : null,
                a.getBusiness() != null ? a.getBusiness().getId() : null,
                a.getNotifyItemUpdates(),
                a.getNotifyUserFeedback(),
                a.getCreatedAt(),
                a.getUpdatedAt()
        );
    }
}