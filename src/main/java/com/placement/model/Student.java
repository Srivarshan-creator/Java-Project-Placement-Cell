package com.placement.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "students")
public class Student {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    private String name;

    @Indexed
    private String branch;

    private Double cgpa;

    private Integer backlogs;

    @Builder.Default
    private List<String> skills = new ArrayList<>();

    private String resumeFileId;

    private String resumeFileName;

    @Builder.Default
    private boolean placed = false;

    private String placedAtJobId;

    private Double offerLpa;

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}

