// src/main/java/com/build4all/features/ecommerce/web/ProductController.java
package com.build4all.features.ecommerce.web;

import com.build4all.features.ecommerce.domain.ProductType;
import com.build4all.features.ecommerce.dto.AttributeValueDTO;
import com.build4all.features.ecommerce.dto.ProductRequest;
import com.build4all.features.ecommerce.dto.ProductResponse;
import com.build4all.features.ecommerce.dto.ProductUpdateRequest;
import com.build4all.features.ecommerce.service.ProductService;
import com.build4all.security.JwtUtil;
import com.build4all.tax.domain.TaxClass;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    public ProductController(ProductService productService, JwtUtil jwtUtil) {
        this.productService = productService;
        this.jwtUtil = jwtUtil;
    }

    private String strip(String auth) {
        return auth == null ? "" : auth.replaceFirst("(?i)^Bearer\\s+", "").trim();
    }

    private boolean hasRole(String token, String... roles) {
        String role = jwtUtil.extractRole(token);
        if (role == null) return false;
        for (String r : roles) {
            if (r.equalsIgnoreCase(role)) return true;
        }
        return false;
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid or missing token."));
    }

    private ResponseEntity<?> forbidden(String msg) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", msg));
    }

    /**
     * Tenant resolution ONLY from token.
     * OWNER token -> extractOwnerProjectId
     * USER token  -> extractOwnerProjectIdForUser
     */
    private Long resolveOwnerProjectIdFromToken(String token) {
        if (hasRole(token, "OWNER")) {
            return jwtUtil.extractOwnerProjectId(token);
        }
        if (hasRole(token, "USER")) {
            return jwtUtil.extractOwnerProjectIdForUser(token);
        }
        return null; // unsupported role
    }

    private ResponseEntity<?> tenantMissing() {
        return forbidden("Tenant (ownerProjectId) is missing in token claims.");
    }

    private boolean invalidToken(String token) {
        return token == null || token.isBlank() || !jwtUtil.validateToken(token);
    }

    /* ------------------------ create ------------------------ */

    @PostMapping(value = "/with-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create product with optional image (flat form-data) - tenant from token")
    public ResponseEntity<?> createWithImageFlat(
            @RequestHeader(value = "Authorization", required = false) String auth,

            @RequestParam(required = false) Long itemTypeId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long currencyId,

            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) BigDecimal price,
            @RequestParam(required = false) Integer stock,
            @RequestParam(required = false) String status,
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
        String token = strip(auth);
        if (invalidToken(token)) return unauthorized();
        if (!hasRole(token, "OWNER")) return forbidden("Owner role required.");

        Long tokenOwnerProjectId = resolveOwnerProjectIdFromToken(token);
        if (tokenOwnerProjectId == null) return tenantMissing();

        if (itemTypeId == null && categoryId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Either itemTypeId or categoryId must be provided."));
        }

        try {
            ProductRequest req = new ProductRequest();

            // ✅ tenant ONLY from token
            req.setOwnerProjectId(tokenOwnerProjectId);

            req.setItemTypeId(itemTypeId);
            req.setCategoryId(categoryId);
            req.setCurrencyId(currencyId);

            req.setName(name);
            req.setDescription(description);
            req.setPrice(price);
            req.setStock(stock);
            req.setStatus(status);
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

            if (attributesJson != null && !attributesJson.isBlank()) {
                ObjectMapper om = new ObjectMapper();
                List<AttributeValueDTO> attrs =
                        om.readValue(attributesJson, new TypeReference<List<AttributeValueDTO>>() {});
                req.setAttributes(attrs);
            }

            ProductResponse saved = productService.createWithImage(req, image);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (DataIntegrityViolationException e) {
            String msg = String.valueOf(
                    e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage()
            ).toLowerCase();

            if (msg.contains("uk_items_aup_sku_ci")) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "SKU already exists in this app"));
            }

            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Data conflict"));
        
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
        
    }

    /* ------------------------ update ------------------------ */

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Update product with optional image (flat form-data) - tenant from token")
    public ResponseEntity<?> updateWithImage(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id,

            @RequestParam(required = false) Long itemTypeId,
            @RequestParam(required = false) Long categoryId,

            @RequestParam(required = false) String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) BigDecimal price,
            @RequestParam(required = false) Integer stock,
            @RequestParam(required = false) String status,
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
        String token = strip(auth);
        if (invalidToken(token)) return unauthorized();
        if (!hasRole(token, "OWNER")) return forbidden("Owner role required.");

        Long tokenOwnerProjectId = resolveOwnerProjectIdFromToken(token);
        if (tokenOwnerProjectId == null) return tenantMissing();

        try {
            ProductUpdateRequest req = new ProductUpdateRequest();

            req.setItemTypeId(itemTypeId);
            req.setCategoryId(categoryId);

            req.setName(name);
            req.setDescription(description);
            req.setPrice(price);
            req.setStock(stock);
            req.setStatus(status);
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

            if (attributesJson != null && !attributesJson.isBlank()) {
                ObjectMapper om = new ObjectMapper();
                List<AttributeValueDTO> attrs =
                        om.readValue(attributesJson, new TypeReference<List<AttributeValueDTO>>() {});
                req.setAttributes(attrs);
            }

            ProductResponse updated =
                    productService.updateWithImageTenant(id, tokenOwnerProjectId, req, image);

            return ResponseEntity.ok(updated);

        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Product not found"));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (DataIntegrityViolationException e) {
            String msg = String.valueOf(
                    e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage()
            ).toLowerCase();

            if (msg.contains("uk_items_aup_sku_ci")) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "SKU already exists in this app"));
            }

            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Data conflict"));
        
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /* ------------------------ delete ------------------------ */

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id
    ) {
        String token = strip(auth);
        if (invalidToken(token)) return unauthorized();
        if (!hasRole(token, "OWNER")) return forbidden("Only Owner users can delete products.");

        Long tokenOwnerProjectId = resolveOwnerProjectIdFromToken(token);
        if (tokenOwnerProjectId == null) return tenantMissing();

        try {
            productService.deleteTenant(id, tokenOwnerProjectId);
            return ResponseEntity.noContent().build();

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("code", "PRODUCT_NOT_FOUND"));
        } catch (DataIntegrityViolationException e) {
            // FK violation (cart/orders/etc)
            String msg = String.valueOf(e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage()).toLowerCase();

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
    

    /* ------------------------ get one ------------------------ */

    @GetMapping("/{id}")
    @Operation(summary = "Get product by id - tenant from token")
    public ResponseEntity<?> getById(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id
    ) {
        String token = strip(auth);
        if (invalidToken(token)) return unauthorized();
        if (!hasRole(token, "USER", "OWNER")) return forbidden("Only USER/OWNER can access the product.");

        Long tokenOwnerProjectId = resolveOwnerProjectIdFromToken(token);
        if (tokenOwnerProjectId == null) return tenantMissing();

        try {
            ProductResponse p = productService.getTenant(id, tokenOwnerProjectId);
            return ResponseEntity.ok(p);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Product not found"));
        }
    }

    /* ------------------------ lists ------------------------ */

    @GetMapping
    @Operation(summary = "List products - tenant from token")
    public ResponseEntity<?> list(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(required = false) Long itemTypeId,
            @RequestParam(required = false) Long categoryId
    ) {
        String token = strip(auth);
        if (invalidToken(token)) return unauthorized();
        if (!hasRole(token, "USER", "OWNER")) return forbidden("Only USER/OWNER can access products.");

        Long ownerProjectId = resolveOwnerProjectIdFromToken(token);
        if (ownerProjectId == null) return tenantMissing();

        try {
            List<ProductResponse> result;

            if (itemTypeId != null) {
                result = productService.listByItemType(ownerProjectId, itemTypeId);
            } else if (categoryId != null) {
                result = productService.listByCategory(ownerProjectId, categoryId);
            } else {
                result = productService.listByOwnerProject(ownerProjectId);
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
    public ResponseEntity<?> listNewArrivals(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(required = false) Integer days
    ) {
        String token = strip(auth);
        if (invalidToken(token)) return unauthorized();
        if (!hasRole(token, "USER", "OWNER")) return forbidden("Only USER/OWNER can access new arrivals.");

        Long ownerProjectId = resolveOwnerProjectIdFromToken(token);
        if (ownerProjectId == null) return tenantMissing();

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
    public ResponseEntity<?> listBestSellers(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(required = false) Integer limit
    ) {
        String token = strip(auth);
        if (invalidToken(token)) return unauthorized();
        if (!hasRole(token, "USER", "OWNER")) return forbidden("Only USER/OWNER can access best sellers.");

        Long ownerProjectId = resolveOwnerProjectIdFromToken(token);
        if (ownerProjectId == null) return tenantMissing();

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
    public ResponseEntity<?> listDiscounted(
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        String token = strip(auth);
        if (invalidToken(token)) return unauthorized();
        if (!hasRole(token, "USER", "OWNER")) return forbidden("Only USER/OWNER can access discounted products.");

        Long ownerProjectId = resolveOwnerProjectIdFromToken(token);
        if (ownerProjectId == null) return tenantMissing();

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

    @GetMapping("/owner/app-products")
    @Operation(summary = "List all products for one app (OWNER only) - tenant from token")
    public ResponseEntity<?> listOwnerAppProducts(
            @RequestHeader(value = "Authorization", required = false) String auth
    ) {
        String token = strip(auth);
        if (invalidToken(token)) return unauthorized();
        if (!hasRole(token, "OWNER")) return forbidden("Owner role required.");

        Long ownerProjectId = resolveOwnerProjectIdFromToken(token);
        if (ownerProjectId == null) return tenantMissing();

        try {
            return ResponseEntity.ok(productService.listByOwnerProject(ownerProjectId));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
