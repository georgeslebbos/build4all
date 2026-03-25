package com.build4all.features.ecommerce.service;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.catalog.domain.Category;
import com.build4all.catalog.domain.Currency;
import com.build4all.catalog.domain.ItemAttribute;
import com.build4all.catalog.domain.ItemAttributeDataType;
import com.build4all.catalog.domain.ItemAttributeValue;
import com.build4all.catalog.domain.ItemStatus;
import com.build4all.catalog.domain.ItemType;
import com.build4all.catalog.repository.CategoryRepository;
import com.build4all.catalog.repository.CurrencyRepository;
import com.build4all.catalog.repository.ItemAttributeRepository;
import com.build4all.catalog.repository.ItemAttributeValueRepository;
import com.build4all.catalog.repository.ItemStatusRepository;
import com.build4all.catalog.repository.ItemTypeRepository;
import com.build4all.catalog.service.ItemImageService;
import com.build4all.features.ecommerce.domain.Product;
import com.build4all.features.ecommerce.domain.ProductType;
import com.build4all.features.ecommerce.dto.AttributeValueDTO;
import com.build4all.features.ecommerce.dto.ProductRequest;
import com.build4all.features.ecommerce.dto.ProductResponse;
import com.build4all.features.ecommerce.dto.ProductUpdateRequest;
import com.build4all.features.ecommerce.repository.ProductRepository;
import com.build4all.order.repository.OrderItemRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProductService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_UPCOMING = "UPCOMING";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_ARCHIVED = "ARCHIVED";

    private final ItemImageService itemImageService;
    
    private final ProductRepository productRepository;
    private final ItemTypeRepository itemTypeRepository;
    private final CurrencyRepository currencyRepository;
    private final AdminUserProjectRepository adminUserProjectRepository;
    private final ItemAttributeRepository itemAttributeRepository;
    private final ItemAttributeValueRepository itemAttributeValueRepository;
    private final OrderItemRepository orderItemRepository;
    private final CategoryRepository categoryRepository;
    private final ItemStatusRepository itemStatusRepository;

    public ProductService(ProductRepository productRepository,
                          ItemTypeRepository itemTypeRepository,
                          CurrencyRepository currencyRepository,
                          AdminUserProjectRepository adminUserProjectRepository,
                          ItemAttributeRepository itemAttributeRepository,
                          ItemAttributeValueRepository itemAttributeValueRepository,
                          OrderItemRepository orderItemRepository,
                          CategoryRepository categoryRepository,
                          ItemStatusRepository itemStatusRepository,
                          ItemImageService itemImageService) {
        this.productRepository = productRepository;
        this.itemTypeRepository = itemTypeRepository;
        this.currencyRepository = currencyRepository;
        this.adminUserProjectRepository = adminUserProjectRepository;
        this.itemAttributeRepository = itemAttributeRepository;
        this.itemAttributeValueRepository = itemAttributeValueRepository;
        this.orderItemRepository = orderItemRepository;
        this.categoryRepository = categoryRepository;
        this.itemStatusRepository = itemStatusRepository;
        this.itemImageService = itemImageService;
    }

    /* =========================================================
       TENANT SAFE CORE
       ========================================================= */

    private Product getTenantProductOrThrow(Long id, Long ownerProjectId) {
        return productRepository.findByIdAndTenant(id, ownerProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
    }

    public ProductResponse getTenant(Long id, Long ownerProjectId) {
        return toResponse(getTenantProductOrThrow(id, ownerProjectId));
    }

    /* =========================================================
       STATUS HELPERS
       ========================================================= */

   
    private ItemStatus resolveStatusOrDefault(String rawStatusCode) {
        String normalized = normalizeStatusCode(rawStatusCode);
        String finalCode = (normalized == null) ? STATUS_DRAFT : normalized;

        return itemStatusRepository.findByCode(finalCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid statusCode: " + finalCode));
    }

    private String normalizeStatusCode(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isBlank()) return null;
        return s.toUpperCase(Locale.ROOT);
    }

    private String getStatusCode(Product p) {
        if (p == null || p.getStatus() == null || p.getStatus().getCode() == null) {
            return null;
        }
        return p.getStatus().getCode().trim().toUpperCase(Locale.ROOT);
    }

    private boolean isPublicVisibleStatus(Product p) {
        String code = getStatusCode(p);
        return STATUS_PUBLISHED.equals(code) || STATUS_UPCOMING.equals(code);
    }

    private boolean isPurchasableStatus(Product p) {
        String code = getStatusCode(p);
        return STATUS_PUBLISHED.equals(code);
    }

    /* =========================================================
       VALIDATION HELPERS
       ========================================================= */

    private void assertItemTypeBelongsToOwnerProject(AdminUserProject ownerProject, ItemType itemType) {
        if (ownerProject == null) throw new IllegalArgumentException("ownerProject is required");
        if (itemType == null) throw new IllegalArgumentException("itemType is required");

        if (ownerProject.getProject() == null || ownerProject.getProject().getId() == null) {
            throw new IllegalArgumentException("OwnerProject project is missing");
        }
        if (itemType.getCategory() == null
                || itemType.getCategory().getProject() == null
                || itemType.getCategory().getProject().getId() == null) {
            throw new IllegalArgumentException("ItemType category/project is missing");
        }

        if (!itemType.getCategory().getProject().getId().equals(ownerProject.getProject().getId())) {
            throw new IllegalArgumentException("Category/ItemType does not belong to this ownerProject's project");
        }
    }

    private ItemType resolveDefaultItemTypeForCategory(Long categoryId) {
        if (categoryId == null) {
            throw new IllegalArgumentException("categoryId is required when itemTypeId is not provided");
        }

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid categoryId: " + categoryId));

        return itemTypeRepository.findByCategory_IdAndDefaultForCategoryTrue(categoryId)
                .orElseGet(() -> {
                    ItemType t = new ItemType();
                    t.setCategory(category);
                    t.setName("DEFAULT_" + category.getId());
                    t.setDefaultForCategory(true);
                    return itemTypeRepository.save(t);
                });
    }

    
    private String trimToNull(String v) {
        if (v == null) return null;
        String s = v.trim();
        return s.isBlank() ? null : s;
    }

    private void assertMaxLen(String field, String value, int max) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException(field + " is too long");
        }
    }

    private void validateProductBusinessRules(
            ProductType productType,
            boolean virtualProduct,
            boolean downloadable,
            String downloadUrl,
            String externalUrl,
            String buttonText
    ) {
        ProductType finalType = (productType == null) ? ProductType.SIMPLE : productType;

        String cleanDownloadUrl = trimToNull(downloadUrl);
        String cleanExternalUrl = trimToNull(externalUrl);
        String cleanButtonText = trimToNull(buttonText);

        assertMaxLen("downloadUrl", cleanDownloadUrl, 2000);
        assertMaxLen("externalUrl", cleanExternalUrl, 2000);
        assertMaxLen("buttonText", cleanButtonText, 500);

        if (downloadable) {
            if (finalType != ProductType.SIMPLE) {
                throw new IllegalArgumentException("Downloadable product must use SIMPLE product type");
            }
            if (!virtualProduct) {
                throw new IllegalArgumentException("Downloadable product must be virtual");
            }
            if (cleanDownloadUrl == null) {
                throw new IllegalArgumentException("Download URL is required for downloadable product");
            }
            if (cleanExternalUrl != null) {
                throw new IllegalArgumentException("Downloadable product cannot have externalUrl");
            }
        }

        if (finalType == ProductType.EXTERNAL) {
            if (cleanExternalUrl == null) {
                throw new IllegalArgumentException("External URL is required for external product");
            }
            if (downloadable) {
                throw new IllegalArgumentException("External product cannot be downloadable");
            }
        }
    }

    private ProductResponse hideCustomerDownloadUrl(ProductResponse r) {
        r.setDownloadUrl(null);
        return r;
    }
    
    private Currency resolveCurrencyOrDefault(Long currencyId) {
        if (currencyId != null) {
            return currencyRepository.findById(currencyId)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid currency"));
        }

        return currencyRepository.findByCurrencyType("CAD")
                .orElseThrow(() -> new IllegalStateException("Default currency not found"));
    }

    private ItemType resolveItemTypeForCreate(ProductRequest request) {
        if (request.getItemTypeId() != null) {
            return itemTypeRepository.findById(request.getItemTypeId())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid item type"));
        }
        if (request.getCategoryId() != null) {
            return resolveDefaultItemTypeForCategory(request.getCategoryId());
        }
        throw new IllegalArgumentException("Either itemTypeId or categoryId must be provided");
    }

    private ItemType resolveItemTypeForUpdate(ProductUpdateRequest request) {
        if (request.getItemTypeId() != null) {
            return itemTypeRepository.findById(request.getItemTypeId())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid item type"));
        }
        if (request.getCategoryId() != null) {
            return resolveDefaultItemTypeForCategory(request.getCategoryId());
        }
        return null;
    }

    private void assertSkuUniquePerTenant(Long ownerProjectId, String rawSku, Long currentProductIdOrNull) {
        String sku = normalizeSku(rawSku);
        if (sku == null) return;

        boolean exists = (currentProductIdOrNull == null)
                ? productRepository.existsByOwnerProject_IdAndSkuIgnoreCase(ownerProjectId, sku)
                : productRepository.existsByOwnerProject_IdAndSkuIgnoreCaseAndIdNot(ownerProjectId, sku, currentProductIdOrNull);

        if (exists) {
            throw new IllegalArgumentException("SKU already exists in this app");
        }
    }

    /* =========================================================
       CREATE
       ========================================================= */

    public ProductResponse create(ProductRequest request) {
        if (request.getOwnerProjectId() == null) {
            throw new IllegalArgumentException("ownerProjectId is required");
        }

        AdminUserProject ownerProject = adminUserProjectRepository.findById(request.getOwnerProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Owner project (aup_id) not found"));

        ItemType itemType = resolveItemTypeForCreate(request);
        assertItemTypeBelongsToOwnerProject(ownerProject, itemType);

        Currency currency = resolveCurrencyOrDefault(request.getCurrencyId());

        String normalizedSku = normalizeSku(request.getSku());
        assertSkuUniquePerTenant(ownerProject.getId(), normalizedSku, null);

        ItemStatus status = resolveStatusOrDefault(request.getStatusCode());

        validateProductBusinessRules(
                request.getProductType(),
                request.isVirtualProduct(),
                request.isDownloadable(),
                request.getDownloadUrl(),
                request.getExternalUrl(),
                request.getButtonText()
        );

        Product p = new Product();

        p.setOwnerProject(ownerProject);
        p.setItemType(itemType);
        p.setCurrency(currency);
        p.setStatus(status);

        p.setItemName(request.getName());
        p.setDescription(request.getDescription());
        p.setPrice(request.getPrice());

        // old compatibility: keep supporting direct imageUrl if frontend sends it manually
        p.setImageUrl(request.getImageUrl());

        if (request.getStock() != null) p.setStock(request.getStock());

        if (request.getTaxable() != null) p.setTaxable(request.getTaxable());
        if (request.getTaxClass() != null) p.setTaxClass(request.getTaxClass());

        p.setWeightKg(request.getWeightKg());
        p.setWidthCm(request.getWidthCm());
        p.setHeightCm(request.getHeightCm());
        p.setLengthCm(request.getLengthCm());

        p.setSku(normalizedSku);
        p.setProductType(request.getProductType() == null ? ProductType.SIMPLE : request.getProductType());
        p.setVirtualProduct(request.isVirtualProduct());
        p.setDownloadable(request.isDownloadable());
        p.setDownloadUrl(trimToNull(request.getDownloadUrl()));
        p.setExternalUrl(trimToNull(request.getExternalUrl()));
        p.setButtonText(trimToNull(request.getButtonText()));

        p.setSalePrice(request.getSalePrice());
        p.setSaleStart(parseDateTimeOrNull(request.getSaleStart()));
        p.setSaleEnd(parseDateTimeOrNull(request.getSaleEnd()));

        Product saved = productRepository.save(p);

        if (request.getAttributes() != null && !request.getAttributes().isEmpty()) {
            saveAttributes(saved, itemType, request.getAttributes());
        }

        return toResponse(saved);
    }
    
    public ProductResponse createWithImages(ProductRequest request, List<MultipartFile> images) throws IOException {
        ProductResponse created = create(request);

        Product saved = getTenantProductOrThrow(created.getId(), created.getOwnerProjectId());

        if (images != null && !images.isEmpty()) {
            itemImageService.saveNewImages(saved, images, request.getMainImageIndex());
            saved = productRepository.save(saved);
        } else {
            itemImageService.syncMainImageField(saved);
            saved = productRepository.save(saved);
        }

        return toResponse(saved);
    }
    
    public ProductResponse createWithImage(ProductRequest request, MultipartFile image) throws IOException {
        List<MultipartFile> images = (image != null && !image.isEmpty())
                ? List.of(image)
                : Collections.emptyList();

        return createWithImages(request, images);
    }
    
    /* =========================================================
       UPDATE (TENANT SAFE)
       ========================================================= */

    public ProductResponse updateWithImageTenant(Long id, Long ownerProjectId, ProductUpdateRequest request, MultipartFile imageFile) throws IOException {
        List<MultipartFile> images = (imageFile != null && !imageFile.isEmpty())
                ? List.of(imageFile)
                : Collections.emptyList();

        return updateWithImagesTenant(id, ownerProjectId, request, images);
    }

    public ProductResponse updateWithImagesTenant(Long id, Long ownerProjectId, ProductUpdateRequest request, List<MultipartFile> imageFiles) throws IOException {
        Product p = getTenantProductOrThrow(id, ownerProjectId);

        ItemType newItemType = resolveItemTypeForUpdate(request);
        if (newItemType != null) {
            assertItemTypeBelongsToOwnerProject(p.getOwnerProject(), newItemType);
            p.setItemType(newItemType);
        }

        if (request.getSku() != null) {
            String normalizedSku = normalizeSku(request.getSku());
            assertSkuUniquePerTenant(ownerProjectId, normalizedSku, id);
            p.setSku(normalizedSku);
        }

        if (request.getStatusCode() != null && !request.getStatusCode().isBlank()) {
            p.setStatus(resolveStatusOrDefault(request.getStatusCode()));
        }

        ProductType finalProductType = request.getProductType() != null
                ? request.getProductType()
                : p.getProductType();

        Boolean finalVirtual = request.getVirtualProduct() != null
                ? request.getVirtualProduct()
                : p.isVirtualProduct();

        Boolean finalDownloadable = request.getDownloadable() != null
                ? request.getDownloadable()
                : p.isDownloadable();

        String finalDownloadUrl = request.getDownloadUrl() != null
                ? request.getDownloadUrl()
                : p.getDownloadUrl();

        String finalExternalUrl = request.getExternalUrl() != null
                ? request.getExternalUrl()
                : p.getExternalUrl();

        String finalButtonText = request.getButtonText() != null
                ? request.getButtonText()
                : p.getButtonText();

        validateProductBusinessRules(
                finalProductType,
                Boolean.TRUE.equals(finalVirtual),
                Boolean.TRUE.equals(finalDownloadable),
                finalDownloadUrl,
                finalExternalUrl,
                finalButtonText
        );

        if (request.getName() != null) p.setItemName(request.getName());
        if (request.getDescription() != null) p.setDescription(request.getDescription());
        if (request.getPrice() != null) p.setPrice(request.getPrice());
        if (request.getStock() != null) p.setStock(request.getStock());

        if (request.getTaxable() != null) p.setTaxable(request.getTaxable());
        if (request.getTaxClass() != null) p.setTaxClass(request.getTaxClass());

        if (request.getWeightKg() != null) p.setWeightKg(request.getWeightKg());
        if (request.getWidthCm() != null) p.setWidthCm(request.getWidthCm());
        if (request.getHeightCm() != null) p.setHeightCm(request.getHeightCm());
        if (request.getLengthCm() != null) p.setLengthCm(request.getLengthCm());

        p.setProductType(finalProductType);
        p.setVirtualProduct(Boolean.TRUE.equals(finalVirtual));
        p.setDownloadable(Boolean.TRUE.equals(finalDownloadable));
        p.setDownloadUrl(trimToNull(finalDownloadUrl));
        p.setExternalUrl(trimToNull(finalExternalUrl));
        p.setButtonText(trimToNull(finalButtonText));

        if (request.getSalePrice() != null) p.setSalePrice(request.getSalePrice());
        if (request.getSaleStart() != null) p.setSaleStart(parseDateTimeOrNull(request.getSaleStart()));
        if (request.getSaleEnd() != null) p.setSaleEnd(parseDateTimeOrNull(request.getSaleEnd()));

        Product saved = productRepository.save(p);

        if (request.getAttributes() != null) {
            var oldValues = itemAttributeValueRepository.findByItem(saved);
            itemAttributeValueRepository.deleteAll(oldValues);
            saveAttributes(saved, saved.getItemType(), request.getAttributes());
        }

        if (request.getRemoveImageIds() != null && !request.getRemoveImageIds().isEmpty()) {
            itemImageService.removeImages(saved, request.getRemoveImageIds());
        }

        if (imageFiles != null && !imageFiles.isEmpty()) {
            itemImageService.saveNewImages(saved, imageFiles, request.getMainImageIndex());
        }

        if (request.getMainImageId() != null) {
            itemImageService.setMainImage(saved, request.getMainImageId());
        } else if (request.getImageUrl() != null && !request.getImageUrl().isBlank()) {
            // compatibility fallback for older clients sending raw imageUrl manually
            saved.setImageUrl(request.getImageUrl());
        } else {
            itemImageService.syncMainImageField(saved);
        }

        saved = productRepository.save(saved);
        return toResponse(saved);
    }
    
    public ProductResponse updateTenant(Long id, Long ownerProjectId, ProductUpdateRequest request) {
        try {
            return updateWithImagesTenant(id, ownerProjectId, request, Collections.emptyList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /* =========================================================
       READ / LIST
       ========================================================= */

    public List<ProductResponse> listByOwnerProject(Long ownerProjectId) {
        return productRepository.findByOwnerProject_Id(ownerProjectId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
    
    public List<ProductResponse> listCustomerVisibleByOwnerProject(Long ownerProjectId) {
        return productRepository.findByOwnerProject_Id(ownerProjectId).stream()
                .filter(this::isPublicVisibleStatus)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<ProductResponse> listNewArrivals(Long ownerProjectId, Integer daysBack) {
        if (ownerProjectId == null) throw new IllegalArgumentException("ownerProjectId is required");

        int days = (daysBack == null || daysBack <= 0) ? 14 : daysBack;
        LocalDateTime from = LocalDateTime.now().minusDays(days);

        return productRepository
                .findByOwnerProject_IdAndCreatedAtAfterOrderByCreatedAtDesc(ownerProjectId, from)
                .stream()
                .filter(this::isPurchasableStatus)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
    
    public ProductResponse getCustomerVisibleSafe(Long id, Long ownerProjectId) {
        return hideCustomerDownloadUrl(toResponse(getCustomerVisibleProductOrThrow(id, ownerProjectId)));
    }

    public List<ProductResponse> listCustomerVisibleSafeByOwnerProject(Long ownerProjectId) {
        return productRepository.findByOwnerProject_Id(ownerProjectId).stream()
                .filter(this::isPublicVisibleStatus)
                .map(this::toResponse)
                .map(this::hideCustomerDownloadUrl)
                .collect(Collectors.toList());
    }

    public List<ProductResponse> listCustomerVisibleSafeByItemType(Long ownerProjectId, Long itemTypeId) {
        return listByItemType(ownerProjectId, itemTypeId).stream()
                .filter(p -> p.getStatusCode() != null && (
                        STATUS_PUBLISHED.equalsIgnoreCase(p.getStatusCode())
                                || STATUS_UPCOMING.equalsIgnoreCase(p.getStatusCode())
                ))
                .map(this::hideCustomerDownloadUrl)
                .collect(Collectors.toList());
    }
    
    private Product getDownloadableCustomerProductOrThrow(Long id, Long ownerProjectId) {
        Product p = getCustomerVisibleProductOrThrow(id, ownerProjectId);
        if (!p.isDownloadable()) {
            throw new IllegalArgumentException("This product is not downloadable");
        }
        return p;
    }

    public Map<String, Object> getDownloadAccess(Long id, Long ownerProjectId, Long userId) {
        Product p = getDownloadableCustomerProductOrThrow(id, ownerProjectId);

        boolean purchased = orderItemRepository.userPurchasedDownloadableProduct(userId, p.getId());

        return Map.of(
                "productId", p.getId(),
                "downloadable", true,
                "purchased", purchased,
                "canDownload", purchased,
                "message", purchased ? "Ready to download" : "Available after purchase"
        );
    }

    public Map<String, Object> getDownload(Long id, Long ownerProjectId, Long userId) {
        Product p = getDownloadableCustomerProductOrThrow(id, ownerProjectId);

        boolean purchased = orderItemRepository.userPurchasedDownloadableProduct(userId, p.getId());
        if (!purchased) {
            throw new SecurityException("You must purchase this product first");
        }

        String url = trimToNull(p.getDownloadUrl());
        if (url == null) {
            throw new IllegalStateException("Download URL is missing");
        }

        return Map.of(
                "productId", p.getId(),
                "canDownload", true,
                "downloadUrl", url
        );
    }

    public List<ProductResponse> listCustomerVisibleSafeByCategory(Long ownerProjectId, Long categoryId) {
        return listByCategory(ownerProjectId, categoryId).stream()
                .filter(p -> p.getStatusCode() != null && (
                        STATUS_PUBLISHED.equalsIgnoreCase(p.getStatusCode())
                                || STATUS_UPCOMING.equalsIgnoreCase(p.getStatusCode())
                ))
                .map(this::hideCustomerDownloadUrl)
                .collect(Collectors.toList());
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public List<ProductResponse> listBestSellers(Long ownerProjectId, Integer limit) {
        if (ownerProjectId == null) throw new IllegalArgumentException("ownerProjectId is required");
        int max = (limit == null || limit <= 0) ? 10 : limit;

        List<Object[]> rows = orderItemRepository.findBestSellingItemsByOwnerProject(ownerProjectId);
        if (rows.isEmpty()) return List.of();

        List<Long> itemIdsOrdered = rows.stream()
                .map(r -> ((Number) r[0]).longValue())
                .distinct()
                .limit(max)
                .toList();

        if (itemIdsOrdered.isEmpty()) return List.of();

        List<Product> products = productRepository.findByIdIn(itemIdsOrdered);
        Map<Long, Product> productById = new HashMap<>();
        for (Product p : products) productById.put(p.getId(), p);

        return itemIdsOrdered.stream()
                .map(productById::get)
                .filter(Objects::nonNull)
                .filter(this::isPublicVisibleStatus)
                .map(this::toResponse)
                .toList();
    }
    
    public List<ProductResponse> listCustomerVisibleSafeNewArrivals(Long ownerProjectId, Integer daysBack) {
        if (ownerProjectId == null) throw new IllegalArgumentException("ownerProjectId is required");

        int days = (daysBack == null || daysBack <= 0) ? 14 : daysBack;
        LocalDateTime from = LocalDateTime.now().minusDays(days);

        return productRepository
                .findByOwnerProject_IdAndCreatedAtAfterOrderByCreatedAtDesc(ownerProjectId, from)
                .stream()
                .filter(this::isPurchasableStatus)
                .map(this::toResponse)
                .map(this::hideCustomerDownloadUrl)
                .collect(Collectors.toList());
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public List<ProductResponse> listCustomerVisibleSafeBestSellers(Long ownerProjectId, Integer limit) {
        if (ownerProjectId == null) throw new IllegalArgumentException("ownerProjectId is required");
        int max = (limit == null || limit <= 0) ? 10 : limit;

        List<Object[]> rows = orderItemRepository.findBestSellingItemsByOwnerProject(ownerProjectId);
        if (rows.isEmpty()) return List.of();

        List<Long> itemIdsOrdered = rows.stream()
                .map(r -> ((Number) r[0]).longValue())
                .distinct()
                .limit(max)
                .toList();

        if (itemIdsOrdered.isEmpty()) return List.of();

        List<Product> products = productRepository.findByIdIn(itemIdsOrdered);
        Map<Long, Product> productById = new HashMap<>();
        for (Product p : products) productById.put(p.getId(), p);

        return itemIdsOrdered.stream()
                .map(productById::get)
                .filter(Objects::nonNull)
                .filter(this::isPublicVisibleStatus)
                .map(this::toResponse)
                .map(this::hideCustomerDownloadUrl)
                .toList();
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public List<ProductResponse> listCustomerVisibleSafeDiscounted(Long ownerProjectId) {
        if (ownerProjectId == null) {
            throw new IllegalArgumentException("ownerProjectId is required");
        }

        LocalDateTime now = LocalDateTime.now();

        return productRepository.findActiveDiscountedByOwnerProject(ownerProjectId, now)
                .stream()
                .filter(this::isRealActiveFlashSale)
                .map(this::toResponse)
                .map(this::hideCustomerDownloadUrl)
                .collect(Collectors.toList());
    }

    public List<ProductResponse> listByItemType(Long ownerProjectId, Long itemTypeId) {
        if (ownerProjectId == null) throw new IllegalArgumentException("ownerProjectId is required");
        if (itemTypeId == null) throw new IllegalArgumentException("itemTypeId is required");

        AdminUserProject aup = adminUserProjectRepository.findById(ownerProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid ownerProjectId"));

        ItemType type = itemTypeRepository.findById(itemTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid itemTypeId"));

        if (!type.getCategory().getProject().getId().equals(aup.getProject().getId())) {
            throw new IllegalArgumentException("itemTypeId does not belong to ownerProject's project");
        }

        return productRepository
                .findByOwnerProject_IdAndItemType_Id(ownerProjectId, itemTypeId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
    
    public List<ProductResponse> listCustomerVisibleByItemType(Long ownerProjectId, Long itemTypeId) {
        return listByItemType(ownerProjectId, itemTypeId).stream()
                .filter(p -> p.getStatusCode() != null && (
                        STATUS_PUBLISHED.equalsIgnoreCase(p.getStatusCode())
                                || STATUS_UPCOMING.equalsIgnoreCase(p.getStatusCode())
                ))
                .collect(Collectors.toList());
    }

    public List<ProductResponse> listByCategory(Long ownerProjectId, Long categoryId) {
        if (ownerProjectId == null) throw new IllegalArgumentException("ownerProjectId is required");
        if (categoryId == null) throw new IllegalArgumentException("categoryId is required");

        AdminUserProject aup = adminUserProjectRepository.findById(ownerProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid ownerProjectId"));

        Category cat = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid categoryId"));

        if (!cat.getProject().getId().equals(aup.getProject().getId())) {
            throw new IllegalArgumentException("categoryId does not belong to ownerProject's project");
        }

        return productRepository
                .findByOwnerProject_IdAndItemType_Category_Id(ownerProjectId, categoryId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
    
    
    public List<ProductResponse> listCustomerVisibleByCategory(Long ownerProjectId, Long categoryId) {
        return listByCategory(ownerProjectId, categoryId).stream()
                .filter(p -> p.getStatusCode() != null && (
                        STATUS_PUBLISHED.equalsIgnoreCase(p.getStatusCode())
                                || STATUS_UPCOMING.equalsIgnoreCase(p.getStatusCode())
                ))
                .collect(Collectors.toList());
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public List<ProductResponse> listDiscounted(Long ownerProjectId) {
        if (ownerProjectId == null) {
            throw new IllegalArgumentException("ownerProjectId is required");
        }

        LocalDateTime now = LocalDateTime.now();

        return productRepository.findActiveDiscountedByOwnerProject(ownerProjectId, now)
                .stream()
                .filter(this::isRealActiveFlashSale)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private boolean isRealActiveFlashSale(Product p) {
        if (p == null) return false;

        String statusCode = getStatusCode(p);
        boolean statusOk = STATUS_PUBLISHED.equals(statusCode) || STATUS_UPCOMING.equals(statusCode);
        if (!statusOk) return false;

        if (p.getPrice() == null || p.getSalePrice() == null) return false;
        if (p.getSalePrice().compareTo(p.getPrice()) >= 0) return false;
        if (p.getSalePrice().compareTo(java.math.BigDecimal.ZERO) <= 0) return false;

        LocalDateTime now = LocalDateTime.now();

        if (p.getSaleStart() == null || p.getSaleEnd() == null) return false;
        if (now.isBefore(p.getSaleStart())) return false;
        if (now.isAfter(p.getSaleEnd())) return false;

        return true;
    }

    /* =========================================================
       DELETE (TENANT SAFE)
       ========================================================= */

    public void deleteTenant(Long id, Long ownerProjectId) {
        Product p = getTenantProductOrThrow(id, ownerProjectId);

        if (orderItemRepository.existsByItem_Id(id)) {
            throw new IllegalStateException(
                    "You can’t delete this product because it’s referenced by orders or present in carts. Archive it instead"
            );
        }

        itemAttributeValueRepository.deleteAllByItem_Id(id);

        itemImageService.deleteAllImages(p);

        productRepository.delete(p);
    }
    /* =========================================================
       ATTRIBUTES
       ========================================================= */

    private void saveAttributes(Product product, ItemType itemType, List<AttributeValueDTO> attributes) {
        if (attributes == null) return;

        AdminUserProject ownerProject = product.getOwnerProject();
        if (ownerProject == null) {
            throw new IllegalStateException("ownerProject (aup_id) must be set on Product before saving attributes");
        }

        for (AttributeValueDTO dto : attributes) {
            if (dto.getCode() == null || dto.getCode().isBlank()) continue;
            if (dto.getValue() == null) continue;

            String code = dto.getCode().trim();

            ItemAttribute attribute = itemAttributeRepository
                    .findByOwnerProjectAndCode(ownerProject, code)
                    .orElseGet(() -> {
                        ItemAttribute attr = new ItemAttribute();
                        attr.setOwnerProject(ownerProject);
                        attr.setItemType(itemType);
                        attr.setCode(code);
                        attr.setLabel(capitalize(code));
                        attr.setDataType(ItemAttributeDataType.STRING);
                        attr.setFilterable(true);
                        attr.setForVariations(false);
                        return itemAttributeRepository.save(attr);
                    });

            ItemAttributeValue value = new ItemAttributeValue();
            value.setItem(product);
            value.setAttribute(attribute);
            value.setValue(dto.getValue());
            itemAttributeValueRepository.save(value);
        }
    }

    /* =========================================================
       IMAGE
       ========================================================= */

    private String saveProductImage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) return null;

        String original = file.getOriginalFilename() == null ? "image" : file.getOriginalFilename();
        String filename = UUID.randomUUID() + "_" + original.replaceAll("\\s+", "_");

        Path baseDir = Paths.get("uploads", "products");
        if (!Files.exists(baseDir)) Files.createDirectories(baseDir);

        Files.copy(file.getInputStream(), baseDir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);

        return "/uploads/products/" + filename;
    }

    private void deleteLocalImageIfManaged(String url) {
        if (url == null || url.isBlank()) return;
        if (!url.startsWith("/uploads/")) return;

        try {
            String fileName = url.substring("/uploads/".length()).replace("\\", "/");
            if (fileName.isBlank()) return;

            Path uploads = Paths.get("uploads");
            Path filePath = uploads.resolve(fileName).normalize();

            Files.deleteIfExists(filePath);
        } catch (Exception ignored) { }
    }

    /* =========================================================
       MAPPING
       ========================================================= */

    private ProductResponse toResponse(Product p) {
        ProductResponse r = new ProductResponse();

        r.setId(p.getId());
        r.setOwnerProjectId(p.getOwnerProject() != null ? p.getOwnerProject().getId() : null);
        r.setItemTypeId(p.getItemType() != null ? p.getItemType().getId() : null);
        r.setCurrencyId(p.getCurrency() != null ? p.getCurrency().getId() : null);

        if (p.getItemType() != null && p.getItemType().getCategory() != null) {
            r.setCategoryId(p.getItemType().getCategory().getId());
        }

        r.setName(p.getItemName());
        r.setDescription(p.getDescription());
        r.setPrice(p.getPrice());
        r.setStock(p.getStock());
        r.setImageUrl(p.getImageUrl());
        r.setImages(itemImageService.getImages(p.getId()));

        r.setStatusId(p.getStatus() != null ? p.getStatus().getId() : null);
        r.setStatusCode(p.getStatus() != null ? p.getStatus().getCode() : null);
        r.setStatusName(p.getStatus() != null ? p.getStatus().getName() : null);

        r.setSku(p.getSku());
        r.setProductType(p.getProductType());
        r.setVirtualProduct(p.isVirtualProduct());
        r.setDownloadable(p.isDownloadable());
        r.setDownloadUrl(p.getDownloadUrl());
        r.setExternalUrl(p.getExternalUrl());
        r.setButtonText(p.getButtonText());

        r.setSalePrice(p.getSalePrice());
        r.setSaleStart(p.getSaleStart());
        r.setSaleEnd(p.getSaleEnd());

        r.setEffectivePrice(p.getEffectivePrice());
        r.setOnSale(p.isOnSaleNow());

        var values = itemAttributeValueRepository.findByItem(p);
        List<AttributeValueDTO> attrs = values.stream()
                .map(v -> {
                    AttributeValueDTO dto = new AttributeValueDTO();
                    dto.setCode(v.getAttribute().getCode());
                    dto.setValue(v.getValue());
                    return dto;
                })
                .collect(Collectors.toList());
        r.setAttributes(attrs);

        r.setTaxable(p.isTaxable());
        r.setTaxClass(p.getTaxClass());

        r.setWeightKg(p.getWeightKg());
        r.setWidthCm(p.getWidthCm());
        r.setHeightCm(p.getHeightCm());
        r.setLengthCm(p.getLengthCm());

        return r;
    }

    private LocalDateTime parseDateTimeOrNull(String value) {
        if (value == null || value.isBlank()) return null;

        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) { }

        try {
            return OffsetDateTime.parse(value)
                    .withOffsetSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid datetime format: " + value);
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private String normalizeSku(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isBlank()) return null;
        return s.toUpperCase();
    }
    
    
    private Product getCustomerVisibleProductOrThrow(Long id, Long ownerProjectId) {
        Product product = getTenantProductOrThrow(id, ownerProjectId);
        if (!isPublicVisibleStatus(product)) {
            throw new IllegalArgumentException("Product not found");
        }
        return product;
    }

    public ProductResponse getCustomerVisible(Long id, Long ownerProjectId) {
        return toResponse(getCustomerVisibleProductOrThrow(id, ownerProjectId));
    }
}