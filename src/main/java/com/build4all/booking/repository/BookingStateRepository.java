package com.build4all.booking.repository;

import com.build4all.booking.domain.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookingStateRepository extends JpaRepository<BookingStatus, Long> {
    Optional<BookingStatus> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
}
