package com.build4all.features.ecommerce.repository;

import com.build4all.features.ecommerce.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // All products in a given owner project (aup_id)
    List<Product> findByOwnerProject_Id(Long ownerProjectId);

    // By ItemType
    List<Product> findByItemType_Id(Long itemTypeId);

    // By Category (through ItemType)
    List<Product> findByItemType_Category_Id(Long categoryId);

    // 🔁 FIX HERE: use "name", not "itemName"
    List<Product> findByNameContainingIgnoreCase(String name);

    // New arrivals for one app (ownerProject), ordered newest first
    List<Product> findByOwnerProject_IdAndCreatedAtAfterOrderByCreatedAtDesc(
            Long ownerProjectId,
            LocalDateTime fromDate
    );
}
