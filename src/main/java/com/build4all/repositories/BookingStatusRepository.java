package com.build4all.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.build4all.entities.*;

public interface BookingStatusRepository extends JpaRepository<BookingStatus, Long> {
    boolean existsByNameIgnoreCase(String name);
}

