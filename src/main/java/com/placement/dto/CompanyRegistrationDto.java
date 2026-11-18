package com.placement.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyRegistrationDto {

    @NotBlank(message = "Company name is required")
    @Size(min = 2, max = 120, message = "Company name must be between 2 and 120 characters")
    private String name;

    @NotBlank(message = "Work email is required")
    @Email(message = "Invalid email format")
    private String contactEmail;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;
}

