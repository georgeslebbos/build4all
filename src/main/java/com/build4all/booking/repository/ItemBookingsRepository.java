package com.build4all.booking.repository;

import com.build4all.booking.domain.ItemBooking;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ItemBookingsRepository extends JpaRepository<ItemBooking, Long> {

    // Prefetch item & booking to avoid lazy problems
    @EntityGraph(attributePaths = {"item", "booking"})
    List<ItemBooking> findByUser_IdOrderByCreatedAtDesc(Long userId);

    // ---- Direct lookups ----
    List<ItemBooking> findByItem_Id(Long itemId);

    boolean existsByItem_IdAndUser_Id(Long itemId, Long userId);
    List<ItemBooking> findByItem_IdAndUser_Id(Long itemId, Long userId);

    long countByItem_Id(Long itemId);

    List<ItemBooking> findByCreatedAtAfter(LocalDateTime after);

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

    // ---- Aggregates (quantity) ----
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

    // Seats already taken for COMPLETED bookings (status normalized)
    @Query("""
           SELECT COALESCE(SUM(ib.quantity), 0)
           FROM ItemBooking ib
           WHERE ib.item.id = :itemId
             AND ib.booking.status IN ('COMPLETED')
           """)
    int sumParticipantsCompletedForItem(@Param("itemId") Long itemId);

    // ---- Deletes ----
    @Modifying
    @Query("DELETE FROM ItemBooking b WHERE b.item.id = :itemId")
    void deleteByItem_Id(@Param("itemId") Long itemId);

    @Modifying
    void deleteByUser_Id(Long userId);

    // ---- “Completed booking” gates ----
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
             AND ib.booking.status = 'COMPLETED'
           """)
    List<Long> findCompletedItemIdsByUser(@Param("userId") Long userId);

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

    @Query("""
           SELECT COALESCE(SUM(ib.price * ib.quantity), 0)
           FROM ItemBooking ib
           WHERE ib.item.business.id = :businessId
           """)
    BigDecimal sumRevenueByBusinessId(@Param("businessId") Long businessId);

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

    @EntityGraph(attributePaths = {"booking", "item"})
    List<ItemBooking> findByUser_Id(Long userId);

    @EntityGraph(attributePaths = {"booking", "item"})
    List<ItemBooking> findTop5ByUser_IdOrderByCreatedAtDesc(Long userId);

    // Secure lookup: business owns the item
    @Query("""
           select ib
           from ItemBooking ib
           join ib.item i
           where ib.id = :id and i.business.id = :businessId
           """)
    Optional<ItemBooking> findByIdAndBusiness(@Param("id") Long id,
                                              @Param("businessId") Long businessId);

    // Secure lookup: user owns the booking header
    @Query("""
           select ib
           from ItemBooking ib
           join ib.booking b
           where ib.id = :id and b.user.id = :userId
           """)
    Optional<ItemBooking> findByIdAndUser(@Param("id") Long id,
                                          @Param("userId") Long userId);

    // Projection for Flutter booking card (keys match your BookingModel)
    @Query("""
           select new map(
               ib.id as id,
               b.status as bookingStatus,
               ib.quantity as numberOfParticipants,
               i.startDatetime as startDatetime,
               i.name as itemName,
               i.location as location,
               i.imageUrl as imageUrl,
               (case when upper(b.status) = 'COMPLETED' then true else false end) as wasPaid
           )
           from ItemBooking ib
           join ib.booking b
           join ib.item i
           where b.user.id = :userId
           order by ib.createdAt desc
           """)
    List<java.util.Map<String, Object>> findUserBookingCards(@Param("userId") Long userId);
    
    @Query("""
    		  select new map(
    		    ib.id as id,
    		    b.status as bookingStatus,
    		    ib.quantity as numberOfParticipants,
    		    i.startDatetime as startDatetime,
    		    i.name as itemName,
    		    i.location as location,
    		    i.imageUrl as imageUrl,
    		    (case when upper(b.status) = 'COMPLETED' then true else false end) as wasPaid
    		  )
    		  from ItemBooking ib
    		  join ib.booking b
    		  join ib.item i
    		  where b.user.id = :userId
    		    and upper(b.status) in :statuses
    		  order by ib.createdAt desc
    		""")
    		List<java.util.Map<String,Object>> findUserBookingCardsByStatuses(
    		  @Param("userId") Long userId,
    		  @Param("statuses") List<String> statuses
    		);


@EntityGraph(attributePaths = {"booking","item","currency","user"})
@Query("""
       select ib
       from ItemBooking ib
       join fetch ib.booking b
       join fetch ib.item i
       left join fetch ib.currency c
       left join fetch ib.user u
       where i.business.id = :businessId
       order by ib.createdAt desc
       """)
List<ItemBooking> findRichByBusinessId(@Param("businessId") Long businessId);
}
