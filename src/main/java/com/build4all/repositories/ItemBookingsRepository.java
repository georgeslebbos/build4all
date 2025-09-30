package com.build4all.repositories;

import com.build4all.entities.ItemBooking;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ItemBookingsRepository extends JpaRepository<ItemBooking, Long> {

    // ---- Direct lookups ----
    List<ItemBooking> findByItem_Id(Long itemId);
    List<ItemBooking> findByUser_Id(Long userId);

    boolean existsByItem_IdAndUser_Id(Long itemId, Long userId);
    List<ItemBooking> findByItem_IdAndUser_Id(Long itemId, Long userId);

    long countByItem_Id(Long itemId);

    List<ItemBooking> findByCreatedAtAfter(LocalDateTime after);

    List<ItemBooking> findTop5ByUser_IdOrderByCreatedAtDesc(Long userId);

    // ---- Business-scoped via Item ----
    @Query("""
           SELECT b
           FROM ItemBooking b
           JOIN b.item i
           WHERE i.business.id = :businessId
           """)
    List<ItemBooking> findAllByBusinessId(@Param("businessId") Long businessId);

    @Query("""
           SELECT COUNT(b)
           FROM ItemBooking b
           JOIN b.item i
           WHERE i.business.id = :businessId
           """)
    long countBookingsByBusinessId(@Param("businessId") Long businessId);

    // ---- Aggregates (participants/quantity) ----
    @Query("""
           SELECT COALESCE(SUM(b.quantity), 0)
           FROM ItemBooking b
           WHERE b.item.id = :itemId
           """)
    int sumQuantityByItemId(@Param("itemId") Long itemId);

    @Query("""
           SELECT COALESCE(SUM(b.quantity), 0)
           FROM ItemBooking b
           WHERE b.item.id = :itemId
             AND b.booking.status IN :statuses
           """)
    int sumQuantityByItemIdAndBookingStatuses(@Param("itemId") Long itemId,
                                              @Param("statuses") List<String> statuses);

    // ---- Deletes ----
    @Modifying
    @Query("DELETE FROM ItemBooking b WHERE b.item.id = :itemId")
    void deleteByItem_Id(@Param("itemId") Long itemId);

    @Modifying
    void deleteByUser_Id(Long userId);

    @Modifying
    @Query("DELETE FROM ItemBooking b WHERE b.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    // ---- “Completed booking” gates for reviews ----
    @Query("""
           SELECT CASE WHEN COUNT(ib) > 0 THEN true ELSE false END
           FROM ItemBooking ib
           WHERE ib.item.id = :itemId
             AND ib.user.id = :userId
             AND ib.booking.status = :status
           """)
    boolean existsByItemIdAndUserIdAndBookingStatus(@Param("itemId") Long itemId,
                                                    @Param("userId") Long userId,
                                                    @Param("status") String status);

    @Query("""
           SELECT DISTINCT ib.item.id
           FROM ItemBooking ib
           WHERE ib.user.id = :userId
             AND ib.booking.status = 'Completed'
           """)
    List<Long> findCompletedItemIdsByUser(@Param("userId") Long userId);

    // ---- Time windows (use Booking header date) ----
    @Query("""
           SELECT COUNT(ib)
           FROM ItemBooking ib
           WHERE ib.booking.bookingDate > :fromDate
           """)
    long countByBookingDateAfter(@Param("fromDate") LocalDateTime fromDate);

    // ---- Monthly counts (PostgreSQL-safe via EXTRACT) ----
    @Query("""
           SELECT COUNT(ib)
           FROM ItemBooking ib
           WHERE ib.item.business.id = :businessId
             AND EXTRACT(YEAR FROM ib.booking.bookingDate) = :year
             AND EXTRACT(MONTH FROM ib.booking.bookingDate) = :month
           """)
    long countBookingsByMonthAndYear(@Param("businessId") Long businessId,
                                     @Param("month") int month,
                                     @Param("year") int year);

    @Query("""
           SELECT CAST(EXTRACT(MONTH FROM ib.booking.bookingDate) AS int) AS m,
                  COUNT(ib) AS c
           FROM ItemBooking ib
           WHERE ib.item.business.id = :businessId
             AND EXTRACT(YEAR FROM ib.booking.bookingDate) = :year
           GROUP BY EXTRACT(MONTH FROM ib.booking.bookingDate)
           ORDER BY m
           """)
    List<Object[]> countBookingsByMonthForYear(@Param("businessId") Long businessId,
                                               @Param("year") int year);

    // ---- Peak booking hours (by Booking header) ----
    @Query("""
           SELECT CAST(EXTRACT(HOUR FROM b.bookingDate) AS int) AS hour,
                  COUNT(b) AS c
           FROM Booking b
           JOIN b.itemBookings ib
           WHERE ib.item.business.id = :businessId
           GROUP BY EXTRACT(HOUR FROM b.bookingDate)
           ORDER BY c DESC
           """)
    List<Object[]> findPeakBookingHours(@Param("businessId") Long businessId);

    // ---- Revenue (sum of line prices * qty) ----
    @Query("""
           SELECT COALESCE(SUM(ib.price * ib.quantity), 0)
           FROM ItemBooking ib
           WHERE ib.item.business.id = :businessId
           """)
    BigDecimal sumRevenueByBusinessId(@Param("businessId") Long businessId);

    // ---- Customers (distinct & returning) ----
    @Query("""
           SELECT COUNT(DISTINCT ib.user.id)
           FROM ItemBooking ib
           WHERE ib.item.business.id = :businessId
           """)
    long countDistinctCustomers(@Param("businessId") Long businessId);

    @Query(value = """
           SELECT COUNT(*) FROM (
             SELECT ib.user_id
             FROM item_bookings ib
             JOIN bookings b   ON ib.booking_id = b.booking_id
             JOIN items i      ON ib.item_id = i.item_id
             WHERE i.business_id = :businessId
             GROUP BY ib.user_id
             HAVING COUNT(DISTINCT ib.booking_id) >= 2
           ) t
           """, nativeQuery = true)
    long countReturningCustomers(@Param("businessId") Long businessId);

    @Query("""
           SELECT COUNT(ib)
           FROM ItemBooking ib
           WHERE ib.booking.bookingDate > :fromDate
           """)
    long countByBookingDatetimeAfter(@Param("fromDate") LocalDateTime fromDate);
}
