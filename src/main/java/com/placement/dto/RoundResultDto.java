package com.placement.dto;

import com.placement.model.RoundResult;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoundResultDto {

    @NotNull(message = "Round number is required")
    private Integer roundNumber;

    @NotNull(message = "Result is required")
    private RoundResult result;

    private Double score;
    private String feedback;
    private String notes;
}

