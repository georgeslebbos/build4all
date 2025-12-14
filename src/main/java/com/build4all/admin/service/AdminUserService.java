package com.build4all.admin.service;

import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.dto.AdminUserProfileDTO;
import com.build4all.business.domain.Businesses;
import com.build4all.review.repository.ReviewRepository;
import com.build4all.role.domain.Role;
import com.build4all.admin.repository.AdminUsersRepository;
import com.build4all.role.repository.RoleRepository;
import com.build4all.user.domain.Users;
import com.build4all.user.repository.UsersRepository;
import com.build4all.order.repository.OrderItemRepository;
import com.build4all.user.dto.UserSummaryDTO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    // REMOVED: AdminUserBusinessRepository
    // Reason (based on your comments): the relationship is now stored directly via FK on AdminUser (business_id).

    @Autowired
    private OrderItemRepository OrderItemRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    // Used to hash plain passwords when creating admin users.
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UsersRepository usersRepository;

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

        // Hash the password before storing it in DB.
        String encodedPassword = passwordEncoder.encode(plainPassword);

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

        // Uses the user's passwordHash directly (meaning the user already has a hashed password).
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

        // Map Users entities into summary DTOs.
        List<UserSummaryDTO> users = usersRepository.findAll().stream()
                .map(u -> new UserSummaryDTO(
                        u.getId(),
                        u.getFirstName() + " " + u.getLastName(),
                        // Prefer email; fallback to phone number if email is null.
                        u.getEmail() != null ? u.getEmail() : u.getPhoneNumber(),
                        "USER"))
                .collect(Collectors.toList());

        // Map AdminUser entities into summary DTOs (role name included).
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
        // OLD: delete links from admin_user_business
        // NEW: not needed; just delete the AdminUser (FK is on AdminUser now)
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
            // Return all regular users.
            result = usersRepository.findAll().stream()
                    .map(u -> new UserSummaryDTO(
                            u.getId(),
                            u.getFirstName() + " " + u.getLastName(),
                            u.getEmail() != null ? u.getEmail() : u.getPhoneNumber(),
                            "USER"))
                    .collect(Collectors.toList());
        } else {
            // Return all admins matching the given role.
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
        // Checks for an AdminUser with the same email tied to the same business (FK)
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
                a.getRole() != null ? a.getRole().getName() : null,
                a.getBusiness() != null ? a.getBusiness().getId() : null,
                a.getNotifyItemUpdates(),
                a.getNotifyUserFeedback(),
                a.getCreatedAt(),
                a.getUpdatedAt()
        );
    }
}
