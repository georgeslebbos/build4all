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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

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

    /* ------------------------ helpers (same pattern as ActivityController) ------------------------ */

    private String strip(String auth) {
        return auth == null ? "" : auth.replace("Bearer ", "").trim();
    }

    private boolean hasRole(String token, String... roles) {
        String role = jwtUtil.extractRole(token);
        if (role == null) return false;
        for (String r : roles) {
            if (r.equalsIgnoreCase(role)) return true;
        }
        return false;
    }

    /* ------------------------ create ------------------------ */

    @PostMapping(value = "/with-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createWithImageFlat(
            @RequestHeader("Authorization") String auth,
            @RequestParam Long ownerProjectId,
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

            // 👇 IMPORTANT: must match your Postman key
            @RequestPart(value = "image", required = false) MultipartFile image
    ) throws Exception {

        ProductRequest req = new ProductRequest();
        req.setOwnerProjectId(ownerProjectId);
        req.setItemTypeId(itemTypeId);
        req.setCategoryId(categoryId);
        req.setCurrencyId(currencyId);
        req.setName(name);
        req.setDescription(description);
        req.setPrice(price);
        req.setStock(stock);
        req.setStatus(status);
        req.setSku(sku);

        if (productType != null) {
            req.setProductType(productType); // or enum mapping
        }

        req.setVirtualProduct(virtualProduct != null && virtualProduct);
        req.setDownloadable(downloadable != null && downloadable);
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
            List<AttributeValueDTO> attrs = om.readValue(
                    attributesJson, new TypeReference<List<AttributeValueDTO>>() {});
            req.setAttributes(attrs);
        }

        // ✅ THIS is the key line
        ProductResponse saved = productService.createWithImage(req, image);

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /* ------------------------ update ------------------------ */

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Update product with optional image (flat form-data)")
    public ResponseEntity<?> updateWithImage(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id,

            // ✅ This will bind ALL text fields from form-data into ProductUpdateRequest
            @ModelAttribute ProductUpdateRequest request,

            // ✅ Same fix as create
            @RequestParam(value = "image", required = false) MultipartFile image
    ) {
        String token = strip(auth);
        if (!hasRole(token, "OWNER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Owner role required."));
        }

        try {
            ProductResponse updated = productService.updateWithImage(id, request, image);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /* ------------------------ delete ------------------------ */

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete product")
    public ResponseEntity<?> delete(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id
    ) {
        String token = strip(auth);
        // same logic as Activity delete: only BUSINESS
        if (!hasRole(token, "OWNER")) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Only Owner users can delete products."));
        }

        try {
            productService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /* ------------------------ get one (public) ------------------------ */

    @GetMapping("/{id}")
    @Operation(summary = "Get product by id")
    public ResponseEntity<?> getById(@RequestHeader("Authorization") String auth,
            @PathVariable Long id) {
        String token = strip(auth);
        // same logic as Activity delete: only BUSINESS
        if (!hasRole(token, "USER", "OWNER")) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Only users can access the product."));
        }
       try {
            ProductResponse p = productService.get(id);
            return ResponseEntity.ok(p);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Product not found"));
        }
    }

    /* ------------------------ lists (public, like activities getAll/upcoming) ------------------------ */

    @GetMapping
    @Operation(summary = "List products (by ownerProject, itemType, or category)")
    public ResponseEntity<?> list(
            @RequestHeader("Authorization") String auth,
            @RequestParam Long ownerProjectId,
            @RequestParam(required = false) Long itemTypeId,
            @RequestParam(required = false) Long categoryId) {
        String token = strip(auth);
        // same logic as Activity delete: only BUSINESS
        if (!hasRole(token, "USER", "OWNER")) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Only users can access the list of products."));
        }
        try {
            List<ProductResponse> result;
            if (itemTypeId != null) {
                result = productService.listByItemType(itemTypeId);
            } else if (categoryId != null) {
                result = productService.listByCategory(categoryId);
            } else {
                result = productService.listByOwnerProject(ownerProjectId);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/new-arrivals")
    @Operation(summary = "List new arrival products for an app (ownerProject)")
    public ResponseEntity<?> listNewArrivals(
            @RequestHeader("Authorization") String auth,
            @RequestParam Long ownerProjectId,
            @RequestParam(required = false) Integer days
    ) {
        String token = strip(auth);
        // same logic as Activity delete: only BUSINESS
        if (!hasRole(token, "USER", "OWNER")) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Only users can access the List of new arrival products for an app."));
        }
        try {
            List<ProductResponse> result = productService.listNewArrivals(ownerProjectId, days);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/best-sellers")
    @Operation(summary = "List best-selling products for an app (ownerProject)")
    public ResponseEntity<?> listBestSellers(
            @RequestHeader("Authorization") String auth,
            @RequestParam Long ownerProjectId,
            @RequestParam(required = false) Integer limit
    ) {
        String token = strip(auth);
        // same logic as Activity delete: only BUSINESS
        if (!hasRole(token, "USER", "OWNER")) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Only users can access the List of best-selling products for an app."));
        }
        try {
            var result = productService.listBestSellers(ownerProjectId, limit);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/discounted")
    @Operation(summary = "List discounted/on-sale products for an app (ownerProject)")
    public ResponseEntity<?> listDiscounted(
            @RequestHeader("Authorization") String auth,
            @RequestParam Long ownerProjectId
    ) {
        String token = strip(auth);
        // same logic as Activity delete: only BUSINESS
        if (!hasRole(token, "USER", "OWNER")) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Only users can access the List of discounted/on-sale products for an app."));
        }
        try {
            var result = productService.listDiscounted(ownerProjectId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
    
    
    /* ------------------------ owner app products (for dashboard) ------------------------ */

    @GetMapping("/owner/app-products")
    @Operation(summary = "List all products for one app (ownerProject)")
    public ResponseEntity<?> listOwnerAppProducts(
            @RequestHeader("Authorization") String auth,
            @RequestParam Long ownerProjectId
    ) {
        String token = strip(auth);

        // Only OWNER can use this endpoint (admin app)
        if (!hasRole(token, "OWNER")) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Owner role required."));
        }

        try {
            // This should already exist in ProductService (you’re using it in /api/products list)
            List<ProductResponse> result = productService.listByOwnerProject(ownerProjectId);

            if (result.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "No products found for this app."));
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

    
    
}
