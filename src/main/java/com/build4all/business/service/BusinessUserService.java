package com.build4all.business.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.build4all.role.domain.Role;
import com.build4all.role.repository.RoleRepository;
import org.springframework.stereotype.Service;

import com.build4all.business.dto.BusinessUserDto;
import com.build4all.business.dto.BusinessUserSimpleDto;
import com.build4all.business.domain.BusinessUser;
import com.build4all.business.repository.BusinessUserRepository;
import com.build4all.business.repository.BusinessesRepository;
import com.build4all.user.repository.UserStatusRepository;

import jakarta.transaction.Transactional;

@Service // Spring service: handles business staff/users lifecycle (create + list + fetch)
public class BusinessUserService {

    private final BusinessUserRepository businessUserRepo; // CRUD for BusinessUser table
    private final BusinessesRepository businessRepo;        // Used to attach BusinessUser to a Businesses record
    private final UserStatusRepository statusRepo;          // Used to resolve user status (e.g., CREATED_BY_BUSINESS)
    private final RoleRepository roleRepository;            // Used to resolve roles (e.g., USER, MANAGER, ...)

    public BusinessUserService(
            BusinessUserRepository businessUserRepo,
            BusinessesRepository businessRepo,
            UserStatusRepository statusRepo,
            RoleRepository roleRepository
    ) {
        this.businessUserRepo = businessUserRepo;
        this.businessRepo = businessRepo;
        this.statusRepo = statusRepo;
        this.roleRepository = roleRepository;
    }

    /**
     * Utility method to load a Role by name (case-insensitive).
     * Throws a RuntimeException if role does not exist in the DB.
     *
     * Example: getRoleOrThrow("USER") -> Role entity for USER
     */
    private Role getRoleOrThrow(String name) {
        return roleRepository.findByNameIgnoreCase(name)
                .orElseThrow(() -> new RuntimeException("Role " + name + " not found"));
    }

    /**
     * Creates a BusinessUser under a given businessId using values from BusinessUserDto.
     *
     * Notes:
     * - Requires at least one identifier: email OR phoneNumber
     * - Assigns a default role (currently "USER")
     * - Assigns a status:
     *   - If dto.statusId provided -> use that
     *   - Else -> default to CREATED_BY_BUSINESS
     * - Attaches to Businesses entity by businessId
     */
    public BusinessUser createBusinessUser(Long businessId, BusinessUserDto dto) {
        // Validate identifiers early (so we fail fast before DB calls)
        if ((dto.getEmail() == null || dto.getEmail().trim().isEmpty()) &&
                (dto.getPhoneNumber() == null || dto.getPhoneNumber().trim().isEmpty())) {
            throw new IllegalArgumentException("Either email or phone number must be provided.");
        }

        BusinessUser user = new BusinessUser();

        // Required fields
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setEmail(dto.getEmail());

        // Role assignment for BusinessUser:
        // - This determines what authorities Spring Security sees from getAuthorities()
        // - Keep consistent with your JWT "role" claim and your security config
        user.setRole(getRoleOrThrow("USER"));

        // Optional fields (can be null)
        user.setUsername(dto.getUsername());
        user.setPasswordHash(dto.getPasswordHash()); // NOTE: Prefer encoding here if dto contains raw password
        user.setPhoneNumber(dto.getPhoneNumber());
        user.setGoogleId(dto.getGoogleId());
        user.setProfilePictureUrl(dto.getProfilePictureUrl());
        user.setIsPublicProfile(dto.getIsPublicProfile());
        user.setCreatedAt(LocalDateTime.now()); // createdAt set at creation time

        // Set status:
        // - If a statusId is sent: attempt to load it
        // - Else use default CREATED_BY_BUSINESS
        if (dto.getStatusId() != null) {
            user.setStatus(statusRepo.findById(dto.getStatusId()).orElse(null));
        } else {
            user.setStatus(
                    statusRepo.findByName("CREATED_BY_BUSINESS")
                            .orElseThrow(() -> new RuntimeException("Default status 'CREATED_BY_BUSINESS' not found"))
            );
        }

        // Attach user to the business (foreign key business_id)
        user.setBusiness(
                businessRepo.findById(businessId)
                        .orElseThrow(() -> new RuntimeException("Business not found"))
        );

        // Role BusinessRole is fetched but NOT used below.
        // Keeping this line as-is because it was in your code,
        // but ideally remove it OR use it (example: setRole(getRoleOrThrow("BUSINESS")) if intended).
        Role BusinessRole = roleRepository.findByNameIgnoreCase("BUSINESS")
                .orElseThrow(() -> new RuntimeException("Role BUSINESS not found"));

        // Persist user (INSERT into business_user table)
        return businessUserRepo.save(user);
    }

    /**
     * Returns a simplified list of users under a business.
     *
     * @Transactional:
     * - Helps when BusinessUserSimpleDto accesses lazy relations (e.g., user.getBusiness().getBusinessName()).
     * - Ensures the session is open while mapping entities -> DTOs.
     */
    @Transactional
    public List<BusinessUserSimpleDto> getUsersByBusiness(Long businessId) {
        List<BusinessUser> users = businessUserRepo.findByBusiness_Id(businessId);

        // Map each BusinessUser -> BusinessUserSimpleDto
        return users.stream()
                .map(BusinessUserSimpleDto::new)
                .collect(Collectors.toList());
    }

    /**
     * Loads one BusinessUser by primary key.
     * Throws if not found.
     */
    public BusinessUser findBusinessUserById(Long businessUserId) {
        return businessUserRepo.findById(businessUserId)
                .orElseThrow(() -> new RuntimeException("Business user not found"));
    }
}
