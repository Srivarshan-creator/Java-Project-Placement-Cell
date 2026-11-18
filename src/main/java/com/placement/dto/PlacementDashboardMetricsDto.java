package com.placement.dto;

import com.placement.model.Student;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlacementDashboardMetricsDto {
    private long totalStudents;
    private long totalPlaced;
    private double overallPlacementPercentage;
    private double overallAvgPackage;
    private double overallHighestPackage;

    @Builder.Default
    private List<DepartmentMetricDto> departmentMetrics = new ArrayList<>();

    @Builder.Default
    private List<CompanyOfferMetricDto> companyOfferMetrics = new ArrayList<>();

    @Builder.Default
    private List<Student> unplacedStudents = new ArrayList<>();
}

