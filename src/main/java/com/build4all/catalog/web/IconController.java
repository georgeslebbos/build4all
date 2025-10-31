package com.build4all.catalog.web;

import com.build4all.catalog.domain.Icon;
import com.build4all.catalog.service.IconService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/icons")
public class IconController {

    private final IconService service;

    public IconController(IconService service) {
        this.service = service;
    }

    @Operation(summary = "Get all icons")
    @ApiResponse(responseCode = "200", description = "List of icons")
    @GetMapping
    public List<Icon> getAll() {
        return service.findAll();
    }

    @Operation(summary = "Create an icon")
    @ApiResponse(responseCode = "201", description = "Icon created")
    @PostMapping
    public Icon create(@RequestBody Icon icon) {
        return service.save(icon);
    }

    @Operation(summary = "Update an icon")
    @ApiResponse(responseCode = "200", description = "Icon updated")
    @PutMapping("/{id}")
    public ResponseEntity<Icon> update(@PathVariable Long id, @RequestBody Icon icon) {
        return service.findById(id)
                .map(existing -> {
                    existing.setName(icon.getName());
                    existing.setLibrary(icon.getLibrary());
                    return ResponseEntity.ok(service.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Delete an icon")
    @ApiResponse(responseCode = "204", description = "Icon deleted")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
