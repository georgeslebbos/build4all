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
import com.build4all.booking.repository.ItemBookingsRepository;
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
public class AdminUserService {

    @Autowired
    private AdminUsersRepository adminUserRepository;

    // REMOVED: AdminUserBusinessRepository

    @Autowired
    private ItemBookingsRepository itemBookingsRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UsersRepository usersRepository;

    public Optional<AdminUser> findByEmail(String email) {
        return adminUserRepository.findByEmail(email);
    }

    public Optional<AdminUser> findByUsername(String username) {
        return adminUserRepository.findByUsername(username);
    }

    public Optional<AdminUser> findById(Long id) {
        return adminUserRepository.findById(id);
    }

    public void save(AdminUser adminUser) {
        adminUserRepository.save(adminUser);
    }

    public AdminUser createAdminUser(String username, String firstName, String lastName,
                                     String email, String plainPassword, String roleName) {

        Role role = roleRepository.findByName(roleName.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleName));

        Optional<AdminUser> existingAdmin = adminUserRepository.findByEmail(email);
        if (existingAdmin.isPresent()) {
            throw new RuntimeException("Admin user with email " + email + " already exists");
        }

        String encodedPassword = passwordEncoder.encode(plainPassword);

        AdminUser admin = new AdminUser(username, firstName, lastName, email, encodedPassword, role);

        return adminUserRepository.save(admin);
    }

    public Optional<AdminUser> findByUsernameOrEmail(String input) {
        return adminUserRepository.findByUsernameOrEmail(input, input);
    }

    public AdminUser promoteUserToManager(Users user) {
        Role managerRole = roleRepository.findByName("MANAGER")
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
                managerRole);

        return adminUserRepository.save(manager);
    }

    public AdminUser promoteUserToManager(Users user, Businesses business) {
        Role managerRole = roleRepository.findByName("MANAGER")
                .orElseThrow(() -> new RuntimeException("Role MANAGER not found"));

        AdminUser manager = new AdminUser();
        manager.setUsername(user.getUsername());
        manager.setFirstName(user.getFirstName());
        manager.setLastName(user.getLastName());
        manager.setEmail(user.getEmail());
        manager.setPasswordHash(user.getPasswordHash());
        manager.setRole(managerRole);

        // 👇 Direct FK instead of join-table
        manager.setBusiness(business);

        // 👇 No AdminUserBusiness creation anymore
        return adminUserRepository.save(manager);
    }

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

    
    @Transactional
    public void deleteUserAndDependencies(Long userId) {
        reviewRepository.deleteByCustomer_Id(userId);
        itemBookingsRepository.deleteByUser_Id(userId);
        usersRepository.deleteById(userId);
    }

    @Transactional
    public void deleteManagerById(Long adminId) {
        // OLD: delete links from admin_user_business
        // NEW: not needed; just delete the AdminUser (FK is on AdminUser now)
        adminUserRepository.findById(adminId).ifPresent(adminUserRepository::delete);
    }

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

    public boolean isUserAlreadyManager(Users user, Businesses business) {
        // Checks for an AdminUser with the same email tied to the same business (FK)
        List<AdminUser> results = adminUserRepository.findByEmailAndBusiness(user.getEmail(), business);
        return !results.isEmpty();
    }

    public void deleteManagerByEmail(String email) {
        Optional<AdminUser> admin = adminUserRepository.findByEmail(email);
        admin.ifPresent(a -> adminUserRepository.deleteById(a.getAdminId()));
    }

    public Optional<AdminUser> findByUserEmail(String email) {
        return adminUserRepository.findByEmail(email);
    }

    public List<AdminUser> findAllByUserEmail(String email) {
        return adminUserRepository.findAllByEmail(email);
    }

    public boolean hasSuperAdmin() {
        return adminUserRepository.countByRoleNameIgnoreCase("SUPER_ADMIN") > 0;
    }
    
    public AdminUser requireById(Long adminId) {
        return adminUserRepository.findById(adminId)
                .orElseThrow(() -> new NoSuchElementException("Admin user not found: " + adminId));
    }

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
