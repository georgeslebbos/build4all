// src/main/java/com/build4all/services/ProductService.java
package com.build4all.features.ecommerce.service;

import com.build4all.business.domain.Businesses;
import com.build4all.catalog.domain.Currency;
import com.build4all.catalog.domain.ItemType;
import com.build4all.features.ecommerce.domain.Product;
import com.build4all.catalog.repository.CurrencyRepository;
import com.build4all.catalog.repository.ItemTypeRepository;
import com.build4all.features.ecommerce.repository.ProductRepository;
import com.build4all.business.service.BusinessService;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.UUID;

@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final ItemTypeRepository itemTypeRepository;
    private final CurrencyRepository currencyRepository;
    private final BusinessService businessService;

    public ProductService(ProductRepository productRepository,
                          ItemTypeRepository itemTypeRepository,
                          CurrencyRepository currencyRepository,
                          BusinessService businessService) {
        this.productRepository = productRepository;
        this.itemTypeRepository = itemTypeRepository;
        this.currencyRepository = currencyRepository;
        this.businessService = businessService;
    }

    public Product createProductWithImage(
            String name,
            Long itemTypeId,
            String description,
            BigDecimal price,
            Integer stock,          // lives on Item (base)
            String status,          // default "Upcoming"
            Long businessId,
            Long sku,             // optional
            MultipartFile image
    ) throws IOException {

        String imageUrl = storeImageIfPresent(image);

        Businesses business = businessService.findById(businessId);
        if (business == null) throw new IllegalArgumentException("Business not found");

        ItemType type = itemTypeRepository.findById(itemTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid item type"));

        Currency currency = currencyRepository.findByCurrencyType("CAD")
                .orElseThrow(() -> new RuntimeException("Default currency not found"));

        Product p = new Product();
        // base item fields
        p.setItemName(name);
        p.setItemType(type);
        p.setDescription(description);
        p.setPrice(price);
        p.setStatus((status != null && !status.isBlank()) ? status : "Upcoming");
        p.setImageUrl(imageUrl);
        p.setBusiness(business);
        p.setCurrency(currency);
        if (stock != null) p.setStockQuantity(stock);

        // product specific
        p.setId(sku);

        return productRepository.save(p);
    }

    public Product updateProductWithImage(
            Long id,
            String name,
            Long itemTypeId,
            String description,
            BigDecimal price,
            Integer stock,
            String status,
            Long businessId,
            Long sku,
            MultipartFile image,
            boolean imageRemoved
    ) throws IOException {

        Product p = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        ItemType type = itemTypeRepository.findById(itemTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid item type"));

        // base fields
        p.setItemName(name);
        p.setItemType(type);
        p.setDescription(description);
        p.setPrice(price);
        if (stock != null) p.setStockQuantity(stock);
        if (status != null && !status.isBlank()) p.setStatus(status);

        Businesses business = businessService.findById(businessId);
        if (business == null) throw new IllegalArgumentException("Business not found");
        p.setBusiness(business);

        // image ops
        if (imageRemoved && p.getImageUrl() != null) {
            tryDeleteExisting(p.getImageUrl());
            p.setImageUrl(null);
        }
        if (image != null && !image.isEmpty()) {
            String imageUrl = storeImageIfPresent(image);
            p.setImageUrl(imageUrl);
        }

        // product-specific
        p.setId(sku);

        return productRepository.save(p);
    }

    // -------- file helpers (local uploads/) --------
    private static String storeImageIfPresent(MultipartFile image) throws IOException {
        if (image == null || image.isEmpty()) return null;

        String filename = UUID.randomUUID() + "_" + StringUtils.cleanPath(image.getOriginalFilename());
        Path uploadPath = Paths.get("uploads/");
        if (!Files.exists(uploadPath)) Files.createDirectories(uploadPath);
        Path filePath = uploadPath.resolve(filename);
        Files.copy(image.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        return "/uploads/" + filename;
    }

    private static void tryDeleteExisting(String existingUrl) {
        try {
            String justName = Paths.get(existingUrl).getFileName().toString();
            Path oldImagePath = Paths.get("uploads/", justName);
            Files.deleteIfExists(oldImagePath);
        } catch (Exception ignore) { }
    }
}
