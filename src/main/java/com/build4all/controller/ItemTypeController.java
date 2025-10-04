package com.build4all.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.build4all.dto.ItemTypeDTO;
import com.build4all.entities.ItemType;
import com.build4all.repositories.ItemTypeRepository;
import com.build4all.services.ItemTypeService;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/item-types")
public class ItemTypeController {

    private final ItemTypeRepository itemTypeRepository;
    private final ItemTypeService itemTypeService;

    public ItemTypeController(ItemTypeRepository itemTypeRepository, ItemTypeService itemTypeService) {
        this.itemTypeRepository = itemTypeRepository;
        this.itemTypeService = itemTypeService;
    }

    // 🔹 GET ALL
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successful")
    })
    @GetMapping
    public List<ItemType> getAll() {
        return itemTypeRepository.findAll();
    }

    // 🔹 DELETE
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successful")
    })
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        itemTypeRepository.deleteById(id);
    }

    // 🔹 CREATE
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Created")
    })
    @PostMapping
    public ItemType create(@RequestBody ItemType itemType) {
        return itemTypeRepository.save(itemType);
    }

    // 🔹 UPDATE
    @PutMapping("/{id}")
    public ResponseEntity<ItemType> update(@PathVariable Long id, @RequestBody ItemType itemType) {
        return itemTypeRepository.findById(id)
                .map(existing -> {
                    existing.setName(itemType.getName());
                    existing.setIcon(itemType.getIcon());
                    existing.setIconLibrary(itemType.getIconLibrary());
                    existing.setProject(itemType.getProject());
                    existing.setInterest(itemType.getInterest());
                    return ResponseEntity.ok(itemTypeRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // 🔹 BY PROJECT
    @GetMapping("/by-project/{projectId}")
    public List<ItemTypeDTO> getByProject(@PathVariable Long projectId) {
        return itemTypeRepository.findByProject_IdOrderByNameAsc(projectId)
                .stream()
                .map(type -> new ItemTypeDTO(
                        type.getId(),
                        type.getName(),
                        type.getName(),  // Display name = DB value now
                        type.getIcon(),
                        type.getIconLibrary(),
                        type.getProject() != null ? type.getProject().getId() : null,
                        type.getProject() != null ? type.getProject().getProjectName() : null
                ))
                .collect(Collectors.toList());
    }

    // 🔹 GUEST VIEW
    @GetMapping("/guest")
    public List<ItemTypeDTO> getAllItemTypes() {
        return itemTypeRepository.findAll()
                .stream()
                .map(type -> new ItemTypeDTO(
                        type.getId(),
                        type.getName(),
                        type.getName(), // Display name from DB
                        type.getIcon(),
                        type.getIconLibrary(),
                        type.getProject() != null ? type.getProject().getId() : null,
                        type.getProject() != null ? type.getProject().getProjectName() : null
                ))
                .collect(Collectors.toList());
    }
}
