package com.build4all.catalog.repository;

import com.build4all.catalog.domain.ItemImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ItemImageRepository extends JpaRepository<ItemImage, Long> {

    List<ItemImage> findByItem_IdOrderBySortOrderAscIdAsc(Long itemId);

    Optional<ItemImage> findByItem_IdAndMainImageTrue(Long itemId);

    Optional<ItemImage> findByIdAndItem_Id(Long id, Long itemId);

    long countByItem_Id(Long itemId);

    void deleteByItem_Id(Long itemId);
}