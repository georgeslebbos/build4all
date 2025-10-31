package com.build4all.catalog.dto;

/** Flat DTO to avoid lazy-init issues and keep client JSON stable. */
public record CategoryDTO(
        Long id,
        String name,
        String iconName,
        String iconLibrary
) {}
