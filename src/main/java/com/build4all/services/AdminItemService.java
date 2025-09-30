package com.build4all.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.build4all.dto.AdminItemDTO;
import com.build4all.repositories.ItemRepository;

@Service
public class AdminItemService {

    @Autowired
    private ItemRepository itemRepository;

    public List<AdminItemDTO> getAllItems() {
        return itemRepository.findAllItemsWithBusinessInfo();
    }

	
}
