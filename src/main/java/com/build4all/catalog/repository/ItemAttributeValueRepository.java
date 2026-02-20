package com.build4all.catalog.repository;

import com.build4all.catalog.domain.Item;
import com.build4all.catalog.domain.ItemAttribute;
import com.build4all.catalog.domain.ItemAttributeValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemAttributeValueRepository extends JpaRepository<ItemAttributeValue, Long> {

    List<ItemAttributeValue> findByItem(Item item);

    List<ItemAttributeValue> findByItemAndAttribute(Item item, ItemAttribute attribute);
    
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ItemAttributeValue v where v.item.id = :itemId")
    int deleteAllByItemId(@Param("itemId") Long itemId);
    
    @Modifying
    @Query("delete from ItemAttributeValue v where v.item.id = :itemId")
    void deleteAllByItem_Id(@Param("itemId") Long itemId);
}
