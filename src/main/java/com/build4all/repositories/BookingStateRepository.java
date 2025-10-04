package com.build4all.repositories;

import com.build4all.entities.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookingStateRepository extends JpaRepository<BookingStatus, Long> {
    Optional<BookingStatus> findByNameIgnoreCase(String name);
}
