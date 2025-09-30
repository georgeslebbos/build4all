package com.build4all.repositories.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.build4all.entities.product.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    // add specific queries later if needed (by SKU, low stock, etc.)
}