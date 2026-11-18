package com.placement.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "jobs")
public class Job {

    @Id
    private String id;

    @Indexed
    private String companyId;

    private String title;

    private String description;

    private Double packageLpa;

    private Double minCgpa;

    private Integer maxBacklogs;

    @Builder.Default
    private List<String> allowedBranches = new ArrayList<>();

    @Indexed
    private LocalDate deadline;

    @Indexed
    @Builder.Default
    private JobStatus status = JobStatus.PENDING;

    private String rejectionReason;

    private String approvedBy;

    private LocalDateTime approvedAt;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}

