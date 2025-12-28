// src/main/java/com/build4all/business/service/BusinessService.java
package com.build4all.business.service;

import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.admin.repository.AdminUsersRepository;
import com.build4all.business.domain.*;
import com.build4all.business.dto.LowRatedBusinessDTO;
import com.build4all.business.repository.*;
import com.build4all.catalog.domain.Item;
import com.build4all.catalog.repository.ItemRepository;
import com.build4all.notifications.service.EmailService;
import com.build4all.order.repository.OrderItemRepository;
import com.build4all.review.domain.Review;
import com.build4all.review.repository.ReviewRepository;
import com.build4all.role.domain.Role;
import com.build4all.role.repository.RoleRepository;
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

@Service // Spring service: business logic layer for "Businesses" (registration, updates, cleanup, invites, etc.)
public class BusinessService {

    @Autowired private BusinessesRepository businessRepository;              // CRUD + tenant-aware queries for Businesses
    @Autowired private PasswordEncoder passwordEncoder;                      // Secure hashing for passwords
    @Autowired private ItemRepository itemRepository;                        // Items owned by a business
    @Autowired private OrderItemRepository OrderItemRepository;              // OrderItem operations (delete by item, analytics, etc.)
    @Autowired private ReviewRepository reviewRepository;                    // Reviews linked to items/business
    @Autowired private AdminUsersRepository adminUsersRepository;            // Admin users table (used during business delete + invite registration)
    @Autowired private PendingBusinessRepository pendingBusinessRepository;  // Temporary table for business registration (step 1 verification)
    @Autowired private PendingManagerRepository pendingManagerRepository;    // Temporary table for staff invite/registration (kept name for backward compat)
    @Autowired private RoleRepository roleRepository;                        // Role table (BUSINESS, OWNER, SUPER_ADMIN, USER)
    @Autowired private BusinessStatusRepository businessStatusRepository;    // BusinessStatus table (ACTIVE, INACTIVE, DELETED, ...)
    @Autowired private AdminUserProjectRepository adminUserProjectRepository;// Tenant link table (app/tenant context)

    private final EmailService emailService; // Email sending (verification codes, invites)
    public BusinessService(EmailService emailService) { this.emailService = emailService; }

    /**
     * In-memory reset code store:
     * - Key: email
     * - Value: 6-digit code
     *
     * NOTE: This will be LOST if the server restarts.
     * For production: store in DB/Redis with TTL.
     */
    private final Map<String, String> resetCodes = new ConcurrentHashMap<>();

    /* =====================================================================
     * Tenant helper
     * ===================================================================== */

    /**
     * Ensures the tenant/app (AdminUserProject) exists.
     * Used as a guard before performing tenant-scoped operations.
     *
     * IMPORTANT:
     * - Multi-tenant Build4All pattern:
     *   Businesses.ownerProjectLink (ManyToOne AdminUserProject) -> stored physically in DB as aup_id.
     * - We always validate the tenant link before tenant-scoped actions.
     */
    private AdminUserProject requireOwnerProject(Long ownerProjectLinkId) {
        if (ownerProjectLinkId == null) throw new IllegalArgumentException("ownerProjectLinkId is required");
        return adminUserProjectRepository.findById(ownerProjectLinkId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid ownerProjectLinkId"));
    }

    /* =====================================================================
     * Tenant-aware finders (IMPORTANT for LOGIN/AUTH)
     * ===================================================================== */

    /**
     * ✅ Tenant-aware lookup by identifier for AUTH (email OR phone).
     *
     * NOTE:
     * - Always prefer these tenant-aware methods for login/authentication.
     * - Avoid calling legacy/global finders for login because it breaks multi-tenancy.
     *
     * Email case-sensitivity note:
     * - Emails should be treated as case-insensitive.
     * - We use repository method findByOwnerProjectLink_IdAndEmailIgnoreCase(...)
     *   to avoid duplicates like Test@x.com vs test@x.com for the same tenant.
     *
     * - If identifier contains "@": treated as email
     * - Otherwise: treated as phone
     */
    public Optional<Businesses> findByEmailOptional(Long ownerProjectLinkId, String identifier) {
        if (ownerProjectLinkId == null) return Optional.empty();
        if (identifier == null || identifier.isBlank()) return Optional.empty();

        String id = identifier.trim();

        if (id.contains("@")) {
            // SQL idea (conceptual):
            // SELECT * FROM businesses
            // WHERE aup_id = :ownerProjectLinkId AND LOWER(email) = LOWER(:id)
            // LIMIT 1;
            return businessRepository.findByOwnerProjectLink_IdAndEmailIgnoreCase(ownerProjectLinkId, id);
        } else {
            // SQL idea:
            // SELECT * FROM businesses
            // WHERE aup_id = :ownerProjectLinkId AND phone_number = :id
            // LIMIT 1;
            return businessRepository.findByOwnerProjectLink_IdAndPhoneNumber(ownerProjectLinkId, id);
        }
    }

    /**
     * Tenant-aware lookup by identifier (throws if not found).
     */
    public Businesses findByEmail(Long ownerProjectLinkId, String identifier) {
        if (ownerProjectLinkId == null) throw new IllegalArgumentException("ownerProjectLinkId is required");
        if (identifier == null || identifier.isBlank())
            throw new IllegalArgumentException("Identifier cannot be null or empty");

        String id = identifier.trim();

        return id.contains("@")
                ? businessRepository.findByOwnerProjectLink_IdAndEmailIgnoreCase(ownerProjectLinkId, id)
                .orElseThrow(() -> new RuntimeException("Business not found with email: " + id))
                : businessRepository.findByOwnerProjectLink_IdAndPhoneNumber(ownerProjectLinkId, id)
                .orElseThrow(() -> new RuntimeException("Business not found with phone: " + id));
    }

    /**
     * Tenant-aware lookup strictly by email.
     */
    public Businesses getByEmailOrThrow(Long ownerProjectLinkId, String email) {
        if (ownerProjectLinkId == null) throw new IllegalArgumentException("ownerProjectLinkId is required");
        if (email == null || email.isBlank()) throw new IllegalArgumentException("email is required");

        String e = email.trim();

        return businessRepository.findByOwnerProjectLink_IdAndEmailIgnoreCase(ownerProjectLinkId, e)
                .orElseThrow(() -> new RuntimeException("Business not found with email: " + e));
    }

    /* =====================================================================
     * Legacy global finders (keep for backward compat)
     * ===================================================================== */

    /**
     * Legacy/global lookup (NOT tenant-scoped).
     * Used to keep old endpoints working.
     *
     * ⚠️ WARNING:
     * - Do NOT use this method for login/auth in a multi-tenant system.
     * - Same email/phone may exist in different apps.
     *
     * Email case-sensitivity note:
     * - We use findByEmailIgnoreCase here (if email is used) to reduce issues.
     */
    public Optional<Businesses> findByEmailOptional(String identifier) {
        if (identifier == null || identifier.isBlank()) return Optional.empty();
        String id = identifier.trim();

        return id.contains("@")
                ? businessRepository.findByEmailIgnoreCase(id)   // SELECT * FROM businesses WHERE LOWER(email)=LOWER(:id)
                : businessRepository.findByPhoneNumber(id);      // SELECT * FROM businesses WHERE phone_number=:id
    }

    /**
     * Legacy/global lookup by identifier (throws if not found).
     *
     * ⚠️ WARNING:
     * - Not tenant-safe.
     */
    public Businesses findByEmail(String identifier) {
        if (identifier == null || identifier.isBlank())
            throw new IllegalArgumentException("Identifier cannot be null or empty");
        String id = identifier.trim();

        return id.contains("@")
                ? businessRepository.findByEmailIgnoreCase(id)
                .orElseThrow(() -> new RuntimeException("Business not found with: " + id))
                : businessRepository.findByPhoneNumber(id)
                .orElseThrow(() -> new RuntimeException("Business not found with: " + id));
    }

    /**
     * Legacy/global lookup strictly by email (throws if not found).
     *
     * ⚠️ WARNING:
     * - Not tenant-safe.
     */
    public Businesses findByEmailOrThrow(String email) {
        if (email == null || email.isBlank()) throw new IllegalArgumentException("email is required");
        String e = email.trim();

        return businessRepository.findByEmailIgnoreCase(e)
                .orElseThrow(() -> new RuntimeException("Business not found with email: " + e));
    }

    /* =====================================================================
     * Save (tenant-aware overload keeps legacy version intact)
     * ===================================================================== */

    /**
     * Tenant-aware save:
     * - Attaches the business to the tenant (AdminUserProject)
     * - Enforces tenant-scoped uniqueness for:
     *   - business name
     *   - email (case-insensitive)
     *   - phone number
     *
     * Recommendation:
     * - Emails should be normalized at save-time (lowercase) OR enforced with DB unique index on LOWER(email).
     * - Here we at least enforce via IgnoreCase repository methods.
     */
    public Businesses save(Long ownerProjectLinkId, Businesses business) {
        AdminUserProject app = requireOwnerProject(ownerProjectLinkId);
        business.setOwnerProjectLink(app);

        // Scoped uniqueness: business name unique per tenant/app
        if (business.getBusinessName() != null) {
            boolean nameTaken = business.getId() == null
                    ? businessRepository.existsByOwnerProjectLink_IdAndBusinessNameIgnoreCase(
                    ownerProjectLinkId, business.getBusinessName())
                    : businessRepository.existsByOwnerProjectLink_IdAndBusinessNameIgnoreCaseAndIdNot(
                    ownerProjectLinkId, business.getBusinessName(), business.getId());

            if (nameTaken) throw new IllegalArgumentException("Business name already exists for this app!");
        }

        // Scoped uniqueness: email unique per tenant/app (when provided) - CASE INSENSITIVE
        if (business.getEmail() != null) {
            String email = business.getEmail().trim();
            business.setEmail(email);

            boolean emailTaken = business.getId() == null
                    ? businessRepository.existsByOwnerProjectLink_IdAndEmailIgnoreCase(ownerProjectLinkId, email)
                    : businessRepository.findByOwnerProjectLink_IdAndEmailIgnoreCase(ownerProjectLinkId, email)
                    .filter(b -> !Objects.equals(b.getId(), business.getId()))
                    .isPresent();

            if (emailTaken) throw new IllegalArgumentException("Email already exists for this app!");
        }

        // Scoped uniqueness: phone unique per tenant/app (when provided)
        if (business.getPhoneNumber() != null) {
            String phone = business.getPhoneNumber().trim();
            business.setPhoneNumber(phone);

            boolean phoneTaken = business.getId() == null
                    ? businessRepository.existsByOwnerProjectLink_IdAndPhoneNumber(ownerProjectLinkId, phone)
                    : businessRepository.findByOwnerProjectLink_IdAndPhoneNumber(ownerProjectLinkId, phone)
                    .filter(b -> !Objects.equals(b.getId(), business.getId()))
                    .isPresent();

            if (phoneTaken) throw new IllegalArgumentException("Phone already exists for this app!");
        }

        // Persists business (INSERT or UPDATE)
        return businessRepository.save(business);
    }

    /**
     * Legacy save (still available if you don’t pass ownerProjectLinkId anywhere).
     *
     * ⚠️ WARNING:
     * - Not tenant-safe uniqueness.
     * - Kept to avoid breaking legacy code paths.
     *
     * Email note:
     * - Uses findByEmailIgnoreCase to avoid duplicates across case.
     */
    public Businesses save(Businesses business) {
        if (business.getId() != null && business.getEmail() != null) {
            String email = business.getEmail().trim();
            business.setEmail(email);

            Optional<Businesses> existing = businessRepository.findByEmailIgnoreCase(email);
            if (existing.isPresent() && !existing.get().getId().equals(business.getId())) {
                throw new IllegalArgumentException("Email already exists for another business!");
            }
        }
        return businessRepository.save(business);
    }

    public Businesses findById(Long id) { return businessRepository.findById(id).orElse(null); } // SELECT * FROM businesses WHERE business_id=:id
    public List<Businesses> findAll() { return businessRepository.findAll(); }                   // SELECT * FROM businesses

    /* =====================================================================
     * Delete with cleanup
     * ===================================================================== */

    /**
     * Deletes a business AND its dependent data.
     *
     * Why this exists:
     * - Some relations might not be configured with cascading deletes
     * - There are cross-table records (order_items, reviews, items) that must be cleaned
     *
     * IMPORTANT:
     * - If your DB is properly configured with FK ON DELETE CASCADE everywhere,
     *   most of this can be simplified.
     */
    @Transactional
    public void delete(Long businessId) {
        Businesses business = businessRepository.findById(businessId)
                .orElseThrow(() -> new RuntimeException("Business not found"));

        // Delete items + dependent order_items/reviews
        List<Item> items = itemRepository.findByBusinessId(businessId);
        for (Item item : items) {
            Long itemId = item.getId();

            // Delete OrderItem rows linked to this item
            OrderItemRepository.deleteByItem_Id(itemId);

            // Delete Review rows linked to this item
            reviewRepository.deleteByItem_Id(itemId);

            // Delete the item itself
            itemRepository.deleteById(itemId);
        }

        // If AdminUser has business_id FK and you don't rely on DB ON DELETE CASCADE, keep this:
        adminUsersRepository.deleteByBusiness_Id(businessId);

        // Finally delete the business
        businessRepository.deleteById(businessId);
    }

    /* =====================================================================
     * Registration / verification (pending stays global)
     * ===================================================================== */

    /**
     * Step 1 of business registration:
     * - Validate tenant/app exists
     * - Validate identifier (email OR phone)
     * - Ensure uniqueness INSIDE tenant (email case-insensitive)
     * - Create/update PendingBusiness record
     * - Send verification code (email) or log it (SMS dev path)
     *
     * Returns pendingBusinessId used later in completeBusinessProfile().
     *
     * NOTE:
     * - PendingBusiness is currently GLOBAL (not tenant-scoped in DB).
     * - If you want true multi-tenant pending registration, you should add aup_id to pending_businesses too.
     */
    public Long sendBusinessVerificationCode(Long ownerProjectLinkId, Map<String, String> data) {
        requireOwnerProject(ownerProjectLinkId);

        String email = data.get("email");
        String phone = data.get("phoneNumber");
        String password = data.get("password");
        String statusStr = data.get("status");
        String isPublicProfileStr = data.get("isPublicProfile");

        if (password == null || password.trim().isEmpty()) {
            throw new RuntimeException("Password is required");
        }

        // Validate one identifier only
        boolean emailProvided = email != null && !email.isBlank();
        boolean phoneProvided = phone != null && !phone.isBlank();

        if (!emailProvided && !phoneProvided)
            throw new RuntimeException("Provide either email or phone.");
        if (emailProvided && phoneProvided)
            throw new RuntimeException("Only one of email or phone should be provided.");

        if (emailProvided) email = email.trim();
        if (phoneProvided) phone = phone.trim();

        // Resolve status entity (defaults to ACTIVE)
        BusinessStatus status = businessStatusRepository.findByNameIgnoreCase(
                statusStr != null ? statusStr.toUpperCase() : "ACTIVE"
        ).orElseThrow(() -> new RuntimeException("Invalid or missing status"));

        // App-scoped uniqueness (business table)
        // - email should be checked ignore-case
        if (emailProvided && businessRepository.findByOwnerProjectLink_IdAndEmailIgnoreCase(ownerProjectLinkId, email).isPresent())
            throw new RuntimeException("Email already registered for this app.");
        if (phoneProvided && businessRepository.findByOwnerProjectLink_IdAndPhoneNumber(ownerProjectLinkId, phone).isPresent())
            throw new RuntimeException("Phone already registered for this app.");

        // Verification code: fixed for phone in dev, random for email
        String code = phoneProvided ? "123456" : String.format("%06d", new Random().nextInt(999999));

        // PendingBusiness is GLOBAL by email/phone (no tenant)
        PendingBusiness pending;
        if (emailProvided) {
            pending = pendingBusinessRepository.findByEmail(email);
            if (pending == null) { pending = new PendingBusiness(); pending.setEmail(email); }
        } else {
            pending = pendingBusinessRepository.findByPhoneNumber(phone);
            if (pending == null) { pending = new PendingBusiness(); pending.setPhoneNumber(phone); }
        }

        // Store encoded password (never store raw password)
        pending.setPasswordHash(passwordEncoder.encode(password));
        pending.setVerificationCode(code);
        pending.setCreatedAt(LocalDateTime.now());
        pending.setStatus(status);

        // Default: true if not provided (or parse boolean)
        pending.setIsPublicProfile(isPublicProfileStr == null || Boolean.parseBoolean(isPublicProfileStr));

        PendingBusiness saved = pendingBusinessRepository.save(pending);

        // Send code
        if (emailProvided) {
            String html = """
                <html><body style="font-family: Arial; text-align:center; padding:20px">
                <h2 style="color:#4CAF50">Welcome to build4all Business!</h2>
                <p>Please use the code below to verify your business email:</p>
                <h1 style="color:#2196F3">%s</h1>
                <p>This code will expire in 10 minutes.</p>
                </body></html>
            """.formatted(code);
            emailService.sendHtmlEmail(email, "Business Verification Code", html);
        } else {
            System.out.println("📱 SMS to " + phone + ": code " + code);
        }

        return saved.getId();
    }

    /**
     * Verify email code for a pending business.
     * NOTE: PendingBusiness lookup is GLOBAL by email (not tenant-scoped).
     */
    public Long verifyBusinessEmailCode(String email, String code) {
        if (email == null || email.isBlank()) throw new IllegalArgumentException("email is required");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code is required");

        PendingBusiness pending = pendingBusinessRepository.findByEmail(email.trim());
        if (pending == null || !Objects.equals(pending.getVerificationCode(), code))
            throw new RuntimeException("Invalid verification code");

        pending.setIsVerified(true);
        pendingBusinessRepository.save(pending);
        return pending.getId();
    }

    /**
     * Verify phone code for a pending business.
     * NOTE: PendingBusiness lookup is GLOBAL by phone (not tenant-scoped).
     */
    public Long verifyBusinessPhoneCode(String phone, String code) {
        if (phone == null || phone.isBlank()) throw new IllegalArgumentException("phone is required");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code is required");

        PendingBusiness pending = pendingBusinessRepository.findByPhoneNumber(phone.trim());
        if (pending == null || !Objects.equals(pending.getVerificationCode(), code))
            throw new RuntimeException("Invalid verification code");

        pending.setIsVerified(true);
        pendingBusinessRepository.save(pending);
        return pending.getId();
    }

    /**
     * Resend verification code to pending business (email or phone).
     * NOTE: PendingBusiness lookup is GLOBAL by email/phone.
     */
    public boolean resendBusinessVerificationCode(String emailOrPhone) {
        if (emailOrPhone == null || emailOrPhone.isBlank()) throw new IllegalArgumentException("emailOrPhone is required");

        String id = emailOrPhone.trim();
        boolean isEmail = id.contains("@");

        PendingBusiness pending = isEmail
                ? pendingBusinessRepository.findByEmail(id)
                : pendingBusinessRepository.findByPhoneNumber(id);

        if (pending == null) throw new RuntimeException("No pending business found with this " + (isEmail ? "email" : "phone"));

        String code = isEmail ? String.format("%06d", new Random().nextInt(999999)) : "123456";
        pending.setVerificationCode(code);
        pending.setCreatedAt(LocalDateTime.now());
        pendingBusinessRepository.save(pending);

        if (isEmail) {
            String html = """
                <html><body style="font-family: Arial; text-align:center; padding:20px">
                <h2 style="color:#4CAF50">Verification Code Resent</h2>
                <h1 style="color:#2196F3">%s</h1>
                </body></html>
            """.formatted(code);
            emailService.sendHtmlEmail(id, "Resend Business Verification Code", html);
        } else {
            System.out.println("📱 Resending SMS to " + id + ": " + code);
        }
        return true;
    }

    /**
     * Step 2 of business registration:
     * - Load PendingBusiness
     * - Create Businesses entity and attach it to tenant/app
     * - Store optional assets (logo/banner) into /uploads
     * - Assign role "BUSINESS"
     * - Save real business, delete pending record
     */
    public Businesses completeBusinessProfile(
            Long ownerProjectLinkId,
            Long pendingId,
            String businessName,
            String description,
            String websiteUrl,
            MultipartFile logo,
            MultipartFile banner
    ) throws IOException {

        AdminUserProject app = requireOwnerProject(ownerProjectLinkId);

        PendingBusiness pending = pendingBusinessRepository.findById(pendingId)
                .orElseThrow(() -> new RuntimeException("Pending business not found."));

        // ✅ Allowed roles in your system: SUPER_ADMIN, OWNER, BUSINESS, USER
        Role businessRole = roleRepository.findByNameIgnoreCase("BUSINESS")
                .orElseThrow(() -> new RuntimeException("Role BUSINESS not found"));

        Businesses business = new Businesses();
        business.setOwnerProjectLink(app);

        // NOTE:
        // - pending.getEmail() may already be normalized; still safe to keep as-is.
        // - save(ownerProjectLinkId, business) will enforce uniqueness.
        business.setEmail(pending.getEmail());
        business.setPhoneNumber(pending.getPhoneNumber());
        business.setPasswordHash(pending.getPasswordHash());
        business.setStatus(pending.getStatus());
        business.setIsPublicProfile(pending.getIsPublicProfile());
        business.setBusinessName(businessName);
        business.setDescription(description);
        business.setWebsiteUrl(websiteUrl);
        business.setRole(businessRole);
        business.setCreatedAt(LocalDateTime.now());
        business.setUpdatedAt(LocalDateTime.now());

        Path uploadDir = Paths.get("uploads");
        if (!Files.exists(uploadDir)) Files.createDirectories(uploadDir);

        if (logo != null && !logo.isEmpty()) {
            String file = UUID.randomUUID() + "_" + logo.getOriginalFilename();
            Files.copy(logo.getInputStream(), uploadDir.resolve(file), StandardCopyOption.REPLACE_EXISTING);
            business.setBusinessLogoUrl("/uploads/" + file);
        }

        if (banner != null && !banner.isEmpty()) {
            String file = UUID.randomUUID() + "_" + banner.getOriginalFilename();
            Files.copy(banner.getInputStream(), uploadDir.resolve(file), StandardCopyOption.REPLACE_EXISTING);
            business.setBusinessBannerUrl("/uploads/" + file);
        }

        // Tenant-aware save enforces tenant uniqueness (including email ignore-case)
        Businesses saved = save(ownerProjectLinkId, business);

        // Remove pending record after successful completion
        pendingBusinessRepository.delete(pending);

        return saved;
    }

    /**
     * Tenant-aware update for business + optional images.
     * - Checks that the business belongs to the passed ownerProjectLinkId
     * - Enforces tenant uniqueness for email/phone (when changed)
     *
     * Email note:
     * - Use findByOwnerProjectLink_IdAndEmailIgnoreCase to avoid case duplicates.
     */
    public Businesses updateBusinessWithImages(
            Long ownerProjectLinkId,
            Long id,
            String name,
            String email,
            String password,
            String description,
            String phoneNumber,
            String websiteUrl,
            MultipartFile logo,
            MultipartFile banner
    ) throws IOException {

        // ✅ Allowed roles in your system: SUPER_ADMIN, OWNER, BUSINESS, USER
        Role businessRole = roleRepository.findByNameIgnoreCase("BUSINESS")
                .orElseThrow(() -> new RuntimeException("Role BUSINESS not found"));

        Businesses existing = businessRepository.findById(id).orElse(null);
        if (existing == null) throw new IllegalArgumentException("Business with ID " + id + " not found.");

        // IMPORTANT: ensure you are not updating a business across tenants
        if (existing.getOwnerProjectLink() == null || !Objects.equals(existing.getOwnerProjectLink().getId(), ownerProjectLinkId)) {
            throw new IllegalArgumentException("Business does not belong to the specified app.");
        }

        // Email update: enforce tenant uniqueness (ignore case)
        if (email != null) {
            String e = email.trim();
            Optional<Businesses> byEmail = businessRepository.findByOwnerProjectLink_IdAndEmailIgnoreCase(ownerProjectLinkId, e);
            if (byEmail.isPresent() && !Objects.equals(byEmail.get().getId(), id)) {
                throw new IllegalArgumentException("Email already exists for this app!");
            }
            existing.setEmail(e);
        }

        // Phone update: enforce tenant uniqueness
        if (phoneNumber != null) {
            String p = phoneNumber.trim();
            Optional<Businesses> byPhone = businessRepository.findByOwnerProjectLink_IdAndPhoneNumber(ownerProjectLinkId, p);
            if (byPhone.isPresent() && !Objects.equals(byPhone.get().getId(), id)) {
                throw new IllegalArgumentException("Phone already exists for this app!");
            }
            existing.setPhoneNumber(p);
        }

        existing.setBusinessName(name);

        // Update password only if provided
        if (password != null && !password.trim().isEmpty()) {
            if (password.length() < 6) throw new IllegalArgumentException("Password must be at least 6 characters long.");
            existing.setPasswordHash(passwordEncoder.encode(password));
        }

        existing.setDescription(description);
        existing.setWebsiteUrl(websiteUrl);
        existing.setRole(businessRole);
        existing.setUpdatedAt(LocalDateTime.now());

        Path uploadDir = Paths.get("uploads/");
        if (!Files.exists(uploadDir)) Files.createDirectories(uploadDir);

        if (logo != null && !logo.isEmpty()) {
            String f = UUID.randomUUID() + "_" + logo.getOriginalFilename();
            Files.copy(logo.getInputStream(), uploadDir.resolve(f), StandardCopyOption.REPLACE_EXISTING);
            existing.setBusinessLogoUrl("/uploads/" + f);
        }

        if (banner != null && !banner.isEmpty()) {
            String f = UUID.randomUUID() + "_" + banner.getOriginalFilename();
            Files.copy(banner.getInputStream(), uploadDir.resolve(f), StandardCopyOption.REPLACE_EXISTING);
            existing.setBusinessBannerUrl("/uploads/" + f);
        }

        return businessRepository.save(existing);
    }

    /**
     * Self-delete (business confirms password).
     * - Checks password hash
     * - Calls delete(businessId) which cleans dependent records
     */
    @Transactional
    public boolean deleteBusinessByIdWithPassword(Long id, String password) {
        Optional<Businesses> opt = businessRepository.findById(id);
        if (opt.isEmpty()) return false;

        Businesses b = opt.get();
        if (!passwordEncoder.matches(password, b.getPasswordHash())) return false;

        delete(id);
        return true;
    }

    /* =====================================================================
     * Password reset (legacy-global by email)
     * ===================================================================== */

    /**
     * Legacy password reset (global).
     * NOTE: Tenant-aware reset is not implemented here.
     *
     * Email note:
     * - We use findByEmailIgnoreCase to allow users to enter email in any case.
     * - resetCodes map key is stored as trimmed email (not lowercased) to keep consistent behavior.
     *   If you want stronger consistency, use e.toLowerCase() as the map key.
     */
    public boolean resetPassword(String email) {
        if (email == null || email.isBlank()) return false;

        String e = email.trim();

        Optional<Businesses> optional = businessRepository.findByEmailIgnoreCase(e);
        if (optional.isEmpty()) return false;

        String code = String.format("%06d", new Random().nextInt(999999));
        resetCodes.put(e, code);

        String htmlMessage = """
            <html><body style="font-family: Arial; text-align: center; padding: 20px;">
                <h2 style="color: #FF9800;">Reset Your Password</h2>
                <p>Use the code below to proceed:</p>
                <h1 style="color: #2196F3;">%s</h1>
                <p style="font-size: 14px; color: #777;">This code will expire in 10 minutes.</p>
            </body></html>
        """.formatted(code);

        emailService.sendHtmlEmail(e, "Password Reset Code", htmlMessage);
        return true;
    }

    public boolean verifyResetCode(String email, String code) {
        if (email == null || email.isBlank()) return false;
        if (code == null || code.isBlank()) return false;

        String e = email.trim();
        return resetCodes.containsKey(e) && Objects.equals(resetCodes.get(e), code);
    }

    public boolean updatePasswordDirectly(String email, String newPassword) {
        if (email == null || email.isBlank()) return false;
        if (newPassword == null || newPassword.trim().isEmpty()) return false;

        String e = email.trim();

        Optional<Businesses> optional = businessRepository.findByEmailIgnoreCase(e);
        if (optional.isEmpty()) return false;

        Businesses b = optional.get();
        b.setPasswordHash(passwordEncoder.encode(newPassword));
        businessRepository.save(b);

        resetCodes.remove(e);
        return true;
    }

    /* =====================================================================
     * Legacy overload kept for backward compatibility
     * ===================================================================== */

    /**
     * Legacy overload:
     * - Keeps compatibility with older controller calls that update by businessId only.
     * - If the business has a tenant link, we delegate to the tenant-aware method.
     * - Otherwise we fall back to global behavior.
     */
    public Businesses updateBusinessWithImages(
            Long id,
            String name,
            String email,
            String password,
            String description,
            String phoneNumber,
            String websiteUrl,
            MultipartFile logo,
            MultipartFile banner
    ) throws IOException {

        Businesses existing = businessRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Business with ID " + id + " not found."));

        // ✅ Allowed roles in your system: SUPER_ADMIN, OWNER, BUSINESS, USER
        Role businessRole = roleRepository.findByNameIgnoreCase("BUSINESS")
                .orElseThrow(() -> new RuntimeException("Role BUSINESS not found"));

        Long ownerProjectLinkId = (existing.getOwnerProjectLink() != null)
                ? existing.getOwnerProjectLink().getId()
                : null;

        // Delegate to tenant-aware method if the record is tenant-scoped
        if (ownerProjectLinkId != null) {
            return updateBusinessWithImages(
                    ownerProjectLinkId, id, name, email, password, description, phoneNumber, websiteUrl, logo, banner
            );
        }

        // ---- Fallback to legacy (global) behavior when no tenant link exists ----
        if (email != null) {
            String e = email.trim();
            Optional<Businesses> byEmail = businessRepository.findByEmailIgnoreCase(e);
            if (byEmail.isPresent() && !Objects.equals(byEmail.get().getId(), id)) {
                throw new IllegalArgumentException("Email already exists for another business!");
            }
            existing.setEmail(e);
        }

        if (phoneNumber != null) existing.setPhoneNumber(phoneNumber.trim());

        existing.setBusinessName(name);
        existing.setDescription(description);
        existing.setWebsiteUrl(websiteUrl);
        existing.setRole(businessRole);
        existing.setUpdatedAt(LocalDateTime.now());

        if (password != null && !password.trim().isEmpty()) {
            if (password.length() < 6) throw new IllegalArgumentException("Password must be at least 6 characters long.");
            existing.setPasswordHash(passwordEncoder.encode(password));
        }

        Path uploadDir = Paths.get("uploads/");
        if (!Files.exists(uploadDir)) Files.createDirectories(uploadDir);

        if (logo != null && !logo.isEmpty()) {
            String fileName = UUID.randomUUID() + "_" + logo.getOriginalFilename();
            Files.copy(logo.getInputStream(), uploadDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            existing.setBusinessLogoUrl("/uploads/" + fileName);
        }

        if (banner != null && !banner.isEmpty()) {
            String fileName = UUID.randomUUID() + "_" + banner.getOriginalFilename();
            Files.copy(banner.getInputStream(), uploadDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            existing.setBusinessBannerUrl("/uploads/" + fileName);
        }

        return businessRepository.save(existing);
    }

    /* =====================================================================
     * Assets
     * ===================================================================== */

    /**
     * Deletes the physical logo file (if stored locally) and nulls the DB field.
     * NOTE: For safety, this assumes local path mapping from "/uploads/..." -> "uploads/..."
     */
    public boolean deleteBusinessLogo(Long businessId) {
        Businesses b = businessRepository.findById(businessId)
                .orElseThrow(() -> new RuntimeException("Business not found"));

        if (b.getBusinessLogoUrl() != null && !b.getBusinessLogoUrl().isEmpty()) {
            String logoPath = b.getBusinessLogoUrl().replace("/uploads", "uploads");
            try { Files.deleteIfExists(Paths.get(logoPath)); }
            catch (IOException e) { throw new RuntimeException("Failed to delete logo: " + e.getMessage()); }

            b.setBusinessLogoUrl(null);
            b.setUpdatedAt(LocalDateTime.now());
            businessRepository.save(b);
            return true;
        }
        return false;
    }

    public boolean deleteBusinessBanner(Long businessId) {
        Businesses b = businessRepository.findById(businessId)
                .orElseThrow(() -> new RuntimeException("Business not found"));

        if (b.getBusinessBannerUrl() != null && !b.getBusinessBannerUrl().isEmpty()) {
            String p = b.getBusinessBannerUrl().replace("/uploads", "uploads");
            try { Files.deleteIfExists(Paths.get(p)); }
            catch (IOException e) { throw new RuntimeException("Failed to delete banner: " + e.getMessage()); }

            b.setBusinessBannerUrl(null);
            b.setUpdatedAt(LocalDateTime.now());
            businessRepository.save(b);
            return true;
        }
        return false;
    }

    /* =====================================================================
     * Listings / housekeeping
     * ===================================================================== */

    public List<Businesses> getAllPublicActiveBusinesses(Long ownerProjectLinkId) {
        BusinessStatus active = businessStatusRepository.findByNameIgnoreCase("ACTIVE")
                .orElseThrow(() -> new RuntimeException("ACTIVE status not found"));
        return businessRepository.findByOwnerProjectLink_IdAndIsPublicProfileTrueAndStatus(ownerProjectLinkId, active);
    }

    // Keep legacy global list (no tenant header)
    public List<Businesses> getAllPublicActiveBusinesses() {
        BusinessStatus active = businessStatusRepository.findByNameIgnoreCase("ACTIVE")
                .orElseThrow(() -> new RuntimeException("ACTIVE status not found"));
        return businessRepository.findByIsPublicProfileTrueAndStatus(active);
    }

    /**
     * Nightly job: soft-delete businesses that stayed INACTIVE for 30+ days.
     * - Converts status INACTIVE -> DELETED
     * - Updates updatedAt
     *
     * NOTE: Current implementation loads ALL businesses then filters in memory.
     * Better: create a repository query that selects only matching businesses.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void deleteInactiveBusinessesOlderThan30Days() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);

        BusinessStatus deleted = businessStatusRepository.findByNameIgnoreCase("DELETED")
                .orElseThrow(() -> new RuntimeException("DELETED status not found"));

        List<Businesses> toSoftDelete = businessRepository.findAll().stream()
                .filter(b -> b.getStatus() != null && "INACTIVE".equalsIgnoreCase(b.getStatus().getName()))
                .filter(b -> b.getUpdatedAt() != null && b.getUpdatedAt().isBefore(cutoffDate))
                .toList();

        for (Businesses b : toSoftDelete) {
            b.setStatus(deleted);
            b.setUpdatedAt(LocalDateTime.now());
            businessRepository.save(b);
        }
    }

    /**
     * Nightly job: permanently delete businesses that stayed DELETED for 90+ days.
     * NOTE: Also loads all businesses in memory; consider DB query for performance.
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void permanentlyDeleteBusinessesAfter90Days() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);

        List<Businesses> toDelete = businessRepository.findAll().stream()
                .filter(b -> b.getStatus() != null && "DELETED".equalsIgnoreCase(b.getStatus().getName()))
                .filter(b -> b.getUpdatedAt() != null && b.getUpdatedAt().isBefore(cutoff))
                .toList();

        for (Businesses b : toDelete) businessRepository.delete(b);
    }

    /* =====================================================================
     * Misc / legacy wrappers
     * ===================================================================== */

    public Optional<Businesses> findByIdOptional(Long id) { return businessRepository.findById(id); }

    public BusinessStatus getStatusByName(String name) {
        return businessStatusRepository.findByNameIgnoreCase(name.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Status '" + name + "' not found"));
    }

    public boolean existsByBusinessName(String businessName) {
        return businessRepository.existsByBusinessNameIgnoreCase(businessName);
    }

    public boolean existsByBusinessNameIgnoreCaseAndIdNot(String name, Long id) {
        return businessRepository.existsByBusinessNameIgnoreCaseAndIdNot(name, id);
    }

    public boolean verifyPassword(Long businessId, String rawPassword) {
        return businessRepository.findById(businessId)
                .map(b -> passwordEncoder.matches(rawPassword, b.getPasswordHash()))
                .orElse(false);
    }

    public BusinessUser findBusinessUserById(Long businessUserId) {
        // TODO: This method currently returns null.
        // Either implement using BusinessUserRepository, or remove to avoid confusion.
        return null;
    }

    /**
     * Computes low-rated businesses from review averages.
     * NOTE: This loads all businesses and reviews; consider an aggregate query for performance.
     */
    public List<LowRatedBusinessDTO> getLowRatedBusinesses() {
        List<Businesses> businesses = businessRepository.findAll();
        List<LowRatedBusinessDTO> result = new ArrayList<>();

        for (Businesses b : businesses) {
            List<Review> reviews = reviewRepository.findByBusinessId(b.getId());
            if (reviews.isEmpty()) continue;

            double avg = reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);

            if (avg <= 3.0) {
                result.add(new LowRatedBusinessDTO(
                        b.getId(),
                        b.getBusinessName(),
                        b.getStatus() != null ? b.getStatus().getName() : "UNKNOWN",
                        avg
                ));
            }
        }
        return result;
    }

    /* =====================================================================
     * Staff invite + registration
     * ===================================================================== */

    /**
     * Sends an invite email to become a business staff/admin user.
     *
     * NOTE (important):
     * - You said you DO NOT want role MANAGER in authorization.
     * - So we keep the PendingManager table/class name for backward compatibility,
     *   BUT we will create the AdminUser with role "BUSINESS" (allowed roles).
     */
    public void sendManagerInvite(String email, Businesses business) {
        if (email == null || email.isBlank()) throw new IllegalArgumentException("email is required");
        if (business == null) throw new IllegalArgumentException("business is required");

        String token = UUID.randomUUID().toString();

        PendingManager pending = new PendingManager(email.trim(), business, token);
        pendingManagerRepository.save(pending);

        // If you have a configured domain, swap the link below
        String inviteLink = "http://localhost:5173/assign-manager?token=" + token;

        // Text can still say "Manager" if you want UI compatibility,
        // but role-wise we will create BUSINESS staff user.
        String html = """
            <html>
            <body style="font-family: Arial, sans-serif; text-align: center; padding: 20px;">
                <h2 style="color: #4CAF50;">You're Invited!</h2>
                <p>You have been invited to join build4all business staff.</p>
                <p>
                  <a href="%s" style="display:inline-block; padding:10px 20px; background:#2196F3; color:#fff; text-decoration:none; border-radius:5px;">
                    Complete Registration
                  </a>
                </p>
                <p>This link will expire in a few days.</p>
            </body>
            </html>
        """.formatted(inviteLink);

        emailService.sendHtmlEmail(email.trim(), "Business Staff Invitation", html);
    }

    /**
     * Completes staff registration using the invite token:
     * - Reads PendingManager by token
     * - Creates an AdminUser with BUSINESS role (NOT MANAGER)
     * - Links staff to the business
     * - Deletes the pending token (one-time use)
     */
    @Transactional
    public boolean registerManagerFromInvite(String token, String username, String firstName, String lastName, String password) {
        if (token == null || token.isBlank()) return false;

        Optional<PendingManager> pendingOpt = pendingManagerRepository.findByToken(token.trim());
        if (pendingOpt.isEmpty()) return false;

        PendingManager pending = pendingOpt.get();
        Businesses business = pending.getBusiness();

        // ✅ Allowed roles in your system: SUPER_ADMIN, OWNER, BUSINESS, USER
        // We use BUSINESS role for invited business staff.
        Role businessStaffRole = roleRepository.findByNameIgnoreCase("BUSINESS")
                .orElseThrow(() -> new RuntimeException("Role BUSINESS not found"));

        // Prevent duplicates: same email already exists as admin
        if (adminUsersRepository.findByEmail(pending.getEmail()).isPresent()) {
            throw new RuntimeException("User already exists as admin");
        }

        AdminUser newStaff = new AdminUser();
        newStaff.setUsername(username);
        newStaff.setFirstName(firstName);
        newStaff.setLastName(lastName);
        newStaff.setEmail(pending.getEmail());
        newStaff.setPasswordHash(passwordEncoder.encode(password));
        newStaff.setRole(businessStaffRole);
        newStaff.setBusiness(business);
        newStaff.setNotifyItemUpdates(true);
        newStaff.setNotifyUserFeedback(true);
        newStaff.setCreatedAt(LocalDateTime.now());
        newStaff.setUpdatedAt(LocalDateTime.now());

        adminUsersRepository.save(newStaff);

        // Consume token so it can't be reused
        pendingManagerRepository.delete(pending);

        return true;
    }
}
