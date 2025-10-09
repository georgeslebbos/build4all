package com.build4all.features.ecommerce.web;

import com.build4all.features.ecommerce.domain.Product;
import com.build4all.features.ecommerce.service.ProductService;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/products")
public class ProductController {

 private final ProductService productService;
 public ProductController(ProductService productService) { this.productService = productService; }

 @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
 public ResponseEntity<Product> create(
         @RequestParam("name") String name,
         @RequestParam("itemTypeId") Long itemTypeId,
         @RequestParam(value = "description", required = false) String description,
         @RequestParam("price") BigDecimal price,
         @RequestParam(value = "stock", required = false) Integer stock,
         @RequestParam(value = "status", defaultValue = "Upcoming") String status,
         @RequestParam("businessId") Long businessId,
         @RequestParam(value = "sku", required = false) Long sku,
         @RequestPart(value = "image", required = false) MultipartFile image
 ) throws IOException {
     Product saved = productService.createProductWithImage(
             name, itemTypeId, description, price, stock, status, businessId, sku, image
     );
     return ResponseEntity.ok(saved);
 }

 @PutMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
 public ResponseEntity<Product> update(
         @PathVariable Long id,
         @RequestParam("name") String name,
         @RequestParam("itemTypeId") Long itemTypeId,
         @RequestParam(value = "description", required = false) String description,
         @RequestParam("price") BigDecimal price,
         @RequestParam(value = "stock", required = false) Integer stock,
         @RequestParam(value = "status", defaultValue = "Upcoming") String status,
         @RequestParam("businessId") Long businessId,
         @RequestParam(value = "sku", required = false) Long sku,
         @RequestPart(value = "image", required = false) MultipartFile image,
         @RequestParam(value = "imageRemoved", defaultValue = "false") boolean imageRemoved
 ) throws IOException {
     Product saved = productService.updateProductWithImage(
             id, name, itemTypeId, description, price, stock, status, businessId, sku, image, imageRemoved
     );
     return ResponseEntity.ok(saved);
 }
}

