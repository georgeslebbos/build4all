package com.build4all.catalog.repository;

import com.build4all.catalog.domain.Item;
import com.build4all.catalog.domain.ItemAttribute;
import com.build4all.catalog.domain.ItemAttributeValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemAttributeValueRepository extends JpaRepository<ItemAttributeValue, Long> {

    List<ItemAttributeValue> findByItem(Item item);

    List<ItemAttributeValue> findByItemAndAttribute(Item item, ItemAttribute attribute);
}
