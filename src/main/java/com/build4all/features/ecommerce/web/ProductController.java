package com.build4all.features.ecommerce.web;

import com.build4all.features.ecommerce.dto.ProductRequest;
import com.build4all.features.ecommerce.dto.ProductResponse;
import com.build4all.features.ecommerce.dto.ProductUpdateRequest;
import com.build4all.features.ecommerce.service.ProductService;
import com.build4all.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping
    @Operation(summary = "Create product")
    public ResponseEntity<?> create(
            @RequestHeader("Authorization") String auth,
            @RequestBody ProductRequest request
    ) {
        String token = strip(auth);
        if (!hasRole(token, "OWNER")) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Owner role required."));
        }

        try {
            ProductResponse saved = productService.create(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /* ------------------------ update ------------------------ */

    @PutMapping("/{id}")
    @Operation(summary = "Update product")
    public ResponseEntity<?> update(
            @RequestHeader("Authorization") String auth,
            @PathVariable Long id,
            @RequestBody ProductUpdateRequest request
    ) {
        String token = strip(auth);
        if (!hasRole(token, "OWNER")) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Owner role required."));
        }

        try {
            ProductResponse updated = productService.update(id, request);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
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
