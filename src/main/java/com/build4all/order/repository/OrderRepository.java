package com.build4all.order.repository;

import com.build4all.order.domain.Order;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * OrderRepository
 *
 * Repository for the Order *header* entity (table: orders).
 *
 * Reminder:
 * - Order is the header (user, totals, status, shipping, payment method...)
 * - OrderItem is the line (item, qty, unit price...)
 *
 * This repository supports:
 * - USER history (my orders)
 * - Date/time reporting
 * - BUSINESS reporting (orders that include items owned by a business)
 * - OWNER reporting (orders that include items owned by an application/tenant = ownerProject)
 * - SUPER_ADMIN reporting (orders grouped by application/tenant)
 *
 * Notes:
 * - Some queries use @EntityGraph / JOIN FETCH to avoid LazyInitialization errors
 *   and reduce N+1 queries.
 * - "Application scope" is derived from OrderItem -> Item -> ownerProject.id
 *   because Order header does not store ownerProjectId directly.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /* =========================================================================================
       USER (End-user) - header-level queries
       ========================================================================================= */

    /**
     * Fetch all orders for a user (header only).
     * Use when you don't need items.
     */
    List<Order> findByUser_Id(Long userId);

    /**
     * Fetch orders within a date range (useful for admin reporting).
     * Only checks orderDate column on the order header.
     */
    List<Order> findByOrderDateBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Fetch orders for a user after a specific date/time (recent orders, analytics).
     */
    List<Order> findByUser_IdAndOrderDateAfter(Long userId, LocalDateTime after);

    /**
     * Security/ownership helper: does this order id belong to this user?
     * Useful before exposing order details.
     */
    boolean existsByIdAndUser_Id(Long id, Long userId);

    /**
     * Delete all orders for a user (dangerous, use carefully).
     * Typically only for test resets or admin cleanup.
     */
    void deleteByUser_Id(Long userId);

    /**
     * Fetch orders for a user filtered by status codes.
     *
     * status is a FK to OrderStatus, but you filter by status.name.
     * Example statuses: ["PENDING","COMPLETED"].
     */
    List<Order> findByUser_IdAndStatus_NameIn(Long userId, List<String> statuses);

    /**
     * Sum of totalPrice for all orders of a user.
     * Returns Double because JPQL SUM on BigDecimal sometimes mapped that way here.
     */
    @Query("SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o WHERE o.user.id = :userId")
    Double sumTotalPriceByUser(@Param("userId") Long userId);

    /**
     * "My Orders" (header + items + item details) with EntityGraph:
     * - Loads orderItems and their item in one go (reduces N+1).
     * - Ordered by most recent first.
     */
    @EntityGraph(attributePaths = {"orderItems", "orderItems.item"})
    List<Order> findByUser_IdOrderByOrderDateDesc(Long userId);

    /**
     * Load a single order with its items and item entities.
     * LEFT JOIN FETCH ensures orderItems and item are loaded even if some are null.
     */
    @Query("""
           SELECT o
           FROM Order o
           LEFT JOIN FETCH o.orderItems oi
           LEFT JOIN FETCH oi.item i
           WHERE o.id = :id
           """)
    Optional<Order> findByIdWithItems(@Param("id") Long id);

    /**
     * Quick recent list (top 5) for a user.
     */
    List<Order> findTop5ByUser_IdOrderByOrderDateDesc(Long userId);

    /**
     * Feed-style orders after a date, with items + item loaded.
     * Useful for "recent orders" dashboards.
     */
    @EntityGraph(attributePaths = {"orderItems", "orderItems.item"})
    List<Order> findByOrderDateAfterOrderByOrderDateDesc(LocalDateTime after);

    /* =========================================================================================
       BUSINESS (Item owner) - header-level queries via items.business.id
       ========================================================================================= */

    /**
     * Business dashboard: count distinct orders that include at least one item
     * owned by the given businessId.
     *
     * Why DISTINCT:
     * - One order can have multiple orderItems for the same business; we count the order once.
     */
    @Query("""
           SELECT COUNT(DISTINCT o.id)
           FROM Order o
           JOIN o.orderItems oi
           JOIN oi.item i
           WHERE i.business.id = :businessId
           """)
    BigDecimal countByBusinessId(@Param("businessId") Long businessId);

    /**
     * Fetch all orders that contain items belonging to a business.
     * DISTINCT prevents duplicates when an order contains multiple items from same business.
     */
    @Query("""
           SELECT DISTINCT o
           FROM Order o
           JOIN o.orderItems oi
           JOIN oi.item i
           WHERE i.business.id = :businessId
           """)
    List<Order> findAllByBusinessId(@Param("businessId") Long businessId);

    /* =========================================================================================
       PUBLIC marketplace / discovery use case (existing)
       ========================================================================================= */

    /**
     * Returns orders associated with businesses that are:
     * - ACTIVE
     * - Public profile enabled
     *
     * NOTE:
     * - This is unusual logically (orders are private).
     * - Be careful not to expose sensitive user data.
     */
    @Query("""
           SELECT DISTINCT o
           FROM Order o
           JOIN o.orderItems oi
           JOIN oi.item i
           JOIN i.business biz
           WHERE biz.status.name = 'ACTIVE'
             AND biz.isPublicProfile = true
           """)
    List<Order> findAllForActivePublicBusinesses();

    /* =========================================================================================
       OWNER (Application admin) - header-level queries via items.ownerProject.id  ✅ NEW
       ========================================================================================= */

    /**
     * OWNER: List all orders for one application (tenant) with items loaded.
     *
     * We scope by Item.ownerProject.id because the Order header does not store ownerProjectId.
     * DISTINCT: avoids duplicating headers due to multiple lines.
     */
    @EntityGraph(attributePaths = {"orderItems", "orderItems.item"})
    @Query("""
           select distinct o
           from Order o
           join o.orderItems oi
           join oi.item i
           where i.ownerProject.id = :ownerProjectId
           order by o.orderDate desc
           """)
    List<Order> findAllByOwnerProjectIdWithItems(@Param("ownerProjectId") Long ownerProjectId);

    /**
     * OWNER: List all orders for one application filtered by status list.
     *
     * Example statuses: ["PENDING","COMPLETED","CANCEL_REQUESTED"].
     */
    @EntityGraph(attributePaths = {"orderItems", "orderItems.item"})
    @Query("""
           select distinct o
           from Order o
           join o.orderItems oi
           join oi.item i
           where i.ownerProject.id = :ownerProjectId
             and upper(o.status.name) in :statuses
           order by o.orderDate desc
           """)
    List<Order> findAllByOwnerProjectIdWithItemsAndStatuses(@Param("ownerProjectId") Long ownerProjectId,
                                                            @Param("statuses") List<String> statuses);

    /* =========================================================================================
       SUPER_ADMIN (Engine) - aggregation by application (ownerProjectId) ✅ NEW
       ========================================================================================= */

    /**
     * SUPER_ADMIN:
     * Count orders grouped by application (ownerProjectId).
     *
     * Returns rows: Object[] where:
     * - row[0] = ownerProjectId (Long)
     * - row[1] = ordersCount (Long)
     */
    @Query("""
           select i.ownerProject.id, count(distinct o.id)
           from Order o
           join o.orderItems oi
           join oi.item i
           group by i.ownerProject.id
           order by count(distinct o.id) desc
           """)
    List<Object[]> countOrdersGroupedByOwnerProject();
}
