package com.build4all.controller;



import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.build4all.dto.ItemTypeDTO;
import com.build4all.entities.ItemType;
import com.build4all.repositories.ItemTypeRepository;
import com.build4all.services.ItemTypeService;
import com.build4all.enums.ItemTypeEnum;


import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/item-types")
public class ItemTypeController {


	 @Autowired
	    private ItemTypeRepository itemTypeRepository; 

	    @Autowired
	    private ItemTypeService itemTypeService; 

    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    

    @GetMapping
    public List<ItemType> getAll() {
        return itemTypeRepository.findAllByOrderByNameAsc();
    }


    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @DeleteMapping("/{id}")
    
    public void delete(@PathVariable Long id) {
    	itemTypeRepository.deleteById(id);
    }


    @ApiResponses(value = {
    	    @ApiResponse(responseCode = "200", description = "Successful"),
    	    @ApiResponse(responseCode = "400", description = "Bad Request – Invalid or missing parameters or token"),
    	    @ApiResponse(responseCode = "401", description = "Unauthorized – Authentication credentials are missing or invalid"),
    	    @ApiResponse(responseCode = "402", description = "Payment Required – Payment is required to access this resource (reserved)"),
    	    @ApiResponse(responseCode = "403", description = "Forbidden – You do not have permission to perform this action"),
    	    @ApiResponse(responseCode = "404", description = "Not Found – The requested resource could not be found"),
    	    @ApiResponse(responseCode = "500", description = "Internal Server Error – An unexpected error occurred on the server")
    	})
    @PostMapping("/seed-defaults")
    public String seedDefaults() {
        itemTypeService.ensureItemTypes();
        return "Default item types and interests seeded successfully.";
    }
    
    @GetMapping("/guest")
    public List<ItemTypeDTO> getAllItemTypes() {
        return  itemTypeRepository.findAllByOrderByNameAsc()
            .stream()
            .map(type -> {
                String rawName = type.getName();
                String displayName = Arrays.stream(com.build4all.enums.ItemTypeEnum.values())
                	    .filter(e -> e.name().equals(rawName))
                	    .findFirst()
                	    .map(com.build4all.enums.ItemTypeEnum::getDisplayName)
                	    .orElse(rawName);

                return new ItemTypeDTO(
                    type.getId(),
                    rawName,
                    displayName, // ✅ Pass display name here
                    type.getIcon() != null ? type.getIcon().name() : null,
                    type.getIconLib() != null ? type.getIconLib().name() : null
                );
            })
            .toList();
    }

 

}