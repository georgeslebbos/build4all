package com.build4all.controller;

import com.build4all.entities.Interest;
import com.build4all.repositories.InterestRepository;
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
@RequestMapping("/api/interests")
public class InterestController {

    private final InterestRepository interestRepository;

    @Autowired
    private JwtUtil jwtUtil;

    public InterestController(InterestRepository interestRepository) {
        this.interestRepository = interestRepository;
    }

    // 🔐 Auth check
    private boolean isAuthorized(String token) {
        if (token == null || !token.startsWith("Bearer ")) return false;
        String jwt = token.substring(7).trim();
        return jwtUtil.isUserToken(jwt) || jwtUtil.isBusinessToken(jwt)
                || "SUPER_ADMIN".equals(jwtUtil.extractRole(jwt))
                || "MANAGER".equals(jwtUtil.extractRole(jwt));
    }

    // 🔹 GET all interests
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/all")
    public ResponseEntity<List<Map<String, Object>>> getAllInterests() {
        List<Map<String, Object>> interests = interestRepository.findAll()
            .stream()
            .map(i -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", i.getId());
                map.put("name", i.getName());
                map.put("icon", i.getIcon());
                map.put("iconLib", i.getIconLibrary());
                return map;
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(interests);
    }

    // 🔹 Categorized interests (still static for now)
    @GetMapping("/categorized")
    public ResponseEntity<Map<String, List<String>>> getCategorizedInterests() {
        Map<String, List<String>> categorizedTypes = new LinkedHashMap<>();
        categorizedTypes.put("Sports", List.of("Football", "Yoga", "Martial Arts", "Hiking"));
        categorizedTypes.put("Music", List.of("Singing", "Dance", "Music Production"));
        categorizedTypes.put("Tech", List.of("Coding", "Robotics", "3D Printing"));
        categorizedTypes.put("Other", List.of("Podcasting", "Astronomy", "Public Service"));

        return ResponseEntity.ok(categorizedTypes);
    }

    // 🔹 CREATE new interest
    @PostMapping("/create")
    public ResponseEntity<String> createInterest(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {

        if (!isAuthorized(authHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String name = body.get("name");
        String icon = body.get("icon");
        String iconLib = body.get("iconLib");

        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body("Interest name is required");
        }

        if (interestRepository.existsByNameIgnoreCase(name)) {
            return ResponseEntity.badRequest().body("Interest already exists");
        }

        Interest interest = new Interest(name, icon, iconLib);
        interestRepository.save(interest);

        return ResponseEntity.ok("Interest created successfully");
    }

    // 🔹 GET all icons (from DB instead of Enum)
    @GetMapping("/icons")
    public ResponseEntity<List<String>> getAllIcons() {
        List<String> icons = interestRepository.findAll()
                .stream()
                .map(Interest::getIcon)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return ResponseEntity.ok(icons);
    }

    // 🔹 GET all icon libraries (from DB instead of Enum)
    @GetMapping("/icon-libraries")
    public ResponseEntity<List<String>> getAllIconLibraries() {
        List<String> libs = interestRepository.findAll()
                .stream()
                .map(Interest::getIconLibrary)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return ResponseEntity.ok(libs);
    }
}
