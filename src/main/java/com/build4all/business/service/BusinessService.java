package com.build4all.business.service;

import com.build4all.admin.domain.AdminUser;
import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.admin.repository.AdminUsersRepository;
import com.build4all.booking.repository.ItemBookingsRepository;
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

@Service
public class BusinessService {

    @Autowired private BusinessesRepository businessRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ItemRepository itemRepository;
    @Autowired private ItemBookingsRepository itemBookingRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private AdminUsersRepository adminUsersRepository;
    @Autowired private PendingBusinessRepository pendingBusinessRepository;
    @Autowired private PendingManagerRepository pendingManagerRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private BusinessStatusRepository businessStatusRepository;
    @Autowired private AdminUserProjectRepository adminUserProjectRepository;

    private final EmailService emailService;
    public BusinessService(EmailService emailService) { this.emailService = emailService; }

    private final Map<String, String> resetCodes = new ConcurrentHashMap<>();

    /* -------- tenant helper -------- */
    private AdminUserProject requireOwnerProject(Long ownerProjectLinkId) {
        return adminUserProjectRepository.findById(ownerProjectLinkId)
            .orElseThrow(() -> new IllegalArgumentException("Invalid ownerProjectLinkId"));
    }

    /* -------- tenant-aware finders -------- */
    public Optional<Businesses> findByEmailOptional(Long ownerProjectLinkId, String identifier) {
        if (identifier == null || identifier.isBlank()) return Optional.empty();
        if (identifier.contains("@")) {
            return businessRepository.findByOwnerProjectLink_IdAndEmail(ownerProjectLinkId, identifier);
        } else {
            return businessRepository.findByOwnerProjectLink_IdAndPhoneNumber(ownerProjectLinkId, identifier);
        }
    }

    public Businesses findByEmail(Long ownerProjectLinkId, String identifier) {
        if (identifier == null || identifier.isBlank())
            throw new IllegalArgumentException("Identifier cannot be null or empty");

        return identifier.contains("@")
            ? businessRepository.findByOwnerProjectLink_IdAndEmail(ownerProjectLinkId, identifier)
                .orElseThrow(() -> new RuntimeException("Business not found with email: " + identifier))
            : businessRepository.findByOwnerProjectLink_IdAndPhoneNumber(ownerProjectLinkId, identifier)
                .orElseThrow(() -> new RuntimeException("Business not found with phone: " + identifier));
    }

    public Businesses getByEmailOrThrow(Long ownerProjectLinkId, String email) {
        return businessRepository.findByOwnerProjectLink_IdAndEmail(ownerProjectLinkId, email)
                .orElseThrow(() -> new RuntimeException("Business not found with email: " + email));
    }

    /* -------- legacy global finders (keep for backward compat) -------- */
    public Optional<Businesses> findByEmailOptional(String identifier) {
        if (identifier == null || identifier.isBlank()) return Optional.empty();
        return identifier.contains("@")
            ? businessRepository.findByEmail(identifier)
            : businessRepository.findByPhoneNumber(identifier);
    }

    public Businesses findByEmail(String identifier) {
        if (identifier == null || identifier.isBlank())
            throw new IllegalArgumentException("Identifier cannot be null or empty");
        return identifier.contains("@")
            ? businessRepository.findByEmail(identifier).orElseThrow(() -> new RuntimeException("Business not found with: " + identifier))
            : businessRepository.findByPhoneNumber(identifier).orElseThrow(() -> new RuntimeException("Business not found with: " + identifier));
    }

    public Businesses findByEmailOrThrow(String email) {
        return businessRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Business not found with email: " + email));
    }

    /* -------- save (tenant-aware overload keeps legacy version intact) -------- */
    public Businesses save(Long ownerProjectLinkId, Businesses business) {
        AdminUserProject app = requireOwnerProject(ownerProjectLinkId);
        business.setOwnerProjectLink(app);

        // Scoped uniqueness
        if (business.getBusinessName() != null) {
            boolean nameTaken = business.getId() == null
                ? businessRepository.existsByOwnerProjectLink_IdAndBusinessNameIgnoreCase(ownerProjectLinkId, business.getBusinessName())
                : businessRepository.existsByOwnerProjectLink_IdAndBusinessNameIgnoreCaseAndIdNot(ownerProjectLinkId, business.getBusinessName(), business.getId());
            if (nameTaken) throw new IllegalArgumentException("Business name already exists for this app!");
        }

        if (business.getEmail() != null) {
            boolean emailTaken = business.getId() == null
                ? businessRepository.existsByOwnerProjectLink_IdAndEmail(ownerProjectLinkId, business.getEmail())
                : businessRepository.findByOwnerProjectLink_IdAndEmail(ownerProjectLinkId, business.getEmail())
                    .filter(b -> !Objects.equals(b.getId(), business.getId())).isPresent();
            if (emailTaken) throw new IllegalArgumentException("Email already exists for this app!");
        }

        if (business.getPhoneNumber() != null) {
            boolean phoneTaken = business.getId() == null
                ? businessRepository.existsByOwnerProjectLink_IdAndPhoneNumber(ownerProjectLinkId, business.getPhoneNumber())
                : businessRepository.findByOwnerProjectLink_IdAndPhoneNumber(ownerProjectLinkId, business.getPhoneNumber())
                    .filter(b -> !Objects.equals(b.getId(), business.getId())).isPresent();
            if (phoneTaken) throw new IllegalArgumentException("Phone already exists for this app!");
        }

        return businessRepository.save(business);
    }

    // Legacy save (still available if you don’t pass ownerProjectLinkId anywhere)
    public Businesses save(Businesses business) {
        // Keep old behavior: global email uniqueness check only when updating
        if (business.getId() != null) {
            Optional<Businesses> existing = businessRepository.findByEmail(business.getEmail());
            if (existing.isPresent() && !existing.get().getId().equals(business.getId())) {
                throw new IllegalArgumentException("Email already exists for another business!");
            }
        }
        return businessRepository.save(business);
    }

    public Businesses findById(Long id) { return businessRepository.findById(id).orElse(null); }
    public List<Businesses> findAll() { return businessRepository.findAll(); }

    /* -------- delete with cleanup -------- */
    @Transactional
    public void delete(Long businessId) {
        Businesses business = businessRepository.findById(businessId)
                .orElseThrow(() -> new RuntimeException("Business not found"));

        // Delete items + dependent bookings/reviews
        List<Item> items = itemRepository.findByBusinessId(businessId);
        for (Item item : items) {
            Long itemId = item.getId();
            itemBookingRepository.deleteByItem_Id(itemId);
            reviewRepository.deleteByItem_Id(itemId);
            itemRepository.deleteById(itemId);
        }

        // Join table removed — do NOT reference AdminUserBusinessRepository anymore.
        // If AdminUser has business_id FK and you don't rely on DB ON DELETE CASCADE, keep this:
        adminUsersRepository.deleteByBusiness_Id(businessId);

        // Finally delete the business
        businessRepository.deleteById(businessId);
    }

    /* -------- Registration / verification (pending stays global) -------- */

    public Long sendBusinessVerificationCode(Long ownerProjectLinkId, Map<String, String> data) {
        requireOwnerProject(ownerProjectLinkId);
        String email = data.get("email");
        String phone = data.get("phoneNumber");
        String password = data.get("password");
        String statusStr = data.get("status");
        String isPublicProfileStr = data.get("isPublicProfile");

        if ((email == null || email.isBlank()) && (phone == null || phone.isBlank()))
            throw new RuntimeException("Provide either email or phone.");
        if (email != null && phone != null)
            throw new RuntimeException("Only one of email or phone should be provided.");

        BusinessStatus status = businessStatusRepository.findByNameIgnoreCase(
            statusStr != null ? statusStr.toUpperCase() : "ACTIVE"
        ).orElseThrow(() -> new RuntimeException("Invalid or missing status"));

        // App-scoped uniqueness
        if (email != null && businessRepository.findByOwnerProjectLink_IdAndEmail(ownerProjectLinkId, email).isPresent())
            throw new RuntimeException("Email already registered for this app.");
        if (phone != null && businessRepository.findByOwnerProjectLink_IdAndPhoneNumber(ownerProjectLinkId, phone).isPresent())
            throw new RuntimeException("Phone already registered for this app.");

        String code = (phone != null) ? "123456" : String.format("%06d", new Random().nextInt(999999));

        PendingBusiness pending;
        if (email != null) {
            pending = pendingBusinessRepository.findByEmail(email);
            if (pending == null) { pending = new PendingBusiness(); pending.setEmail(email); }
        } else {
            pending = pendingBusinessRepository.findByPhoneNumber(phone);
            if (pending == null) { pending = new PendingBusiness(); pending.setPhoneNumber(phone); }
        }

        pending.setPasswordHash(passwordEncoder.encode(password));
        pending.setVerificationCode(code);
        pending.setCreatedAt(LocalDateTime.now());
        pending.setStatus(status);
        pending.setIsPublicProfile(isPublicProfileStr == null || Boolean.parseBoolean(isPublicProfileStr));

        PendingBusiness saved = pendingBusinessRepository.save(pending);

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
            System.out.println("📱 SMS to " + phone + ": code " + code);
        }

        return saved.getId();
    }

    public Long verifyBusinessEmailCode(String email, String code) {
        PendingBusiness pending = pendingBusinessRepository.findByEmail(email);
        if (pending == null || !pending.getVerificationCode().equals(code))
            throw new RuntimeException("Invalid verification code");
        pending.setIsVerified(true);
        pendingBusinessRepository.save(pending);
        return pending.getId();
    }

    public Long verifyBusinessPhoneCode(String phone, String code) {
        PendingBusiness pending = pendingBusinessRepository.findByPhoneNumber(phone);
        if (pending == null || !pending.getVerificationCode().equals(code))
            throw new RuntimeException("Invalid verification code");
        pending.setIsVerified(true);
        pendingBusinessRepository.save(pending);
        return pending.getId();
    }

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

        Businesses business = new Businesses();
        business.setOwnerProjectLink(app);
        business.setEmail(pending.getEmail());
        business.setPhoneNumber(pending.getPhoneNumber());
        business.setPasswordHash(pending.getPasswordHash());
        business.setStatus(pending.getStatus());
        business.setIsPublicProfile(pending.getIsPublicProfile());
        business.setBusinessName(businessName);
        business.setDescription(description);
        business.setWebsiteUrl(websiteUrl);
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

        Businesses saved = save(ownerProjectLinkId, business);
        pendingBusinessRepository.delete(pending);
        return saved;
    }

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

        Businesses existing = businessRepository.findById(id).orElse(null);
        if (existing == null) throw new IllegalArgumentException("Business with ID " + id + " not found.");

        if (!Objects.equals(existing.getOwnerProjectLink().getId(), ownerProjectLinkId)) {
            throw new IllegalArgumentException("Business does not belong to the specified app.");
        }

        if (email != null) {
            Optional<Businesses> byEmail = businessRepository.findByOwnerProjectLink_IdAndEmail(ownerProjectLinkId, email);
            if (byEmail.isPresent() && !Objects.equals(byEmail.get().getId(), id)) {
                throw new IllegalArgumentException("Email already exists for this app!");
            }
            existing.setEmail(email);
        }

        if (phoneNumber != null) {
            Optional<Businesses> byPhone = businessRepository.findByOwnerProjectLink_IdAndPhoneNumber(ownerProjectLinkId, phoneNumber);
            if (byPhone.isPresent() && !Objects.equals(byPhone.get().getId(), id)) {
                throw new IllegalArgumentException("Phone already exists for this app!");
            }
            existing.setPhoneNumber(phoneNumber);
        }

        existing.setBusinessName(name);
        if (password != null && !password.trim().isEmpty()) {
            if (password.length() < 6) throw new IllegalArgumentException("Password must be at least 6 characters long.");
            existing.setPasswordHash(passwordEncoder.encode(password));
        }
        existing.setDescription(description);
        existing.setWebsiteUrl(websiteUrl);

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
    public BusinessUser findBusinessUserById(Long businessUserId) { return null; }
    public List<LowRatedBusinessDTO> getLowRatedBusinesses() {
        List<Businesses> businesses = businessRepository.findAll();
        List<LowRatedBusinessDTO> result = new ArrayList<>();
        for (Businesses b : businesses) {
            List<Review> reviews = reviewRepository.findByBusinessId(b.getId());
            if (reviews.isEmpty()) continue;
            double avg = reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);
            if (avg <= 3.0) {
                result.add(new LowRatedBusinessDTO(b.getId(), b.getBusinessName(),
                        b.getStatus() != null ? b.getStatus().getName() : "UNKNOWN", avg));
            }
        }
        return result;
    }

    /* -------- manager invite + registration -------- */
    public void sendManagerInvite(String email, Businesses business) {
        String token = UUID.randomUUID().toString();           // unique token

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

    @Transactional
    public boolean registerManagerFromInvite(String token, String username, String firstName, String lastName, String password) {
        Optional<PendingManager> pendingOpt = pendingManagerRepository.findByToken(token);
        if (pendingOpt.isEmpty()) return false;

        PendingManager pending = pendingOpt.get();
        Businesses business = pending.getBusiness();

        Role managerRole = roleRepository.findByName("MANAGER")
                .orElseThrow(() -> new RuntimeException("Manager role not found"));

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
        pendingManagerRepository.delete(pending);               // consume token

        return true;
    }
}
