package com.build4all.business.service;

import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.admin.repository.AdminUsersRepository;
import com.build4all.order.repository.OrderItemRepository;
import com.build4all.business.domain.*;
import com.build4all.business.dto.LowRatedBusinessDTO;
import com.build4all.business.repository.*;
import com.build4all.catalog.domain.Item;
import com.build4all.catalog.repository.ItemRepository;
import com.build4all.notifications.service.EmailService;
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
    @Autowired private AdminUsersRepository adminUsersRepository;            // Admin/manager users table (used during business delete + manager registration)
    @Autowired private PendingBusinessRepository pendingBusinessRepository;  // Temporary table for business registration (step 1 verification)
    @Autowired private PendingManagerRepository pendingManagerRepository;    // Temporary table for manager invite/registration
    @Autowired private RoleRepository roleRepository;                        // Role table (BUSINESS, MANAGER, ...)
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

    /* -------- tenant helper -------- */

    /**
     * Ensures the tenant/app (AdminUserProject) exists.
     * Used as a guard before performing tenant-scoped operations.
     */
    private AdminUserProject requireOwnerProject(Long ownerProjectLinkId) {
        return adminUserProjectRepository.findById(ownerProjectLinkId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid ownerProjectLinkId"));
    }

    /* -------- tenant-aware finders -------- */

    /**
     * Tenant-aware lookup by identifier.
     * - If identifier contains "@": treated as email
     * - Otherwise: treated as phone
     */
    public Optional<Businesses> findByEmailOptional(Long ownerProjectLinkId, String identifier) {
        if (identifier == null || identifier.isBlank()) return Optional.empty();
        if (identifier.contains("@")) {
            // SQL idea:
            // SELECT * FROM businesses WHERE aup_id = :ownerProjectLinkId AND email = :identifier LIMIT 1;
            return businessRepository.findByOwnerProjectLink_IdAndEmail(ownerProjectLinkId, identifier);
        } else {
            // SQL idea:
            // SELECT * FROM businesses WHERE aup_id = :ownerProjectLinkId AND phone_number = :identifier LIMIT 1;
            return businessRepository.findByOwnerProjectLink_IdAndPhoneNumber(ownerProjectLinkId, identifier);
        }
    }

    /**
     * Tenant-aware lookup by identifier (throws if not found).
     */
    public Businesses findByEmail(Long ownerProjectLinkId, String identifier) {
        if (identifier == null || identifier.isBlank())
            throw new IllegalArgumentException("Identifier cannot be null or empty");

        return identifier.contains("@")
                ? businessRepository.findByOwnerProjectLink_IdAndEmail(ownerProjectLinkId, identifier)
                .orElseThrow(() -> new RuntimeException("Business not found with email: " + identifier))
                : businessRepository.findByOwnerProjectLink_IdAndPhoneNumber(ownerProjectLinkId, identifier)
                .orElseThrow(() -> new RuntimeException("Business not found with phone: " + identifier));
    }

    /**
     * Tenant-aware lookup strictly by email.
     */
    public Businesses getByEmailOrThrow(Long ownerProjectLinkId, String email) {
        return businessRepository.findByOwnerProjectLink_IdAndEmail(ownerProjectLinkId, email)
                .orElseThrow(() -> new RuntimeException("Business not found with email: " + email));
    }

    /* -------- legacy global finders (keep for backward compat) -------- */

    /**
     * Legacy/global lookup (NOT tenant-scoped).
     * Used to keep old endpoints working.
     */
    public Optional<Businesses> findByEmailOptional(String identifier) {
        if (identifier == null || identifier.isBlank()) return Optional.empty();
        return identifier.contains("@")
                ? businessRepository.findByEmail(identifier)          // SELECT * FROM businesses WHERE email = :identifier
                : businessRepository.findByPhoneNumber(identifier);   // SELECT * FROM businesses WHERE phone_number = :identifier
    }

    /**
     * Legacy/global lookup by identifier (throws if not found).
     */
    public Businesses findByEmail(String identifier) {
        if (identifier == null || identifier.isBlank())
            throw new IllegalArgumentException("Identifier cannot be null or empty");
        return identifier.contains("@")
                ? businessRepository.findByEmail(identifier).orElseThrow(() -> new RuntimeException("Business not found with: " + identifier))
                : businessRepository.findByPhoneNumber(identifier).orElseThrow(() -> new RuntimeException("Business not found with: " + identifier));
    }

    /**
     * Legacy/global lookup strictly by email (throws if not found).
     */
    public Businesses findByEmailOrThrow(String email) {
        return businessRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Business not found with email: " + email));
    }

    /* -------- save (tenant-aware overload keeps legacy version intact) -------- */

    /**
     * Tenant-aware save:
     * - Attaches the business to the tenant (AdminUserProject)
     * - Enforces tenant-scoped uniqueness for:
     *   - business name
     *   - email
     *   - phone number
     */
    public Businesses save(Long ownerProjectLinkId, Businesses business) {
        AdminUserProject app = requireOwnerProject(ownerProjectLinkId);
        business.setOwnerProjectLink(app);

        // Scoped uniqueness: business name unique per tenant/app
        if (business.getBusinessName() != null) {
            boolean nameTaken = business.getId() == null
                    ? businessRepository.existsByOwnerProjectLink_IdAndBusinessNameIgnoreCase(ownerProjectLinkId, business.getBusinessName())
                    : businessRepository.existsByOwnerProjectLink_IdAndBusinessNameIgnoreCaseAndIdNot(ownerProjectLinkId, business.getBusinessName(), business.getId());
            // SQL idea (create):
            // SELECT CASE WHEN COUNT(*)>0 THEN true ELSE false END
            // FROM businesses WHERE aup_id=:ownerProjectLinkId AND LOWER(business_name)=LOWER(:name);
            // SQL idea (update):
            // ... AND business_id <> :id
            if (nameTaken) throw new IllegalArgumentException("Business name already exists for this app!");
        }

        // Scoped uniqueness: email unique per tenant/app (when provided)
        if (business.getEmail() != null) {
            boolean emailTaken = business.getId() == null
                    ? businessRepository.existsByOwnerProjectLink_IdAndEmail(ownerProjectLinkId, business.getEmail())
                    : businessRepository.findByOwnerProjectLink_IdAndEmail(ownerProjectLinkId, business.getEmail())
                    .filter(b -> !Objects.equals(b.getId(), business.getId())).isPresent();
            // SQL idea (create):
            // SELECT CASE WHEN COUNT(*)>0 THEN true ELSE false END
            // FROM businesses WHERE aup_id=:ownerProjectLinkId AND email=:email;
            if (emailTaken) throw new IllegalArgumentException("Email already exists for this app!");
        }

        // Scoped uniqueness: phone unique per tenant/app (when provided)
        if (business.getPhoneNumber() != null) {
            boolean phoneTaken = business.getId() == null
                    ? businessRepository.existsByOwnerProjectLink_IdAndPhoneNumber(ownerProjectLinkId, business.getPhoneNumber())
                    : businessRepository.findByOwnerProjectLink_IdAndPhoneNumber(ownerProjectLinkId, business.getPhoneNumber())
                    .filter(b -> !Objects.equals(b.getId(), business.getId())).isPresent();
            // SQL idea (create):
            // SELECT CASE WHEN COUNT(*)>0 THEN true ELSE false END
            // FROM businesses WHERE aup_id=:ownerProjectLinkId AND phone_number=:phone;
            if (phoneTaken) throw new IllegalArgumentException("Phone already exists for this app!");
        }

        // Persists business (INSERT or UPDATE)
        return businessRepository.save(business);
    }

    // Legacy save (still available if you don’t pass ownerProjectLinkId anywhere)
    public Businesses save(Businesses business) {
        // Keep old behavior: global email uniqueness check only when updating
        if (business.getId() != null) {
            Optional<Businesses> existing = businessRepository.findByEmail(business.getEmail());
            // SQL idea:
            // SELECT * FROM businesses WHERE email=:email LIMIT 1;
            if (existing.isPresent() && !existing.get().getId().equals(business.getId())) {
                throw new IllegalArgumentException("Email already exists for another business!");
            }
        }
        return businessRepository.save(business);
    }

    public Businesses findById(Long id) { return businessRepository.findById(id).orElse(null); } // SELECT * FROM businesses WHERE business_id=:id
    public List<Businesses> findAll() { return businessRepository.findAll(); }                   // SELECT * FROM businesses

    /* -------- delete with cleanup -------- */

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
        // SQL idea:
        // SELECT * FROM items WHERE business_id=:businessId;
        for (Item item : items) {
            Long itemId = item.getId();

            // Delete OrderItem rows linked to this item
            // SQL idea:
            // DELETE FROM order_item WHERE item_id=:itemId;
            OrderItemRepository.deleteByItem_Id(itemId);

            // Delete Review rows linked to this item
            // SQL idea:
            // DELETE FROM review WHERE item_id=:itemId;
            reviewRepository.deleteByItem_Id(itemId);

            // Delete the item itself
            // SQL idea:
            // DELETE FROM items WHERE item_id=:itemId;
            itemRepository.deleteById(itemId);
        }

        // Join table removed — do NOT reference AdminUserBusinessRepository anymore.
        // If AdminUser has business_id FK and you don't rely on DB ON DELETE CASCADE, keep this:
        // SQL idea:
        // DELETE FROM admin_user WHERE business_id=:businessId;
        adminUsersRepository.deleteByBusiness_Id(businessId);

        // Finally delete the business
        // SQL idea:
        // DELETE FROM businesses WHERE business_id=:businessId;
        businessRepository.deleteById(businessId);
    }

    /* -------- Registration / verification (pending stays global) -------- */

    /**
     * Step 1 of business registration:
     * - Validate tenant/app exists
     * - Validate identifier (email OR phone)
     * - Ensure uniqueness INSIDE tenant
     * - Create/update PendingBusiness record
     * - Send verification code (email) or log it (SMS dev path)
     *
     * Returns pendingBusinessId used later in completeBusinessProfile().
     */
    public Long sendBusinessVerificationCode(Long ownerProjectLinkId, Map<String, String> data) {
        requireOwnerProject(ownerProjectLinkId);

        String email = data.get("email");
        String phone = data.get("phoneNumber");
        String password = data.get("password");
        String statusStr = data.get("status");
        String isPublicProfileStr = data.get("isPublicProfile");

        // Validate one identifier only
        if ((email == null || email.isBlank()) && (phone == null || phone.isBlank()))
            throw new RuntimeException("Provide either email or phone.");
        if (email != null && phone != null)
            throw new RuntimeException("Only one of email or phone should be provided.");

        // Resolve status entity (defaults to ACTIVE)
        BusinessStatus status = businessStatusRepository.findByNameIgnoreCase(
                statusStr != null ? statusStr.toUpperCase() : "ACTIVE"
        ).orElseThrow(() -> new RuntimeException("Invalid or missing status"));

        // App-scoped uniqueness (business table)
        if (email != null && businessRepository.findByOwnerProjectLink_IdAndEmail(ownerProjectLinkId, email).isPresent())
            throw new RuntimeException("Email already registered for this app.");
        if (phone != null && businessRepository.findByOwnerProjectLink_IdAndPhoneNumber(ownerProjectLinkId, phone).isPresent())
            throw new RuntimeException("Phone already registered for this app.");

        // Verification code: fixed for phone in dev, random for email
        String code = (phone != null) ? "123456" : String.format("%06d", new Random().nextInt(999999));

        // PendingBusiness is currently GLOBAL (not tenant-scoped in DB).
        // That means: findByEmail/findByPhoneNumber does not include ownerProjectLinkId.
        // If you want true multi-tenant pending registration, you should add aup_id to pending_businesses too.
        PendingBusiness pending;
        if (email != null) {
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

        // Persist pending
        PendingBusiness saved = pendingBusinessRepository.save(pending);

        // Send code
        if (email != null) {
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
            // SMS dev path
            System.out.println("📱 SMS to " + phone + ": code " + code);
        }

        return saved.getId();
    }

    /**
     * Verify email code for a pending business.
     * NOTE: PendingBusiness lookup is GLOBAL by email (not tenant-scoped).
     */
    public Long verifyBusinessEmailCode(String email, String code) {
        PendingBusiness pending = pendingBusinessRepository.findByEmail(email);
        if (pending == null || !pending.getVerificationCode().equals(code))
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
        PendingBusiness pending = pendingBusinessRepository.findByPhoneNumber(phone);
        if (pending == null || !pending.getVerificationCode().equals(code))
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
        boolean isEmail = emailOrPhone.contains("@");

        PendingBusiness pending = isEmail
                ? pendingBusinessRepository.findByEmail(emailOrPhone)
                : pendingBusinessRepository.findByPhoneNumber(emailOrPhone);

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
            emailService.sendHtmlEmail(emailOrPhone, "Resend Business Verification Code", html);
        } else {
            System.out.println("📱 Resending SMS to " + emailOrPhone + ": " + code);
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

        Role BusinessRole = roleRepository.findByNameIgnoreCase("BUSINESS")
                .orElseThrow(() -> new RuntimeException("Role BUSINESS not found"));

        Businesses business = new Businesses();
        business.setOwnerProjectLink(app); // attach tenant/app
        business.setEmail(pending.getEmail());
        business.setPhoneNumber(pending.getPhoneNumber());
        business.setPasswordHash(pending.getPasswordHash());
        business.setStatus(pending.getStatus());
        business.setIsPublicProfile(pending.getIsPublicProfile());
        business.setBusinessName(businessName);
        business.setDescription(description);
        business.setWebsiteUrl(websiteUrl);
        business.setRole(BusinessRole);
        business.setCreatedAt(LocalDateTime.now());
        business.setUpdatedAt(LocalDateTime.now());

        // Ensure local upload folder exists
        Path uploadDir = Paths.get("uploads");
        if (!Files.exists(uploadDir)) Files.createDirectories(uploadDir);

        // Save logo file if provided
        if (logo != null && !logo.isEmpty()) {
            String file = UUID.randomUUID() + "_" + logo.getOriginalFilename();
            Files.copy(logo.getInputStream(), uploadDir.resolve(file), StandardCopyOption.REPLACE_EXISTING);
            business.setBusinessLogoUrl("/uploads/" + file); // stored as public path
        }

        // Save banner file if provided
        if (banner != null && !banner.isEmpty()) {
            String file = UUID.randomUUID() + "_" + banner.getOriginalFilename();
            Files.copy(banner.getInputStream(), uploadDir.resolve(file), StandardCopyOption.REPLACE_EXISTING);
            business.setBusinessBannerUrl("/uploads/" + file);
        }

        // Use tenant-aware save (applies tenant uniqueness checks)
        Businesses saved = save(ownerProjectLinkId, business);

        // Remove pending record after successful completion
        pendingBusinessRepository.delete(pending);

        return saved;
    }

    /**
     * Tenant-aware update for business + optional images.
     * - Checks that the business belongs to the passed ownerProjectLinkId
     * - Enforces tenant uniqueness for email/phone (when changed)
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

        Role BusinessRole = roleRepository.findByNameIgnoreCase("BUSINESS")
                .orElseThrow(() -> new RuntimeException("Role BUSINESS not found"));

        Businesses existing = businessRepository.findById(id).orElse(null);
        if (existing == null) throw new IllegalArgumentException("Business with ID " + id + " not found.");

        // IMPORTANT: ensure you are not updating a business across tenants
        if (!Objects.equals(existing.getOwnerProjectLink().getId(), ownerProjectLinkId)) {
            throw new IllegalArgumentException("Business does not belong to the specified app.");
        }

        // Email update: enforce tenant uniqueness
        if (email != null) {
            Optional<Businesses> byEmail = businessRepository.findByOwnerProjectLink_IdAndEmail(ownerProjectLinkId, email);
            if (byEmail.isPresent() && !Objects.equals(byEmail.get().getId(), id)) {
                throw new IllegalArgumentException("Email already exists for this app!");
            }
            existing.setEmail(email);
        }

        // Phone update: enforce tenant uniqueness
        if (phoneNumber != null) {
            Optional<Businesses> byPhone = businessRepository.findByOwnerProjectLink_IdAndPhoneNumber(ownerProjectLinkId, phoneNumber);
            if (byPhone.isPresent() && !Objects.equals(byPhone.get().getId(), id)) {
                throw new IllegalArgumentException("Phone already exists for this app!");
            }
            existing.setPhoneNumber(phoneNumber);
        }

        existing.setBusinessName(name);

        // Update password only if provided (and enforce minimal length)
        if (password != null && !password.trim().isEmpty()) {
            if (password.length() < 6) throw new IllegalArgumentException("Password must be at least 6 characters long.");
            existing.setPasswordHash(passwordEncoder.encode(password));
        }

        existing.setDescription(description);
        existing.setWebsiteUrl(websiteUrl);
        existing.setRole(BusinessRole);

        // Upload directory
        Path uploadDir = Paths.get("uploads/");
        if (!Files.exists(uploadDir)) Files.createDirectories(uploadDir);

        // Update logo file (overwrite DB link only)
        if (logo != null && !logo.isEmpty()) {
            String f = UUID.randomUUID() + "_" + logo.getOriginalFilename();
            Files.copy(logo.getInputStream(), uploadDir.resolve(f), StandardCopyOption.REPLACE_EXISTING);
            existing.setBusinessLogoUrl("/uploads/" + f);
        }

        // Update banner file
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

    /* -------- password reset (legacy-global by email) -------- */

    /**
     * Legacy password reset (global).
     * NOTE: Tenant-aware reset is not implemented here.
     */
    public boolean resetPassword(String email) {
        Optional<Businesses> optional = businessRepository.findByEmail(email);
        if (optional.isEmpty()) return false;

        String code = String.format("%06d", new Random().nextInt(999999));
        resetCodes.put(email, code);

        String htmlMessage = """
            <html><body style="font-family: Arial; text-align: center; padding: 20px;">
                <h2 style="color: #FF9800;">Reset Your Password</h2>
                <p>Use the code below to proceed:</p>
                <h1 style="color: #2196F3;">%s</h1>
                <p style="font-size: 14px; color: #777;">This code will expire in 10 minutes.</p>
            </body></html>
        """.formatted(code);

        emailService.sendHtmlEmail(email, "Password Reset Code", htmlMessage);
        return true;
    }

    public boolean verifyResetCode(String email, String code) {
        // Basic equality check on the in-memory map
        return resetCodes.containsKey(email) && Objects.equals(resetCodes.get(email), code);
    }

    public boolean updatePasswordDirectly(String email, String newPassword) {
        Optional<Businesses> optional = businessRepository.findByEmail(email);
        if (optional.isEmpty()) return false;

        Businesses b = optional.get();
        b.setPasswordHash(passwordEncoder.encode(newPassword));
        businessRepository.save(b);

        resetCodes.remove(email);
        return true;
    }

    // --- Legacy overload kept for backward compatibility ---
    // Matches calls like: updateBusinessWithImages(id, name, email, password, description, phoneNumber, websiteUrl, logo, banner)
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

        // Load existing business first
        Businesses existing = businessRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Business with ID " + id + " not found."));

        Role BusinessRole = roleRepository.findByNameIgnoreCase("BUSINESS")
                .orElseThrow(() -> new RuntimeException("Role BUSINESS not found"));

        // If the business is tied to an app, delegate to the tenant-aware method
        Long ownerProjectLinkId = (existing.getOwnerProjectLink() != null)
                ? existing.getOwnerProjectLink().getId()
                : null;

        if (ownerProjectLinkId != null) {
            return updateBusinessWithImages(
                    ownerProjectLinkId, id, name, email, password, description, phoneNumber, websiteUrl, logo, banner
            );
        }

        // ---- Fallback to legacy (global) behavior when no tenant link exists ----
        // Email uniqueness (global)
        if (email != null) {
            Optional<Businesses> byEmail = businessRepository.findByEmail(email);
            if (byEmail.isPresent() && !Objects.equals(byEmail.get().getId(), id)) {
                throw new IllegalArgumentException("Email already exists for another business!");
            }
            existing.setEmail(email);
        }

        if (phoneNumber != null) existing.setPhoneNumber(phoneNumber);

        existing.setBusinessName(name);
        existing.setDescription(description);
        existing.setWebsiteUrl(websiteUrl);
        existing.setRole(BusinessRole);

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

    /* -------- assets -------- */

    /**
     * Deletes the physical logo file (if stored locally) and nulls the DB field.
     * NOTE: For safety, this assumes local path mapping from "/uploads/..." -> "uploads/..."
     */
    public boolean deleteBusinessLogo(Long businessId) {
        Businesses b = businessRepository.findById(businessId).orElseThrow(() -> new RuntimeException("Business not found"));
        if (b.getBusinessLogoUrl() != null && !b.getBusinessLogoUrl().isEmpty()) {
            String logoPath = b.getBusinessLogoUrl().replace("/uploads", "uploads");
            try { Files.deleteIfExists(Paths.get(logoPath)); }
            catch (IOException e) { throw new RuntimeException("Failed to delete logo: " + e.getMessage()); }
            b.setBusinessLogoUrl(null);
            businessRepository.save(b);
            return true;
        }
        return false;
    }

    public boolean deleteBusinessBanner(Long businessId) {
        Businesses b = businessRepository.findById(businessId).orElseThrow(() -> new RuntimeException("Business not found"));
        if (b.getBusinessBannerUrl() != null && !b.getBusinessBannerUrl().isEmpty()) {
            String p = b.getBusinessBannerUrl().replace("/uploads", "uploads");
            try { Files.deleteIfExists(Paths.get(p)); }
            catch (IOException e) { throw new RuntimeException("Failed to delete banner: " + e.getMessage()); }
            b.setBusinessBannerUrl(null);
            businessRepository.save(b);
            return true;
        }
        return false;
    }

    /* -------- listings / housekeeping -------- */

    /**
     * Tenant-aware list of public businesses with ACTIVE status.
     */
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

    /* -------- misc / legacy wrappers -------- */

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

            // <= 3.0 means low rated (threshold)
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

    /* -------- manager invite + registration -------- */

    /**
     * Sends an invite email to become a manager for a business.
     * - Creates a PendingManager row with a unique token
     * - Sends email with link containing the token
     */
    public void sendManagerInvite(String email, Businesses business) {
        String token = UUID.randomUUID().toString(); // unique token for this invite

        PendingManager pending = new PendingManager(email, business, token);
        pendingManagerRepository.save(pending);

        // If you have a configured domain, swap the link below
        String inviteLink = "http://localhost:5173/assign-manager?token=" + token;

        String html = """
            <html>
            <body style="font-family: Arial, sans-serif; text-align: center; padding: 20px;">
                <h2 style="color: #4CAF50;">You're Invited to Be a Manager!</h2>
                <p>You have been invited to become a manager at build4all.</p>
                <p>
                  <a href="%s" style="display:inline-block; padding:10px 20px; background:#2196F3; color:#fff; text-decoration:none; border-radius:5px;">
                    Complete Registration
                  </a>
                </p>
                <p>This link will expire in a few days.</p>
            </body>
            </html>
        """.formatted(inviteLink);

        emailService.sendHtmlEmail(email, "Manager Invitation", html);
    }

    /**
     * Completes manager registration using the invite token:
     * - Reads PendingManager by token
     * - Creates an AdminUser with MANAGER role
     * - Links manager to the business
     * - Deletes the pending token (one-time use)
     */
    @Transactional
    public boolean registerManagerFromInvite(String token, String username, String firstName, String lastName, String password) {
        Optional<PendingManager> pendingOpt = pendingManagerRepository.findByToken(token);
        if (pendingOpt.isEmpty()) return false;

        PendingManager pending = pendingOpt.get();
        Businesses business = pending.getBusiness();

        Role managerRole = roleRepository.findByNameIgnoreCase("MANAGER")
                .orElseThrow(() -> new RuntimeException("Manager role not found"));

        // Prevent duplicates: same email already exists as admin
        if (adminUsersRepository.findByEmail(pending.getEmail()).isPresent()) {
            throw new RuntimeException("User already exists as admin");
        }

        AdminUser newManager = new AdminUser();
        newManager.setUsername(username);
        newManager.setFirstName(firstName);
        newManager.setLastName(lastName);
        newManager.setEmail(pending.getEmail());
        newManager.setPasswordHash(passwordEncoder.encode(password));
        newManager.setRole(managerRole);
        newManager.setBusiness(business);
        newManager.setNotifyItemUpdates(true);
        newManager.setNotifyUserFeedback(true);
        newManager.setCreatedAt(LocalDateTime.now());
        newManager.setUpdatedAt(LocalDateTime.now());

        adminUsersRepository.save(newManager);

        // Consume token so it can't be reused
        pendingManagerRepository.delete(pending);

        return true;
    }
}
