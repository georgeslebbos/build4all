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

/**
 * OrderItemRepository
 *
 * Repository for the OrderItem *line* entity (table: order_items).
 *
 * Reminder:
 * - Order is the header (user, totals, status, shipping, payment method...)
 * - OrderItem is the line (item, qty, unit price...)
 *
 * This repository supports:
 * - USER "my orders" list (cards)
 * - BUSINESS dashboards (items owned by a business)
 * - OWNER dashboards (application/tenant scope via Item.ownerProject.id)
 * - SUPER_ADMIN reporting (cross-application)
 *
 * Performance notes:
 * - Many methods use @EntityGraph or JOIN FETCH to avoid lazy-loading problems.
 * - Projections (new map(...)) are used for lightweight Flutter card responses.
 */
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /* =========================================================================================
       USER - history / direct lookups
       ========================================================================================= */

    /**
     * Prefetch item & order to avoid lazy problems.
     * Useful for user "order history" pages.
     */
    @EntityGraph(attributePaths = {"item", "order"})
    List<OrderItem> findByUser_IdOrderByCreatedAtDesc(Long userId);

    // ---- Direct lookups ----
    List<OrderItem> findByItem_Id(Long itemId);

    boolean existsByItem_IdAndUser_Id(Long itemId, Long userId);

    // ✅ NEW (needed for delete guard in ProductService)
    boolean existsByItem_Id(Long itemId);

    List<OrderItem> findByItem_IdAndUser_Id(Long itemId, Long userId);

    long countByItem_Id(Long itemId);

    List<OrderItem> findByCreatedAtAfter(LocalDateTime after);

    /* =========================================================================================
       BUSINESS-SCOPED via Item.business.id
       ========================================================================================= */

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

    /* =========================================================================================
       AGGREGATES (quantity) / capacity / booking counts
       ========================================================================================= */

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

    /* =========================================================================================
       DELETES (maintenance)
       ========================================================================================= */

    @Modifying
    @Query("DELETE FROM OrderItem oi WHERE oi.item.id = :itemId")
    void deleteByItem_Id(@Param("itemId") Long itemId);

    @Modifying
    void deleteByUser_Id(Long userId);

    /* =========================================================================================
       "COMPLETED order" gates (access control for content / prevent double purchase)
       ========================================================================================= */

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

    /* =========================================================================================
       BUSINESS analytics (existing)
       ========================================================================================= */

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

    /* =========================================================================================
       EntityGraph helpers (existing)
       ========================================================================================= */

    @EntityGraph(attributePaths = {"order", "item"})
    List<OrderItem> findByUser_Id(Long userId);

    @EntityGraph(attributePaths = {"order", "item"})
    List<OrderItem> findTop5ByUser_IdOrderByCreatedAtDesc(Long userId);

    /* =========================================================================================
       Secure lookups (existing)
       ========================================================================================= */

    /**
     * Secure lookup: business owns the item.
     */
    @Query("""
           select oi
           from OrderItem oi
           join oi.item i
           where oi.id = :id and i.business.id = :businessId
           """)
    Optional<OrderItem> findByIdAndBusiness(@Param("id") Long id,
                                            @Param("businessId") Long businessId);

    /**
     * Secure lookup: user owns the order header.
     */
    @Query("""
           select oi
           from OrderItem oi
           join oi.order o
           where oi.id = :id and o.user.id = :userId
           """)
    Optional<OrderItem> findByIdAndUser(@Param("id") Long id,
                                        @Param("userId") Long userId);

    /* =========================================================================================
       Flutter card projections (existing)
       ========================================================================================= */

    /**
     * Projection for Flutter "order card" list.
     * Keep it lightweight: returns Map rows.
     */
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

    /* =========================================================================================
       Rich business view (existing) - loads order+item+currency+user for dashboard rows
       ========================================================================================= */

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

    /* =========================================================================================
       OwnerProject (application/tenant) reports (existing + FIXED)
       ========================================================================================= */

    /**
     * Best-selling items for one app (ownerProjectId).
     * Returns rows: [ itemId (Long), totalQty (Long) ] ordered by totalQty desc.
     *
     * ✅ FIXED:
     * - counts ONLY completed orders (real sales)
     * - counts ONLY published products
     * - counts ONLY Product subtype (no other item types)
     */
    @Query("""
           SELECT oi.item.id AS itemId, SUM(oi.quantity) AS totalQty
           FROM OrderItem oi
           JOIN oi.order o
           JOIN oi.item i
           WHERE i.ownerProject.id = :ownerProjectId
             AND UPPER(o.status.name) = 'COMPLETED'
             AND LOWER(i.status) = 'published'
             AND TYPE(i) = com.build4all.features.ecommerce.domain.Product
           GROUP BY oi.item.id
           ORDER BY totalQty DESC
           """)
    List<Object[]> findBestSellingItemsByOwnerProject(@Param("ownerProjectId") Long ownerProjectId);

    /**
     * OWNER:
     * List all order items in one application (tenant/app) with rich graph.
     * Useful for OWNER dashboard screens without businessId (application-level).
     */
    @EntityGraph(attributePaths = {"order","item","currency","user"})
    @Query("""
           select oi
           from OrderItem oi
           join oi.item i
           where i.ownerProject.id = :ownerProjectId
           order by oi.createdAt desc
           """)
    List<OrderItem> findRichByOwnerProjectId(@Param("ownerProjectId") Long ownerProjectId);

    /**
     * OWNER:
     * List order items in one application filtered by order header statuses.
     * Example statuses: ["PENDING","COMPLETED","CANCEL_REQUESTED"].
     */
    @EntityGraph(attributePaths = {"order","item","currency","user"})
    @Query("""
           select oi
           from OrderItem oi
           join oi.order o
           join oi.item i
           where i.ownerProject.id = :ownerProjectId
             and upper(o.status.name) in :statuses
           order by oi.createdAt desc
           """)
    List<OrderItem> findRichByOwnerProjectIdAndStatuses(@Param("ownerProjectId") Long ownerProjectId,
                                                        @Param("statuses") List<String> statuses);

    /**
     * SUPER_ADMIN:
     * List order items for a specific application (ownerProjectId).
     */
    @EntityGraph(attributePaths = {"order","item","currency","user"})
    @Query("""
           select oi
           from OrderItem oi
           join oi.item i
           where i.ownerProject.id = :ownerProjectId
           order by oi.createdAt desc
           """)
    List<OrderItem> findRichByApplication(@Param("ownerProjectId") Long ownerProjectId);

    /**
     * SUPER_ADMIN:
     * Aggregate: count distinct orders grouped by application (ownerProjectId).
     */
    @Query("""
           select i.ownerProject.id, count(distinct o.id)
           from OrderItem oi
           join oi.order o
           join oi.item i
           group by i.ownerProject.id
           order by count(distinct o.id) desc
           """)
    List<Object[]> countOrdersGroupedByOwnerProject();

    /**
     * OWNER tenant isolation helper:
     * Checks whether this order header belongs to this application (ownerProjectId),
     * based on at least one line item in the order.
     */
    boolean existsByOrder_IdAndItem_OwnerProject_Id(Long orderId, Long ownerProjectId);
}