package com.build4all.booking.repository;

import com.build4all.booking.domain.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookingStatusRepository extends JpaRepository<BookingStatus, Long> {
    boolean existsByNameIgnoreCase(String name);
    Optional<BookingStatus> findByNameIgnoreCase(String name);
}

