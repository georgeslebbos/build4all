package com.build4all.app.internaltesting.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateIosInternalTestingRequestDto(

        @NotBlank(message = "Apple email is required")
        @Email(message = "Invalid Apple email")
        @Size(max = 255, message = "Apple email is too long")
        String appleEmail,

        @NotBlank(message = "First name is required")
        @Size(min = 2, max = 100, message = "First name must be between 2 and 100 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(min = 2, max = 100, message = "Last name must be between 2 and 100 characters")
        String lastName
) {}