// src/main/java/com/build4all/features/ecommerce/web/ProductController.java
package com.build4all.features.ecommerce.web;

import com.build4all.features.ecommerce.domain.ProductType;
import com.build4all.features.ecommerce.dto.AttributeValueDTO;
import com.build4all.features.ecommerce.dto.ProductRequest;
import com.build4all.features.ecommerce.dto.ProductResponse;
import com.build4all.features.ecommerce.dto.ProductUpdateRequest;
import com.build4all.features.ecommerce.service.ProductService;
import com.build4all.licensing.dto.OwnerAppAccessResponse;
import com.build4all.licensing.service.LicensingService;
import com.build4all.security.JwtUtil;
import com.build4all.tax.domain.TaxClass;
import com.build4all.webSocket.service.WebSocketEventService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final JwtUtil jwtUtil;
    private final LicensingService licensingService;
    private final WebSocketEventService wsEvents;

    public ProductController(ProductService productService,
                             JwtUtil jwtUtil,
                             LicensingService licensingService,
                             WebSocketEventService wsEvents) {
        this.productService = productService;
        this.jwtUtil = jwtUtil;
        this.licensingService = licensingService;
        this.wsEvents = wsEvents;
    }

    /* =========================================================
     * Helpers
     * ========================================================= */

    private Long tenantFromAuth(String authHeader) {
        return jwtUtil.requireOwnerProjectId(authHeader);
    }

    private ResponseEntity<?> blockIfSubscriptionExceeded(Long ownerProjectId) {
        try {
            OwnerAppAccessResponse access = licensingService.getOwnerDashboardAccess(ownerProjectId);

            if (access == null || !access.isCanAccessDashboard()) {
                String reason = (access != null && access.getBlockingReason() != null)
                        ? access.getBlockingReason()
                        : "SUBSCRIPTION_LIMIT_EXCEEDED";

                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "error", "Subscription limit exceeded. Upgrade your plan or reduce usage.",
                        "code", "SUBSCRIPTION_LIMIT_EXCEEDED",
                        "blockingReason", reason,
                        "ownerProjectId", ownerProjectId
                ));
            }

            return null;
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error", "Unable to validate subscription right now.",
                    "code", "SUBSCRIPTION_CHECK_UNAVAILABLE"
            ));
        }
    }

    private List<AttributeValueDTO> parseAttributes(String attributesJson) throws Exception {
        if (attributesJson == null || attributesJson.isBlank()) return null;
        ObjectMapper om = new ObjectMapper();
        return om.readValue(attributesJson, new TypeReference<List<AttributeValueDTO>>() {});
    }

    private ResponseEntity<?> handleSkuConflict(DataIntegrityViolationException e) {
        String msg = String.valueOf(
                e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage()
        ).toLowerCase();

        if (msg.contains("uk_items_aup_sku_ci")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "SKU already exists in this app"));
        }

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "Data conflict"));
    }

    /* =========================================================
     * CREATE (OWNER only)
     * ========================================================= */

    @PostMapping(value = "/with-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create product with optional image (flat form-data) - tenant from token")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<?> createWithImageFlat(
            @RequestHeader(value = "Authorization", required = false) String auth,

            @RequestParam(required = false) Long itemTypeId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long currencyId,

            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) BigDecimal price,
            @RequestParam(required = false) Integer stock,
            @RequestParam(required = false) String statusCode,
            @RequestParam(required = false) String sku,

            @RequestParam(required = false) ProductType productType,
            @RequestParam(required = false) Boolean virtualProduct,
            @RequestParam(required = false) Boolean downloadable,
            @RequestParam(required = false) String downloadUrl,
            @RequestParam(required = false) String externalUrl,
            @RequestParam(required = false) String buttonText,

            @RequestParam(required = false) BigDecimal salePrice,
            @RequestParam(required = false) String saleStart,
            @RequestParam(required = false) String saleEnd,

            @RequestParam(required = false) Boolean taxable,
            @RequestParam(required = false) TaxClass taxClass,

            @RequestParam(required = false) BigDecimal weightKg,
            @RequestParam(required = false) BigDecimal widthCm,
            @RequestParam(required = false) BigDecimal heightCm,
            @RequestParam(required = false) BigDecimal lengthCm,

            @RequestParam(required = false) String attributesJson,

            @RequestParam(value = "image", required = false) MultipartFile image
    ) {
        if (auth == null || auth.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing Authorization header"));
        }

        Long ownerProjectId = tenantFromAuth(auth);

        ResponseEntity<?> blocked = blockIfSubscriptionExceeded(ownerProjectId);
        if (blocked != null) return blocked;

        if (itemTypeId == null && categoryId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Either itemTypeId or categoryId must be provided."));
        }

        try {
            ProductRequest req = new ProductRequest();

            req.setOwnerProjectId(ownerProjectId);

            req.setItemTypeId(itemTypeId);
            req.setCategoryId(categoryId);
            req.setCurrencyId(currencyId);

            req.setName(name);
            req.setDescription(description);
            req.setPrice(price);
            req.setStock(stock);
            req.setStatusCode(statusCode);
            req.setSku(sku);

            if (productType != null) req.setProductType(productType);

            req.setVirtualProduct(Boolean.TRUE.equals(virtualProduct));
            req.setDownloadable(Boolean.TRUE.equals(downloadable));
            req.setDownloadUrl(downloadUrl);
            req.setExternalUrl(externalUrl);
            req.setButtonText(buttonText);

            req.setSalePrice(salePrice);
            req.setSaleStart(saleStart);
            req.setSaleEnd(saleEnd);

            req.setTaxable(taxable);
            req.setTaxClass(taxClass);

            req.setWeightKg(weightKg);
            req.setWidthCm(widthCm);
            req.setHeightCm(heightCm);
            req.setLengthCm(lengthCm);

            List<AttributeValueDTO> attrs = parseAttributes(attributesJson);
            if (attrs != null) req.setAttributes(attrs);

            ProductResponse saved = productService.createWithImage(req, image);
            wsEvents.sendProductCreated(ownerProjectId, saved);

            return ResponseEntity.status(HttpStatus.CREATED).body(saved);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (DataIntegrityViolationException e) {
            return handleSkuConflict(e);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /* =========================================================
     * UPDATE (OWNER only)
     * ========================================================= */

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Update product with optional image (flat form-data) - tenant from token")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<?> updateWithImage(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id,

            @RequestParam(required = false) Long itemTypeId,
            @RequestParam(required = false) Long categoryId,

            @RequestParam(required = false) String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) BigDecimal price,
            @RequestParam(required = false) Integer stock,
            @RequestParam(required = false) String statusCode,
            @RequestParam(required = false) String sku,

            @RequestParam(required = false) ProductType productType,
            @RequestParam(required = false) Boolean virtualProduct,
            @RequestParam(required = false) Boolean downloadable,
            @RequestParam(required = false) String downloadUrl,
            @RequestParam(required = false) String externalUrl,
            @RequestParam(required = false) String buttonText,

            @RequestParam(required = false) BigDecimal salePrice,
            @RequestParam(required = false) String saleStart,
            @RequestParam(required = false) String saleEnd,

            @RequestParam(required = false) Boolean taxable,
            @RequestParam(required = false) TaxClass taxClass,

            @RequestParam(required = false) BigDecimal weightKg,
            @RequestParam(required = false) BigDecimal widthCm,
            @RequestParam(required = false) BigDecimal heightCm,
            @RequestParam(required = false) BigDecimal lengthCm,

            @RequestParam(required = false) String attributesJson,

            @RequestParam(value = "image", required = false) MultipartFile image
    ) {
        if (auth == null || auth.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing Authorization header"));
        }

        Long ownerProjectId = tenantFromAuth(auth);

        ResponseEntity<?> blocked = blockIfSubscriptionExceeded(ownerProjectId);
        if (blocked != null) return blocked;

        try {
            ProductUpdateRequest req = new ProductUpdateRequest();

            req.setItemTypeId(itemTypeId);
            req.setCategoryId(categoryId);

            req.setName(name);
            req.setDescription(description);
            req.setPrice(price);
            req.setStock(stock);
            req.setStatusCode(statusCode);
            req.setSku(sku);

            if (productType != null) req.setProductType(productType);

            req.setVirtualProduct(virtualProduct);
            req.setDownloadable(downloadable);
            req.setDownloadUrl(downloadUrl);
            req.setExternalUrl(externalUrl);
            req.setButtonText(buttonText);

            req.setSalePrice(salePrice);
            req.setSaleStart(saleStart);
            req.setSaleEnd(saleEnd);

            req.setTaxable(taxable);
            req.setTaxClass(taxClass);

            req.setWeightKg(weightKg);
            req.setWidthCm(widthCm);
            req.setHeightCm(heightCm);
            req.setLengthCm(lengthCm);

            List<AttributeValueDTO> attrs = parseAttributes(attributesJson);
            if (attrs != null) req.setAttributes(attrs);

            ProductResponse updated = productService.updateWithImageTenant(id, ownerProjectId, req, image);
            wsEvents.sendProductUpdated(ownerProjectId, updated);

            if (stock != null) {
                wsEvents.sendStockChanged(ownerProjectId, updated.getId(), 0, stock, "MANUAL_UPDATE", null);
            }

            return ResponseEntity.ok(updated);

        } catch (IllegalArgumentException e) {
            String m = (e.getMessage() == null) ? "" : e.getMessage().toLowerCase();
            if (m.contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Product not found"));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (DataIntegrityViolationException e) {
            return handleSkuConflict(e);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /* =========================================================
     * DELETE (OWNER only)
     * ========================================================= */

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<?> delete(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id
    ) {
        if (auth == null || auth.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing Authorization header"));
        }

        Long ownerProjectId = tenantFromAuth(auth);

        try {
            productService.deleteTenant(id, ownerProjectId);
            wsEvents.sendProductDeleted(ownerProjectId, id);
            return ResponseEntity.noContent().build();

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("code", "PRODUCT_NOT_FOUND"));
        } catch (DataIntegrityViolationException e) {
            String msg = String.valueOf(
                    e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage()
            ).toLowerCase();

            if (msg.contains("cart_items")) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("code", "PRODUCT_DELETE_BLOCKED_CART"));
            }
            if (msg.contains("order_items")) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("code", "PRODUCT_DELETE_BLOCKED_ORDERS"));
            }

            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("code", "PRODUCT_DELETE_BLOCKED_IN_USE"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", "SERVER_ERROR"));
        }
    }

    /* =========================================================
     * READ (USER or OWNER)
     * ========================================================= */

    @GetMapping("/{id}")
    @Operation(summary = "Get product by id - tenant from token")
    @PreAuthorize("hasAnyRole('USER','OWNER')")
    public ResponseEntity<?> getById(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id
    ) {
        if (auth == null || auth.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing Authorization header"));
        }

        Long ownerProjectId = tenantFromAuth(auth);

        try {
            ProductResponse p = isOwner(auth)
                    ? productService.getTenant(id, ownerProjectId)
                    : productService.getCustomerVisible(id, ownerProjectId);

            return ResponseEntity.ok(p);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Product not found"));
        }
    }

    @GetMapping
    @Operation(summary = "List products - tenant from token")
    @PreAuthorize("hasAnyRole('USER','OWNER')")
    public ResponseEntity<?> list(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(required = false) Long itemTypeId,
            @RequestParam(required = false) Long categoryId
    ) {
        if (auth == null || auth.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing Authorization header"));
        }

        Long ownerProjectId = tenantFromAuth(auth);

        try {
            List<ProductResponse> result;

            boolean owner = isOwner(auth);

            if (itemTypeId != null) {
                result = owner
                        ? productService.listByItemType(ownerProjectId, itemTypeId)
                        : productService.listCustomerVisibleByItemType(ownerProjectId, itemTypeId);
            } else if (categoryId != null) {
                result = owner
                        ? productService.listByCategory(ownerProjectId, categoryId)
                        : productService.listCustomerVisibleByCategory(ownerProjectId, categoryId);
            } else {
                result = owner
                        ? productService.listByOwnerProject(ownerProjectId)
                        : productService.listCustomerVisibleByOwnerProject(ownerProjectId);
            }

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/new-arrivals")
    @Operation(summary = "List new arrival products - tenant from token")
    @PreAuthorize("hasAnyRole('USER','OWNER')")
    public ResponseEntity<?> listNewArrivals(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(required = false) Integer days
    ) {
        if (auth == null || auth.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing Authorization header"));
        }

        Long ownerProjectId = tenantFromAuth(auth);

        try {
            return ResponseEntity.ok(productService.listNewArrivals(ownerProjectId, days));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/best-sellers")
    @Operation(summary = "List best-selling products - tenant from token")
    @PreAuthorize("hasAnyRole('USER','OWNER')")
    public ResponseEntity<?> listBestSellers(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(required = false) Integer limit
    ) {
        if (auth == null || auth.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing Authorization header"));
        }

        Long ownerProjectId = tenantFromAuth(auth);

        try {
            return ResponseEntity.ok(productService.listBestSellers(ownerProjectId, limit));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/discounted")
    @Operation(summary = "List discounted products - tenant from token")
    @PreAuthorize("hasAnyRole('USER','OWNER')")
    public ResponseEntity<?> listDiscounted(
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        if (auth == null || auth.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing Authorization header"));
        }

        Long ownerProjectId = tenantFromAuth(auth);

        try {
            return ResponseEntity.ok(productService.listDiscounted(ownerProjectId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /* =========================================================
     * OWNER dashboard list (OWNER only)
     * ========================================================= */

    @GetMapping("/owner/app-products")
    @Operation(summary = "List all products for one app (OWNER only) - tenant from token")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<?> listOwnerAppProducts(
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        if (auth == null || auth.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing Authorization header"));
        }

        Long ownerProjectId = tenantFromAuth(auth);

        try {
            return ResponseEntity.ok(productService.listByOwnerProject(ownerProjectId));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
    
    
    private String roleFromAuth(String authHeader) {
        String role = jwtUtil.extractRole(authHeader);
        return role == null ? "" : role.trim().toUpperCase();
    }

    private boolean isOwner(String authHeader) {
        return "OWNER".equals(roleFromAuth(authHeader));
    }
}