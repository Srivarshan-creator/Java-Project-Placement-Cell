package com.placement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentMetricDto {
    private String branch;
    private long totalStudents;
    private long placedCount;
    private double placementPercentage;
    private double averagePackage;
    private double highestPackage;
}

