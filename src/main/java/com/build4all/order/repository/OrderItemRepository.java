package com.build4all.order.repository;

import com.build4all.order.domain.OrderItem;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    // Prefetch item & order to avoid lazy problems
    @EntityGraph(attributePaths = {"item", "order"})
    List<OrderItem> findByUser_IdOrderByCreatedAtDesc(Long userId);

    // ---- Direct lookups ----
    List<OrderItem> findByItem_Id(Long itemId);

    boolean existsByItem_IdAndUser_Id(Long itemId, Long userId);
    List<OrderItem> findByItem_IdAndUser_Id(Long itemId, Long userId);

    long countByItem_Id(Long itemId);

    List<OrderItem> findByCreatedAtAfter(LocalDateTime after);

    // ---- Business-scoped via Item ----
    @Query("""
           SELECT oi
           FROM OrderItem oi
           JOIN oi.item i
           WHERE i.business.id = :businessId
           """)
    List<OrderItem> findAllByBusinessId(@Param("businessId") Long businessId);

    @Query("""
           SELECT COUNT(oi)
           FROM OrderItem oi
           JOIN oi.item i
           WHERE i.business.id = :businessId
           """)
    long countOrdersByBusinessId(@Param("businessId") Long businessId);

    // ---- Aggregates (quantity) ----
    @Query("""
           SELECT COALESCE(SUM(oi.quantity), 0)
           FROM OrderItem oi
           WHERE oi.item.id = :itemId
           """)
    int sumQuantityByItemId(@Param("itemId") Long itemId);

    @Query("""
           SELECT COALESCE(SUM(oi.quantity), 0)
           FROM OrderItem oi
           WHERE oi.item.id = :itemId
             AND UPPER(oi.order.status.name) IN :statusNames
           """)
    int sumQuantityByItemIdAndStatusNames(@Param("itemId") Long itemId,
                                          @Param("statusNames") List<String> statusNames);

    @Query("""
           SELECT COALESCE(SUM(oi.quantity), 0)
           FROM OrderItem oi
           WHERE oi.item.id = :itemId
             AND UPPER(oi.order.status.name) = 'COMPLETED'
           """)
    int sumParticipantsCompletedForItem(@Param("itemId") Long itemId);

    // ---- Deletes ----
    @Modifying
    @Query("DELETE FROM OrderItem oi WHERE oi.item.id = :itemId")
    void deleteByItem_Id(@Param("itemId") Long itemId);

    @Modifying
    void deleteByUser_Id(Long userId);

    // ---- “Completed order” gates ----
    @Query("""
           SELECT CASE WHEN COUNT(oi) > 0 THEN true ELSE false END
           FROM OrderItem oi
           WHERE oi.item.id = :itemId
             AND oi.user.id = :userId
             AND UPPER(oi.order.status.name) = UPPER(:statusName)
           """)
    boolean existsByItemIdAndUserIdAndOrderStatusName(@Param("itemId") Long itemId,
                                                      @Param("userId") Long userId,
                                                      @Param("statusName") String statusName);

    @Query("""
           SELECT DISTINCT oi.item.id
           FROM OrderItem oi
           WHERE oi.user.id = :userId
             AND UPPER(oi.order.status.name) = 'COMPLETED'
           """)
    List<Long> findCompletedItemIdsByUser(@Param("userId") Long userId);

    @Query("""
           SELECT COUNT(oi)
           FROM OrderItem oi
           WHERE oi.item.business.id = :businessId
             AND EXTRACT(YEAR FROM oi.order.orderDate) = :year
             AND EXTRACT(MONTH FROM oi.order.orderDate) = :month
           """)
    long countOrdersByMonthAndYear(@Param("businessId") Long businessId,
                                   @Param("month") int month,
                                   @Param("year") int year);

    @Query("""
           SELECT CAST(EXTRACT(MONTH FROM oi.order.orderDate) AS int) AS m,
                  COUNT(oi) AS c
           FROM OrderItem oi
           WHERE oi.item.business.id = :businessId
             AND EXTRACT(YEAR FROM oi.order.orderDate) = :year
           GROUP BY EXTRACT(MONTH FROM oi.order.orderDate)
           ORDER BY m
           """)
    List<Object[]> countOrdersByMonthForYear(@Param("businessId") Long businessId,
                                             @Param("year") int year);

    @Query("""
           SELECT CAST(EXTRACT(HOUR FROM o.orderDate) AS int) AS hour,
                  COUNT(o) AS c
           FROM Order o
           JOIN o.orderItems oi
           WHERE oi.item.business.id = :businessId
           GROUP BY EXTRACT(HOUR FROM o.orderDate)
           ORDER BY c DESC
           """)
    List<Object[]> findPeakOrderHours(@Param("businessId") Long businessId);

    @Query("""
           SELECT COALESCE(SUM(oi.price * oi.quantity), 0)
           FROM OrderItem oi
           WHERE oi.item.business.id = :businessId
           """)
    BigDecimal sumRevenueByBusinessId(@Param("businessId") Long businessId);

    @Query("""
           SELECT COUNT(DISTINCT oi.user.id)
           FROM OrderItem oi
           WHERE oi.item.business.id = :businessId
           """)
    long countDistinctCustomers(@Param("businessId") Long businessId);

    @Query(value = """
           SELECT COUNT(*) FROM (
             SELECT oi.user_id
             FROM order_items oi
             JOIN orders o   ON oi.order_id = o.order_id
             JOIN items i    ON oi.item_id = i.item_id
             WHERE i.business_id = :businessId
             GROUP BY oi.user_id
             HAVING COUNT(DISTINCT oi.order_id) >= 2
           ) t
           """, nativeQuery = true)
    long countReturningCustomers(@Param("businessId") Long businessId);

    @Query("""
           SELECT COUNT(oi)
           FROM OrderItem oi
           WHERE oi.order.orderDate > :fromDate
           """)
    long countByOrderDatetimeAfter(@Param("fromDate") LocalDateTime fromDate);

    @EntityGraph(attributePaths = {"order", "item"})
    List<OrderItem> findByUser_Id(Long userId);

    @EntityGraph(attributePaths = {"order", "item"})
    List<OrderItem> findTop5ByUser_IdOrderByCreatedAtDesc(Long userId);

    // Secure lookup: business owns the item
    @Query("""
           select oi
           from OrderItem oi
           join oi.item i
           where oi.id = :id and i.business.id = :businessId
           """)
    Optional<OrderItem> findByIdAndBusiness(@Param("id") Long id,
                                            @Param("businessId") Long businessId);

    // Secure lookup: user owns the order header
    @Query("""
           select oi
           from OrderItem oi
           join oi.order o
           where oi.id = :id and o.user.id = :userId
           """)
    Optional<OrderItem> findByIdAndUser(@Param("id") Long id,
                                        @Param("userId") Long userId);

    // Projection for Flutter order card
    @Query("""
           select new map(
               oi.id as id,
               o.status.name as orderStatus,
               oi.quantity as quantity,
               i.name as itemName,
               i.imageUrl as imageUrl,
               (case when upper(o.status.name) = 'COMPLETED' then true else false end) as wasPaid
           )
           from OrderItem oi
           join oi.order o
           join oi.item i
           where o.user.id = :userId
           order by oi.createdAt desc
           """)
    List<java.util.Map<String, Object>> findUserOrderCards(@Param("userId") Long userId);

    @Query("""
           select new map(
             oi.id as id,
             o.status.name as orderStatus,
             oi.quantity as quantity,
             i.name as itemName,
             i.imageUrl as imageUrl,
             (case when upper(o.status.name) = 'COMPLETED' then true else false end) as wasPaid
           )
           from OrderItem oi
           join oi.order o
           join oi.item i
           where o.user.id = :userId
             and upper(o.status.name) in :statuses
           order by oi.createdAt desc
           """)
    List<java.util.Map<String,Object>> findUserOrderCardsByStatuses(
            @Param("userId") Long userId,
            @Param("statuses") List<String> statuses
    );

    @EntityGraph(attributePaths = {"order","item","currency","user"})
    @Query("""
           select oi
           from OrderItem oi
           join fetch oi.order o
           join fetch oi.item i
           left join fetch oi.currency c
           left join fetch oi.user u
           where i.business.id = :businessId
           order by oi.createdAt desc
           """)
    List<OrderItem> findRichByBusinessId(@Param("businessId") Long businessId);

    /**
     * Best-selling items for one app (ownerProjectId).
     * Returns rows: [ itemId (Long), totalQty (Long) ] ordered by totalQty desc.
     */
    @Query("""
           SELECT oi.item.id AS itemId, SUM(oi.quantity) AS totalQty
           FROM OrderItem oi
           JOIN oi.item i
           WHERE i.ownerProject.id = :ownerProjectId
           GROUP BY oi.item.id
           ORDER BY totalQty DESC
           """)
    List<Object[]> findBestSellingItemsByOwnerProject(@Param("ownerProjectId") Long ownerProjectId);
}
