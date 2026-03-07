package com.build4all.catalog.web;

import com.build4all.catalog.service.ItemStatusService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/item-statuses")
public class ItemStatusController {

    private final ItemStatusService itemStatusService;

    public ItemStatusController(ItemStatusService itemStatusService) {
        this.itemStatusService = itemStatusService;
    }

    @GetMapping
    @Operation(summary = "List active item statuses")
    public ResponseEntity<?> getAll() {
        return ResponseEntity.ok(itemStatusService.findAllActive());
    }
}