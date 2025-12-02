package com.build4all.features.ecommerce.repository;

import com.build4all.features.ecommerce.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // ⭐ for best sellers
    List<Product> findByIdIn(List<Long> ids);

    @Query("""
           SELECT p
           FROM Product p
           WHERE p.ownerProject.id = :ownerProjectId
             AND p.salePrice IS NOT NULL
             AND p.salePrice > 0
             AND p.price IS NOT NULL
             AND p.salePrice < p.price
             AND (p.saleStart IS NULL OR p.saleStart <= CURRENT_TIMESTAMP)
             AND (p.saleEnd IS NULL OR p.saleEnd >= CURRENT_TIMESTAMP)
           ORDER BY p.createdAt DESC
           """)
    List<Product> findActiveDiscountedByOwnerProject(@Param("ownerProjectId") Long ownerProjectId);
}
