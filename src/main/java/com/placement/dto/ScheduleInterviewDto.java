package com.placement.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleInterviewDto {

    @Builder.Default
    private List<String> applicationIds = new ArrayList<>();

    @NotNull(message = "Round number is required")
    @Min(value = 1, message = "Round number must be at least 1")
    private Integer roundNumber;

    @NotBlank(message = "Round type is required")
    private String roundType; // Aptitude, Technical, HR, Group Discussion, Custom

    @NotBlank(message = "Round name is required")
    private String roundName;

    @NotNull(message = "Start date & time is required")
    @Future(message = "Interview start time must be in the future")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startDateTime;

    @NotNull(message = "Duration in minutes is required")
    @Min(value = 10, message = "Duration must be at least 10 minutes")
    private Integer durationMinutes;

    @NotBlank(message = "Mode is required")
    private String mode; // ONLINE or OFFLINE

    private String venueOrLink;
    private String panelNames; // comma separated
    private String instructions;

    // Slot allocation mode: SAME_SLOT or AUTO_SLOTS
    @Builder.Default
    private String slotMode = "SAME_SLOT";
    
    @Builder.Default
    private Integer breakMinutes = 5;
}

