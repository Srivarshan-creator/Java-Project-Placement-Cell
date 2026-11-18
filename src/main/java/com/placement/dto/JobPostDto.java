package com.placement.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobPostDto {

    private String id;

    @NotBlank(message = "Job title is required")
    @Size(min = 3, max = 150, message = "Title must be between 3 and 150 characters")
    private String title;

    @NotBlank(message = "Job description is required")
    private String description;

    @NotNull(message = "Package (LPA) is required")
    @DecimalMin(value = "0.1", message = "Package must be greater than 0")
    private Double packageLpa;

    @NotNull(message = "Minimum CGPA is required")
    @DecimalMin(value = "0.0", message = "CGPA cannot be negative")
    @DecimalMax(value = "10.0", message = "CGPA cannot exceed 10.0")
    private Double minCgpa;

    @NotNull(message = "Maximum allowed backlogs is required")
    @Min(value = 0, message = "Backlogs cannot be negative")
    private Integer maxBacklogs;

    @NotEmpty(message = "Select at least one eligible branch")
    @Builder.Default
    private List<String> allowedBranches = new ArrayList<>();

    @NotNull(message = "Application deadline is required")
    @FutureOrPresent(message = "Deadline must be today or in the future")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate deadline;
}

