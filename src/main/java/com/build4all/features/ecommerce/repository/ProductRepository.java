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

    // ✅ FIXED: Product has "name" as JPA attribute, not "itemName"
    List<Product> findByOwnerProject_IdAndNameContainingIgnoreCase(Long ownerProjectId, String q);

    List<Product> findByOwnerProject_IdAndCreatedAtAfterOrderByCreatedAtDesc(
            Long ownerProjectId,
            LocalDateTime fromDate
    );

    // SKU uniqueness per tenant
    boolean existsByOwnerProject_IdAndSkuIgnoreCase(Long ownerProjectId, String sku);

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

    // Tenant-safe fetch
    @Query("""
       select p
       from Product p
       join fetch p.itemType it
       left join fetch p.business b
       left join fetch p.currency c
       where p.id = :itemId
         and p.ownerProject.id = :aupId
    """)
    Optional<Product> findByIdAndTenant(@Param("itemId") Long itemId,
                                       @Param("aupId") Long aupId);

    @Modifying
    @Query("""
       delete from Product p
       where p.id = :id
         and p.ownerProject.id = :aupId
    """)
    int deleteByIdAndTenant(@Param("id") Long id,
                            @Param("aupId") Long aupId);
    
    @Modifying
    @Query("""
      update Product p
      set p.stock = p.stock - :qty
      where p.id = :id and p.stock >= :qty
    """)
    int decrementStockIfEnough(@Param("id") Long id, @Param("qty") int qty);

    @Modifying
    @Query("""
      update Product p
      set p.stock = p.stock + :qty
      where p.id = :id
    """)
    int incrementStock(@Param("id") Long id, @Param("qty") int qty);
}
