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
@Document(collection = "interviews")
public class Interview {

    @Id
    private String id;

    @Indexed
    private String applicationId;

    @Indexed
    private String studentId;

    @Indexed
    private String jobId;

    @Indexed
    private String companyId;

    private Integer roundNumber;
    private String roundType; // Aptitude, Technical, HR, Group Discussion, Custom
    private String roundName;

    private LocalDateTime startDateTime;
    private Integer durationMinutes; // e.g. 30, 45, 60
    private LocalDateTime endDateTime;

    private String mode; // ONLINE or OFFLINE
    private String venueOrLink;

    @Builder.Default
    private List<String> panelMembers = new ArrayList<>();

    private String instructions;

    @Builder.Default
    private InterviewStatus status = InterviewStatus.SCHEDULED;

    @Builder.Default
    private String studentConfirmation = "PENDING"; // PENDING, CONFIRMED, RESCHEDULE_REQUESTED

    private String rescheduleReason;
    private LocalDateTime preferredRescheduleTime;

    @Builder.Default
    private RoundResult result = RoundResult.PENDING;

    private Double score;
    private String feedback;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
