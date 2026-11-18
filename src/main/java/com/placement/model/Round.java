package com.placement.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Round {
    private Integer roundNumber;
    private String name;
    private LocalDateTime scheduledAt;
    private RoundResult result;
    private String notes;
    private Double score;
    private String feedback;
}

