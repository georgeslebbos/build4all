package com.build4all.booking.repository;

import com.build4all.booking.domain.Booking;
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
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUser_Id(Long userId);

    List<Booking> findByBookingDateBetween(LocalDateTime start, LocalDateTime end);

    List<Booking> findByUser_IdAndBookingDateAfter(Long userId, LocalDateTime after);

    boolean existsByIdAndUser_Id(Long id, Long userId);

    void deleteByUser_Id(Long userId);

    List<Booking> findByUser_IdAndStatusIn(Long userId, List<String> statuses);

    @Query("SELECT COALESCE(SUM(b.totalPrice), 0) FROM Booking b WHERE b.user.id = :userId")
    Double sumTotalPriceByUser(@Param("userId") Long userId);

    @Query("""
           SELECT COUNT(DISTINCT b.id)
           FROM Booking b
           JOIN b.itemBookings ib
           JOIN ib.item i
           WHERE i.business.id = :businessId
           """)
    BigDecimal countByBusinessId(@Param("businessId") Long businessId);

    @Query("""
           SELECT DISTINCT b
           FROM Booking b
           JOIN b.itemBookings ib
           JOIN ib.item i
           WHERE i.business.id = :businessId
           """)
    List<Booking> findAllByBusinessId(@Param("businessId") Long businessId);

    @EntityGraph(attributePaths = {"itemBookings", "itemBookings.item"})
    List<Booking> findByUser_IdOrderByBookingDateDesc(Long userId);

    @Query("""
           SELECT b
           FROM Booking b
           LEFT JOIN FETCH b.itemBookings ib
           LEFT JOIN FETCH ib.item i
           WHERE b.id = :id
           """)
    Optional<Booking> findByIdWithItems(@Param("id") Long id);

    List<Booking> findTop5ByUser_IdOrderByBookingDateDesc(Long userId);

    @EntityGraph(attributePaths = {"itemBookings", "itemBookings.item"})
    List<Booking> findByBookingDateAfterOrderByBookingDateDesc(LocalDateTime after);

    @Query("""
           SELECT DISTINCT b
           FROM Booking b
           JOIN b.itemBookings ib
           JOIN ib.item i
           JOIN i.business biz
           WHERE biz.status.name = 'ACTIVE'
             AND biz.isPublicProfile = true
           """)
    List<Booking> findAllForActivePublicBusinesses();
}