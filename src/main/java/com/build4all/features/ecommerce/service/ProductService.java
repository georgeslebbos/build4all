// src/main/java/com/build4all/features/ecommerce/service/ProductService.java
package com.build4all.features.ecommerce.service;

import com.build4all.admin.domain.AdminUserProject;
import com.build4all.admin.repository.AdminUserProjectRepository;
import com.build4all.catalog.domain.Category;
import com.build4all.catalog.domain.Currency;
import com.build4all.catalog.domain.ItemAttribute;
import com.build4all.catalog.domain.ItemAttributeDataType;
import com.build4all.catalog.domain.ItemAttributeValue;
import com.build4all.catalog.domain.ItemType;
import com.build4all.catalog.repository.CategoryRepository;
import com.build4all.catalog.repository.CurrencyRepository;
import com.build4all.catalog.repository.ItemAttributeRepository;
import com.build4all.catalog.repository.ItemAttributeValueRepository;
import com.build4all.catalog.repository.ItemTypeRepository;
import com.build4all.features.ecommerce.domain.Product;
import com.build4all.features.ecommerce.dto.AttributeValueDTO;
import com.build4all.features.ecommerce.dto.ProductRequest;
import com.build4all.features.ecommerce.dto.ProductResponse;
import com.build4all.features.ecommerce.dto.ProductUpdateRequest;
import com.build4all.features.ecommerce.repository.ProductRepository;
import com.build4all.order.repository.OrderItemRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final ItemTypeRepository itemTypeRepository;
    private final CurrencyRepository currencyRepository;
    private final AdminUserProjectRepository adminUserProjectRepository;
    private final ItemAttributeRepository itemAttributeRepository;
    private final ItemAttributeValueRepository itemAttributeValueRepository;
    private final OrderItemRepository orderItemRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository,
                          ItemTypeRepository itemTypeRepository,
                          CurrencyRepository currencyRepository,
                          AdminUserProjectRepository adminUserProjectRepository,
                          ItemAttributeRepository itemAttributeRepository,
                          ItemAttributeValueRepository itemAttributeValueRepository,
                          OrderItemRepository orderItemRepository,
                          CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.itemTypeRepository = itemTypeRepository;
        this.currencyRepository = currencyRepository;
        this.adminUserProjectRepository = adminUserProjectRepository;
        this.itemAttributeRepository = itemAttributeRepository;
        this.itemAttributeValueRepository = itemAttributeValueRepository;
        this.orderItemRepository = orderItemRepository;
        this.categoryRepository = categoryRepository;
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

    private void assertSkuUniquePerTenant(Long ownerProjectId, String sku, Long currentProductIdOrNull) {
        if (sku == null || sku.isBlank()) return;

        boolean exists = productRepository.existsByOwnerProject_IdAndSkuIgnoreCase(ownerProjectId, sku);
        if (!exists) return;

        if (currentProductIdOrNull == null) {
            throw new IllegalArgumentException("SKU already exists in this app");
        }

        // If updating, allow same product keep same sku
        Product existing = productRepository.findByOwnerProject_Id(ownerProjectId).stream()
                .filter(p -> p.getSku() != null && p.getSku().equalsIgnoreCase(sku))
                .findFirst()
                .orElse(null);

        if (existing != null && !existing.getId().equals(currentProductIdOrNull)) {
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

        // ✅ SKU tenant-safe uniqueness
        assertSkuUniquePerTenant(ownerProject.getId(), request.getSku(), null);

        Product p = new Product();

        p.setOwnerProject(ownerProject);
        p.setItemType(itemType);
        p.setCurrency(currency);

        // ✅ keep old naming: Item base field
        p.setItemName(request.getName());
        p.setDescription(request.getDescription());
        p.setPrice(request.getPrice());
        p.setStatus(request.getStatus() != null ? request.getStatus() : "Upcoming");
        p.setImageUrl(request.getImageUrl());
        if (request.getStock() != null) p.setStock(request.getStock());

        if (request.getTaxable() != null) p.setTaxable(request.getTaxable());
        if (request.getTaxClass() != null) p.setTaxClass(request.getTaxClass());

        p.setWeightKg(request.getWeightKg());
        p.setWidthCm(request.getWidthCm());
        p.setHeightCm(request.getHeightCm());
        p.setLengthCm(request.getLengthCm());

        p.setSku(request.getSku());
        p.setProductType(request.getProductType());
        p.setVirtualProduct(request.isVirtualProduct());
        p.setDownloadable(request.isDownloadable());
        p.setDownloadUrl(request.getDownloadUrl());
        p.setExternalUrl(request.getExternalUrl());
        p.setButtonText(request.getButtonText());

        p.setSalePrice(request.getSalePrice());
        p.setSaleStart(parseDateTimeOrNull(request.getSaleStart()));
        p.setSaleEnd(parseDateTimeOrNull(request.getSaleEnd()));

        Product saved = productRepository.save(p);

        if (request.getAttributes() != null && !request.getAttributes().isEmpty()) {
            saveAttributes(saved, itemType, request.getAttributes());
        }

        return toResponse(saved);
    }

    public ProductResponse createWithImage(ProductRequest request, MultipartFile image) throws IOException {
        String url = saveProductImage(image);
        if (url != null) request.setImageUrl(url);
        return create(request);
    }

    /* =========================================================
       UPDATE (TENANT SAFE)
       ========================================================= */

    public ProductResponse updateWithImageTenant(Long id, Long ownerProjectId, ProductUpdateRequest request, MultipartFile imageFile) throws IOException {
        Product p = getTenantProductOrThrow(id, ownerProjectId);

        ItemType newItemType = resolveItemTypeForUpdate(request);
        if (newItemType != null) {
            assertItemTypeBelongsToOwnerProject(p.getOwnerProject(), newItemType);
            p.setItemType(newItemType);
        }

        if (request.getSku() != null) {
            assertSkuUniquePerTenant(ownerProjectId, request.getSku(), id);
            p.setSku(request.getSku());
        }

        if (request.getName() != null) p.setItemName(request.getName());
        if (request.getDescription() != null) p.setDescription(request.getDescription());
        if (request.getPrice() != null) p.setPrice(request.getPrice());
        if (request.getStatus() != null) p.setStatus(request.getStatus());
        if (request.getStock() != null) p.setStock(request.getStock());

        if (request.getTaxable() != null) p.setTaxable(request.getTaxable());
        if (request.getTaxClass() != null) p.setTaxClass(request.getTaxClass());

        if (request.getWeightKg() != null) p.setWeightKg(request.getWeightKg());
        if (request.getWidthCm() != null) p.setWidthCm(request.getWidthCm());
        if (request.getHeightCm() != null) p.setHeightCm(request.getHeightCm());
        if (request.getLengthCm() != null) p.setLengthCm(request.getLengthCm());

        if (request.getProductType() != null) p.setProductType(request.getProductType());
        if (request.getVirtualProduct() != null) p.setVirtualProduct(request.getVirtualProduct());
        if (request.getDownloadable() != null) p.setDownloadable(request.getDownloadable());
        if (request.getDownloadUrl() != null) p.setDownloadUrl(request.getDownloadUrl());
        if (request.getExternalUrl() != null) p.setExternalUrl(request.getExternalUrl());
        if (request.getButtonText() != null) p.setButtonText(request.getButtonText());

        if (request.getSalePrice() != null) p.setSalePrice(request.getSalePrice());
        if (request.getSaleStart() != null) p.setSaleStart(parseDateTimeOrNull(request.getSaleStart()));
        if (request.getSaleEnd() != null) p.setSaleEnd(parseDateTimeOrNull(request.getSaleEnd()));

        // ✅ image rules
        if (imageFile != null && !imageFile.isEmpty()) {
            deleteLocalImageIfManaged(p.getImageUrl());
            p.setImageUrl(saveProductImage(imageFile));
        } else if (request.getImageUrl() != null) {
            p.setImageUrl(request.getImageUrl());
        }

        Product saved = productRepository.save(p);

        if (request.getAttributes() != null) {
            var oldValues = itemAttributeValueRepository.findByItem(saved);
            itemAttributeValueRepository.deleteAll(oldValues);
            saveAttributes(saved, saved.getItemType(), request.getAttributes());
        }

        return toResponse(saved);
    }

    public ProductResponse updateTenant(Long id, Long ownerProjectId, ProductUpdateRequest request) {
        try {
            return updateWithImageTenant(id, ownerProjectId, request, null);
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

    public List<ProductResponse> listNewArrivals(Long ownerProjectId, Integer daysBack) {
        if (ownerProjectId == null) throw new IllegalArgumentException("ownerProjectId is required");

        int days = (daysBack == null || daysBack <= 0) ? 14 : daysBack;
        LocalDateTime from = LocalDateTime.now().minusDays(days);

        return productRepository
                .findByOwnerProject_IdAndCreatedAtAfterOrderByCreatedAtDesc(ownerProjectId, from)
                .stream()
                .map(this::toResponse)
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
                .map(this::toResponse)
                .toList();
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

    @Transactional(Transactional.TxType.SUPPORTS)
    public List<ProductResponse> listDiscounted(Long ownerProjectId) {
        if (ownerProjectId == null) throw new IllegalArgumentException("ownerProjectId is required");

        return productRepository.findActiveDiscountedByOwnerProject(ownerProjectId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /* =========================================================
       DELETE (TENANT SAFE)
       ========================================================= */

    public void deleteTenant(Long id, Long ownerProjectId) {
        Product p = getTenantProductOrThrow(id, ownerProjectId);

        deleteLocalImageIfManaged(p.getImageUrl());

        itemAttributeValueRepository.deleteAllByItemId(id);

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
        r.setStatus(p.getStatus());
        r.setImageUrl(p.getImageUrl());

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
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid datetime format: " + value);
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
