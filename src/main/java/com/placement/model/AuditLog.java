package com.placement.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "audit_logs")
public class AuditLog {

    @Id
    private String id;

    @Indexed
    private String actorUserId;

    private String actorRole;

    private String action; // e.g. STATUS_CHANGE, INTERVIEW_SCHEDULED, ROUND_RESULT, OFFER_MADE

    @Indexed
    private String targetEntity; // APPLICATION, INTERVIEW, JOB

    @Indexed
    private String targetId;

    private String details;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}

