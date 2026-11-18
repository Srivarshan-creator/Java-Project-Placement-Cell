package com.placement.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "applications")
@CompoundIndexes({
    @CompoundIndex(name = "uniq_student_job", def = "{'studentId': 1, 'jobId': 1}", unique = true)
})
public class Application {

    @Id
    private String id;

    @Indexed
    private String studentId;

    @Indexed
    private String jobId;

    @Indexed
    private String companyId;

    @Indexed
    private String branch; // denormalized

    @Indexed
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.APPLIED;

    @Builder.Default
    private List<Round> rounds = new ArrayList<>();

    private Double offerLpa;

    @Builder.Default
    private LocalDateTime appliedAt = LocalDateTime.now();
}

