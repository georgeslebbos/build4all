package com.build4all.home.sections.service;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.features.ecommerce.domain.Product;
import com.build4all.features.ecommerce.repository.ProductRepository; // adjust if your path differs
import com.build4all.home.banner.dto.HomeBannerResponse;
import com.build4all.home.banner.service.HomeBannerService;
import com.build4all.home.sections.domain.HomeSection;
import com.build4all.home.sections.domain.HomeSectionLayout;
import com.build4all.home.sections.domain.HomeSectionProduct;
import com.build4all.home.sections.dto.*;
import com.build4all.home.sections.repository.HomeSectionProductRepository;
import com.build4all.home.sections.repository.HomeSectionRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * HomePageService
 *
 * Purpose:
 * - Builds the Home page payload returned to the mobile/web client:
 *     1) Top banners (existing feature) from HomeBannerService
 *     2) Home sections (new feature) where each section contains an ordered list of products
 *
 * Why is this a "composer" service?
 * - Because data comes from multiple modules/domains:
 *     - home.banner (banners)
 *     - home.sections (sections + links)
 *     - ecommerce (products)
 * - The service composes them into ONE response DTO so the frontend loads home in a single request.
 *
 * Multi-tenant scope:
 * - ownerProjectId here is actually the AUP id (AdminUserProject.aup_id).
 * - Every query must be filtered by AUP to avoid cross-tenant leakage.
 *
 * Security:
 * - Public home endpoint: USER/OWNER can read (controller checks role).
 * - Admin operations: OWNER only (controller checks role) + we validate ownership:
 *     requireOwnedProject(...) and requireOwnedSection(...)
 *
 * Performance note:
 * - This implementation loads products one-by-one (N+1 style).
 * - For small home sections it is usually fine.
 * - If you expect many products per section, consider optimizing with:
 *     productRepo.findAllById(listOfIds) and mapping in-memory.
 */
@Service
@Transactional
public class HomePageService {

    /**
     * Existing home banner logic (time-window, active, sorting, etc.) lives here.
     * We reuse it to keep behavior consistent with your current banner API.
     */
    private final HomeBannerService bannerService; // reuse your existing logic

    /**
     * Access to home section definitions (title, layout, active, sort order).
     */
    private final HomeSectionRepository sectionRepo;

    /**
     * Access to section->product link table (active, sort order).
     */
    private final HomeSectionProductRepository sectionProductRepo;

    /**
     * Used to load product data by ID and return it in the section DTO.
     * Note: Product extends Item, so it inherits price/sale/image fields from Item.
     */
    private final ProductRepository productRepo; // adjust if your path differs

    /**
     * Used to validate that a given AUP (ownerProjectId) belongs to the OWNER adminId.
     * This follows the same pattern you used in HomeBannerService.
     */
    private final AdminUserProjectRepository aupRepo;

    public HomePageService(HomeBannerService bannerService,
                           HomeSectionRepository sectionRepo,
                           HomeSectionProductRepository sectionProductRepo,
                           ProductRepository productRepo,
                           AdminUserProjectRepository aupRepo) {
        this.bannerService = bannerService;
        this.sectionRepo = sectionRepo;
        this.sectionProductRepo = sectionProductRepo;
        this.productRepo = productRepo;
        this.aupRepo = aupRepo;
    }

    // ---------------- PUBLIC (USER/OWNER) ----------------
    @Transactional(Transactional.TxType.SUPPORTS)
    public HomePageResponse getPublicHome(Long ownerProjectId) {

        // 1) top banners (existing behavior)
        // - returns only active banners within start/end window (if defined)
        // - ordered by sortOrder asc, createdAt desc (see HomeBannerRepository query)
        List<HomeBannerResponse> banners = bannerService.listActivePublic(ownerProjectId);

        // 2) sections below
        // - only active sections for this tenant (AUP)
        // - ordered by sortOrder asc
        List<HomeSection> sections = sectionRepo.findByOwnerProject_IdAndActiveTrueOrderBySortOrderAsc(ownerProjectId);

        // Output list that will be returned to the client
        List<HomeSectionResponse> outSections = new ArrayList<>();

        // Build each section with its product list
        for (HomeSection s : sections) {

            // Fetch active product links for this section in the exact order the owner configured
            List<HomeSectionProduct> links =
                    sectionProductRepo.findBySection_IdAndActiveTrueOrderBySortOrderAsc(s.getId());

            // Map linked productIds into ProductSummaryDTO
            List<ProductSummaryDTO> products = new ArrayList<>();
            for (HomeSectionProduct link : links) {

                // load product by ID
                // If a product was deleted but link remains, we skip it safely.
                Product p = productRepo.findById(link.getProductId()).orElse(null);
                if (p == null) continue;

                // IMPORTANT:
                // - price and salePrice exist in Item (parent of Product)
                // - imageUrl exists in Item as well in your current codebase
                products.add(new ProductSummaryDTO(
                        p.getId(),
                        p.getName(),
                        p.getPrice(),       // ✅ Item.price
                        p.getSalePrice(),   // ✅ Item.sale_price (nullable)
                        resolveProductImage(p) // ✅ tries to resolve image url safely
                ));
            }

            // Convert section entity -> DTO for frontend
            outSections.add(new HomeSectionResponse(
                    s.getCode(),              // stable identifier
                    s.getTitle(),             // display name
                    s.getLayout().name(),     // HORIZONTAL / GRID
                    s.getSortOrder(),         // ordering among sections
                    products                  // ordered products
            ));
        }

        // Final response payload:
        // - banners first (top slider)
        // - sections below
        return new HomePageResponse(banners, outSections);
    }

    // ---------------- ADMIN (OWNER) ----------------

    /**
     * Create a new section for a given tenant (AUP).
     * - Validates required fields
     * - Validates OWNER really owns the target AUP
     * - Inserts into home_sections
     */
    public HomeSection createSection(Long adminId, HomeSectionRequest req) {
        if (req.getOwnerProjectId() == null) throw new IllegalArgumentException("ownerProjectId is required");
        if (req.getCode() == null || req.getCode().isBlank()) throw new IllegalArgumentException("code is required");
        if (req.getLayout() == null || req.getLayout().isBlank()) throw new IllegalArgumentException("layout is required");

        // Validate tenant ownership
        AdminUserProject app = requireOwnedProject(req.getOwnerProjectId(), adminId);

        HomeSection s = new HomeSection();
        s.setOwnerProject(app);
        s.setCode(req.getCode());
        s.setTitle(req.getTitle());

        // Layout stored as enum string in DB
        s.setLayout(HomeSectionLayout.valueOf(req.getLayout().toUpperCase()));

        // Default ordering and visibility
        s.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        s.setActive(req.getActive() == null || req.getActive());

        return sectionRepo.save(s);
    }

    /**
     * Update section metadata (title/layout/sortOrder/active).
     * - Ownership is validated using requireOwnedSection(sectionId, adminId).
     * - NOTE: code is not updated here on purpose (code is a stable identifier + unique constraint).
     */
    public HomeSection updateSection(Long adminId, Long sectionId, HomeSectionRequest req) {
        HomeSection s = requireOwnedSection(sectionId, adminId);

        if (req.getTitle() != null) s.setTitle(req.getTitle());
        if (req.getLayout() != null) s.setLayout(HomeSectionLayout.valueOf(req.getLayout().toUpperCase()));
        if (req.getSortOrder() != null) s.setSortOrder(req.getSortOrder());
        if (req.getActive() != null) s.setActive(req.getActive());

        return sectionRepo.save(s);
    }

    /**
     * Delete a section entirely.
     * - With proper FK ON DELETE CASCADE in SQL, link rows will be deleted automatically.
     * - Ownership is validated.
     */
    public void deleteSection(Long adminId, Long sectionId) {
        HomeSection s = requireOwnedSection(sectionId, adminId);
        sectionRepo.delete(s);
    }

    /**
     * Add a product to a section by sectionCode (e.g., "flash_sale").
     *
     * Steps:
     * 1) Validate ownership of the tenant (AUP).
     * 2) Find section by (aup_id, sectionCode).
     * 3) Validate product exists.
     * 4) Prevent duplicates (same section + product) by returning existing link if found.
     * 5) Save link with active + sortOrder.
     */
    public HomeSectionProduct addProduct(Long adminId, Long ownerProjectId, String sectionCode, SectionAddProductRequest req) {
        AdminUserProject app = requireOwnedProject(ownerProjectId, adminId);

        HomeSection section = sectionRepo.findByOwnerProject_IdAndCode(app.getId(), sectionCode)
                .orElseThrow(() -> new IllegalArgumentException("Section not found: " + sectionCode));

        if (req.getProductId() == null) throw new IllegalArgumentException("productId is required");
        productRepo.findById(req.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + req.getProductId()));

        // Avoid creating duplicate links
        var existing = sectionProductRepo.findBySection_IdAndProductId(section.getId(), req.getProductId());
        if (existing.isPresent()) return existing.get();

        HomeSectionProduct link = new HomeSectionProduct();
        link.setSection(section);
        link.setProductId(req.getProductId());
        link.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        link.setActive(req.getActive() == null || req.getActive());

        return sectionProductRepo.save(link);
    }

    /**
     * Remove a product from a section.
     * - Ownership of the tenant is validated.
     * - Section is found by (aup_id, sectionCode) to prevent cross-tenant access.
     */
    public void removeProduct(Long adminId, Long ownerProjectId, String sectionCode, Long productId) {
        AdminUserProject app = requireOwnedProject(ownerProjectId, adminId);

        HomeSection section = sectionRepo.findByOwnerProject_IdAndCode(app.getId(), sectionCode)
                .orElseThrow(() -> new IllegalArgumentException("Section not found: " + sectionCode));

        sectionProductRepo.deleteBySection_IdAndProductId(section.getId(), productId);
    }

    // -------- helpers (aligned with HomeBannerService ownership checks) --------

    /**
     * Validate that the given ownerProjectId (AUP) belongs to this OWNER (adminId).
     * Prevents a malicious client from managing another tenant by passing a different aup_id.
     */
    private AdminUserProject requireOwnedProject(Long ownerProjectId, Long adminId) {
        return aupRepo.findById(ownerProjectId)
                .filter(aup -> aup.getAdminId() != null && aup.getAdminId().equals(adminId))
                .orElseThrow(() -> new IllegalArgumentException("App not found or not yours"));
    }

    /**
     * Validate section ownership through its parent AUP.
     * This prevents editing/deleting sections that belong to another tenant.
     */
    private HomeSection requireOwnedSection(Long sectionId, Long adminId) {
        HomeSection s = sectionRepo.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("Section not found"));

        AdminUserProject app = s.getOwnerProject();
        if (app == null || app.getAdminId() == null || !app.getAdminId().equals(adminId)) {
            throw new IllegalArgumentException("Section not found or not yours");
        }
        return s;
    }

    /**
     * Product images are not in Product class (based on your snippet).
     * So we try common getters that might exist in Item parent:
     * - getImageUrl / getThumbnailUrl / getCoverImageUrl ...
     * If none exist => return null (Flutter shows placeholder).
     *
     * NOTE:
     * - In your current codebase, Item actually has getImageUrl(), so this will return it.
     * - Keeping this helper makes the service resilient if image fields evolve later.
     */
    private String resolveProductImage(Object p) {
        return tryStringGetter(p,
                "getImageUrl",
                "getThumbnailUrl",
                "getCoverImageUrl",
                "getMainImageUrl"
        );
    }

    /**
     * Utility to call a getter by name using reflection.
     * We use it to avoid compile errors when the model changes (optional resilience).
     */
    private String tryStringGetter(Object target, String... methodNames) {
        for (String name : methodNames) {
            try {
                Method m = target.getClass().getMethod(name);
                Object val = m.invoke(target);
                if (val instanceof String s && !s.isBlank()) return s;
            } catch (Exception ignored) {
                // method missing -> try next
            }
        }
        return null;
    }
}
