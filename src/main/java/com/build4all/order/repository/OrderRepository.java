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

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUser_Id(Long userId);

    List<Order> findByOrderDateBetween(LocalDateTime start, LocalDateTime end);

    List<Order> findByUser_IdAndOrderDateAfter(Long userId, LocalDateTime after);

    boolean existsByIdAndUser_Id(Long id, Long userId);

    void deleteByUser_Id(Long userId);

    // now based on status.name (string codes)
    List<Order> findByUser_IdAndStatus_NameIn(Long userId, List<String> statuses);

    @Query("SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o WHERE o.user.id = :userId")
    Double sumTotalPriceByUser(@Param("userId") Long userId);

    @Query("""
           SELECT COUNT(DISTINCT o.id)
           FROM Order o
           JOIN o.orderItems oi
           JOIN oi.item i
           WHERE i.business.id = :businessId
           """)
    BigDecimal countByBusinessId(@Param("businessId") Long businessId);

    @Query("""
           SELECT DISTINCT o
           FROM Order o
           JOIN o.orderItems oi
           JOIN oi.item i
           WHERE i.business.id = :businessId
           """)
    List<Order> findAllByBusinessId(@Param("businessId") Long businessId);

    @EntityGraph(attributePaths = {"orderItems", "orderItems.item"})
    List<Order> findByUser_IdOrderByOrderDateDesc(Long userId);

    @Query("""
           SELECT o
           FROM Order o
           LEFT JOIN FETCH o.orderItems oi
           LEFT JOIN FETCH oi.item i
           WHERE o.id = :id
           """)
    Optional<Order> findByIdWithItems(@Param("id") Long id);

    List<Order> findTop5ByUser_IdOrderByOrderDateDesc(Long userId);

    @EntityGraph(attributePaths = {"orderItems", "orderItems.item"})
    List<Order> findByOrderDateAfterOrderByOrderDateDesc(LocalDateTime after);

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
}
