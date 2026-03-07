package com.build4all.catalog.repository;

import com.build4all.catalog.domain.ItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface ItemStatusRepository extends JpaRepository<ItemStatus, Long> {

    Optional<ItemStatus> findByCode(String code);

    List<ItemStatus> findByActiveTrueOrderBySortOrderAsc();
}