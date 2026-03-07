package com.build4all.features.ecommerce.repository;

import com.build4all.features.ecommerce.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // Tenant lists
    List<Product> findByOwnerProject_Id(Long ownerProjectId);

    List<Product> findByOwnerProject_IdAndItemType_Id(Long ownerProjectId, Long itemTypeId);

    List<Product> findByOwnerProject_IdAndItemType_Category_Id(Long ownerProjectId, Long categoryId);

    // Search
    List<Product> findByOwnerProject_IdAndNameContainingIgnoreCase(Long ownerProjectId, String q);

    List<Product> findByOwnerProject_IdAndCreatedAtAfterOrderByCreatedAtDesc(
            Long ownerProjectId,
            LocalDateTime fromDate
    );

    // SKU uniqueness per tenant
    boolean existsByOwnerProject_IdAndSkuIgnoreCase(Long ownerProjectId, String sku);

    boolean existsByOwnerProject_IdAndSkuIgnoreCaseAndIdNot(Long ownerProjectId, String sku, Long id);

    List<Product> findByIdIn(List<Long> ids);

    /**
     * Active discounted products for tenant.
     * Status now comes from ItemStatus table via p.status.code
     */
    @Query("""
           SELECT p
           FROM Product p
           WHERE p.ownerProject.id = :ownerProjectId
             AND p.status IS NOT NULL
             AND UPPER(p.status.code) IN ('PUBLISHED', 'UPCOMING')
             AND p.salePrice IS NOT NULL
             AND p.salePrice > 0
             AND p.price IS NOT NULL
             AND p.salePrice < p.price
             AND (p.saleStart IS NULL OR p.saleStart <= :now)
             AND (p.saleEnd IS NULL OR p.saleEnd >= :now)
           ORDER BY p.id DESC
           """)
    List<Product> findActiveDiscountedByOwnerProject(
            @Param("ownerProjectId") Long ownerProjectId,
            @Param("now") LocalDateTime now
    );

    // Tenant-safe fetch
    @Query("""
           SELECT p
           FROM Product p
           JOIN FETCH p.itemType it
           LEFT JOIN FETCH p.business b
           LEFT JOIN FETCH p.currency c
           LEFT JOIN FETCH p.status s
           WHERE p.id = :itemId
             AND p.ownerProject.id = :aupId
           """)
    Optional<Product> findByIdAndTenant(@Param("itemId") Long itemId,
                                        @Param("aupId") Long aupId);

    @Modifying
    @Query("""
           DELETE FROM Product p
           WHERE p.id = :id
             AND p.ownerProject.id = :aupId
           """)
    int deleteByIdAndTenant(@Param("id") Long id,
                            @Param("aupId") Long aupId);

    @Modifying
    @Query("""
           UPDATE Product p
           SET p.stock = p.stock - :qty
           WHERE p.id = :id
             AND p.stock >= :qty
           """)
    int decrementStockIfEnough(@Param("id") Long id, @Param("qty") int qty);

    @Modifying
    @Query("""
           UPDATE Product p
           SET p.stock = p.stock + :qty
           WHERE p.id = :id
           """)
    int incrementStock(@Param("id") Long id, @Param("qty") int qty);
}