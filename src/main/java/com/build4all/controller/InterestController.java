package com.build4all.controller;

import com.build4all.entities.Interests;
import com.build4all.enums.IconLibraryEnum;
import com.build4all.enums.InterestIconEnum;
import com.build4all.repositories.InterestsRepository;
import com.build4all.security.JwtUtil;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/activity-types")
public class InterestController {

    private final InterestsRepository interestsRepository;

    @Autowired
    private JwtUtil jwtUtil;

    public InterestController(InterestsRepository interestsRepository) {
        this.interestsRepository = interestsRepository;
    }

    private boolean isAuthorized(String token) {
        if (token == null || !token.startsWith("Bearer ")) return false;
        String jwt = token.substring(7).trim();
        return jwtUtil.isUserToken(jwt) || jwtUtil.isBusinessToken(jwt)
                || "SUPER_ADMIN".equals(jwtUtil.extractRole(jwt))
                || "MANAGER".equals(jwtUtil.extractRole(jwt));
    }

    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
        })
    @GetMapping("/all")
    public ResponseEntity<List<Map<String, Object>>> getAllInterests() {
        List<Map<String, Object>> interests = interestsRepository.findAll()
            .stream()
            .map(i -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", i.getId());
                map.put("name", i.getName());

                // ✅ Use icon.getIconName() if available
                map.put("icon", i.getIcon() != null ? i.getIcon().getIconName() : null);
                map.put("iconLib", i.getIconLib() != null ? i.getIconLib().name() : null);
                return map;
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(interests);
    }


    @GetMapping("/categorized")
    public ResponseEntity<Map<String, List<String>>> getCategorizedInterests() {
        Map<String, List<String>> categorizedTypes = new LinkedHashMap<>();
        categorizedTypes.put("Sports", List.of("Football", "Yoga", "Martial Arts", "Hiking", "Horseback Riding", "Fishing"));
        categorizedTypes.put("Music", List.of("Music", "Dance", "Music Production"));
        categorizedTypes.put("Art", List.of("Art", "Sculpting", "Knitting", "Calligraphy"));
        categorizedTypes.put("Tech", List.of("Coding", "Robotics", "3D Printing", "Science Experiments"));
        categorizedTypes.put("Fitness", List.of("Fitness", "Self-Defense", "Meditation"));
        categorizedTypes.put("Cooking", List.of("Cooking"));
        categorizedTypes.put("Travel", List.of("Travel", "Nature Walks"));
        categorizedTypes.put("Gaming", List.of("Gaming", "Board Games"));
        categorizedTypes.put("Theater", List.of("Theater", "Stand-up Comedy", "Storytelling"));
        categorizedTypes.put("Language", List.of("Language", "Public Speaking", "Writing"));
        categorizedTypes.put("Photography", List.of("Photography", "Film Making"));
        categorizedTypes.put("DIY", List.of("DIY", "Carpentry", "Interior Design"));
        categorizedTypes.put("Beauty", List.of("Makeup & Beauty"));
        categorizedTypes.put("Finance", List.of("Investment & Finance", "Entrepreneurship"));
        categorizedTypes.put("Other", List.of("Pet Training", "Podcasting", "Magic Tricks", "Astronomy", "Public Service", "Productivity", "Bird Watching", "Cultural Events"));

        return ResponseEntity.ok(categorizedTypes);
    }
    
    @PostMapping("/create")
    public ResponseEntity<String> createInterest(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {

        if (!isAuthorized(authHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String name = body.get("name");
        String iconStr = body.get("icon");
        String iconLibStr = body.get("iconLib");

        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body("Interest name is required");
        }

        if (interestsRepository.existsByNameIgnoreCase(name)) {
            return ResponseEntity.badRequest().body("Interest already exists");
        }

        InterestIconEnum icon = null;
        IconLibraryEnum iconLib = null;

        try {
            if (iconStr != null) icon = InterestIconEnum.valueOf(iconStr);
        } catch (IllegalArgumentException ignored) {}

        try {
            if (iconLibStr != null) iconLib = IconLibraryEnum.valueOf(iconLibStr);
        } catch (IllegalArgumentException ignored) {}

        Interests interest = new Interests(name, icon, iconLib);
        interestsRepository.save(interest);

        return ResponseEntity.ok("Interest created successfully");
    }
    
    @GetMapping("/icons")
    public ResponseEntity<List<String>> getAllIcons() {
        List<String> icons = Arrays.stream(InterestIconEnum.values())
                                   .map(InterestIconEnum::name)
                                   .toList();
        return ResponseEntity.ok(icons);
    }

    @GetMapping("/icon-libraries")
    public ResponseEntity<List<String>> getAllIconLibraries() {
        List<String> libs = Arrays.stream(IconLibraryEnum.values())
                                  .map(Enum::name)
                                  .toList();
        return ResponseEntity.ok(libs);
    }

}
