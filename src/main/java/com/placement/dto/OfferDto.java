package com.placement.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfferDto {

    @NotNull(message = "Offered Package (LPA) is required")
    @DecimalMin(value = "0.1", message = "Offer package must be greater than 0")
    private Double offerLpa;
}

