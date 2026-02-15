package com.build4all.catalog.repository;

import com.build4all.catalog.dto.AdminItemDTO;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import com.build4all.catalog.dto.ItemDetailsDTO;
import com.build4all.ai.dto.AiItemContextDTO;
import com.build4all.catalog.domain.Item;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {

    /* Owner scoping via the link (adminId + projectId) */
    @Query("""
           SELECT i
           FROM Item i
           WHERE i.ownerProject.admin.adminId = :adminId
             AND i.ownerProject.project.id = :projectId
           """)
    List<Item> findAllByOwner(@Param("adminId") Long adminId,
                              @Param("projectId") Long projectId);
    
  
    @Query("""
           SELECT i
           FROM Item i
           WHERE i.ownerProject.admin.adminId = :adminId
             AND i.ownerProject.project.id = :projectId
             AND i.business.id = :businessId
           """)
    List<Item> findByOwnerAndBusiness(@Param("adminId") Long adminId,
                                      @Param("projectId") Long projectId,
                                      @Param("businessId") Long businessId);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select i
        from Item i
        where i.id = :itemId
          and i.ownerProject.id = :aupId
    """)
    Optional<Item> findByTenantForUpdate(@Param("aupId") Long aupId,
                                        @Param("itemId") Long itemId);
    
    

    /* existing */
    @Query("SELECT i FROM Item i WHERE i.business.id = :businessId")
    List<Item> findByBusinessId(@Param("businessId") Long businessId);

    @Modifying
    @Query("DELETE FROM Item i WHERE i.business.id = :businessId")
    void deleteByBusinessId(@Param("businessId") Long businessId);

    @EntityGraph(attributePaths = {"business"})
    @Query("SELECT i FROM Item i WHERE i.id = :id")
    Optional<Item> findByIdWithBusiness(@Param("id") Long id);

    List<Item> findByItemType_Id(Long typeId);

    long countByCreatedAtAfter(LocalDateTime date);

    @Query("""
           SELECT i
           FROM Item i
           WHERE i.business.status.name = 'ACTIVE'
             AND i.business.isPublicProfile = true
           """)
    List<Item> findAllPublicActiveBusinessItems();

    @Query(value = """
            SELECT i.item_name
            FROM order_items b
            JOIN items i ON b.item_id = i.item_id
            WHERE i.business_id = :businessId
            GROUP BY i.item_name
            ORDER BY COUNT(b.id) DESC
            LIMIT 1
            """, nativeQuery = true)
    String findTopItemNameByBusinessId(@Param("businessId") Long businessId);

    @Query("""
           SELECT new com.build4all.catalog.dto.AdminItemDTO(
               i.id,
               i.name,
               b.businessName,
               i.description
           )
           FROM Item i
           JOIN i.business b
           WHERE b.status.name = 'ACTIVE'
             AND b.isPublicProfile = true
           """)
    List<AdminItemDTO> findAllItemsWithBusinessInfo();

    @Query("""
           SELECT i, COUNT(ib.id) AS orderCount
           FROM com.build4all.order.domain.OrderItem ib
           JOIN ib.item i
           WHERE i.business.status.name = 'ACTIVE'
             AND i.business.isPublicProfile = true
           GROUP BY i
           ORDER BY orderCount DESC
           """)
    List<Object[]> findPopularItems();
    
    @Query("""
    	       SELECT i
    	       FROM Item i
    	       WHERE i.ownerProject.id = :aupId
    	       """)
    	List<Item> findByOwnerProjectId(@Param("aupId") Long aupId);
    


 // ItemRepository.java
    @Query("""
    		   select i
    		   from Item i
    		   join fetch i.itemType it
    		   left join fetch i.business b
    		   left join fetch i.currency c
    		   where i.id = :itemId
    		     and i.ownerProject.id = :aupId
    		""")
    		Optional<Item> findByTenantWithJoins(@Param("aupId") Long aupId,
    		                                    @Param("itemId") Long itemId);




    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Item i where i.id = :id")
    Optional<Item> findByIdForStockCheck(@Param("id") Long id);
    


    @Modifying
    @Query("""
        update Item i
        set i.stock = i.stock - :qty
        where i.id = :id and i.stock >= :qty
    """)
    int decrementStockIfEnough(@Param("id") Long id, @Param("qty") int qty);

    // ✅ for restore (cancel/refund)
    @Modifying
    @Query("""
        update Item i
        set i.stock = i.stock + :qty
        where i.id = :id
    """)
    int incrementStock(@Param("id") Long id, @Param("qty") int qty);



}
