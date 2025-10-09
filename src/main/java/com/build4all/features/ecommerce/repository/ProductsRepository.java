package com.build4all.features.ecommerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.build4all.features.ecommerce.domain.Product;

import java.util.List;

@Repository
public interface ProductsRepository extends JpaRepository<Product, Long> {

    // Find products by business
    List<Product> findByBusinessId(Long businessId);

    // Find by name containing
    List<Product> findByNameContainingIgnoreCase(String name);

    // Find available stock products
    List<Product> findByStockGreaterThan(Integer stockQuantity);
}
