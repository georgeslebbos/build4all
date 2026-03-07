package com.build4all.catalog.service;

import com.build4all.catalog.domain.ItemStatus;
import com.build4all.catalog.dto.ItemStatusDTO;
import com.build4all.catalog.repository.ItemStatusRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Transactional
public class ItemStatusService {

    private final ItemStatusRepository itemStatusRepository;

    public ItemStatusService(ItemStatusRepository itemStatusRepository) {
        this.itemStatusRepository = itemStatusRepository;
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public List<ItemStatusDTO> findAllActive() {
        return itemStatusRepository.findByActiveTrueOrderBySortOrderAsc()
                .stream()
                .map(this::toDto)
                .toList();
    }

    private ItemStatusDTO toDto(ItemStatus s) {
        return new ItemStatusDTO(
                s.getId(),
                s.getCode(),
                s.getName()
        );
    }
}