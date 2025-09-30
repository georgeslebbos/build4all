package com.build4all.repositories;

import com.build4all.entities.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    // add specific queries later if needed (by SKU, low stock, etc.)
}