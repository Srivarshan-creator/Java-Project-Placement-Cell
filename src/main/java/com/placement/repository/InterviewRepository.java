package com.placement.repository;

import com.placement.model.Interview;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewRepository extends MongoRepository<Interview, String> {
    List<Interview> findByStudentId(String studentId);
    List<Interview> findByStudentIdOrderByStartDateTimeAsc(String studentId);
    List<Interview> findByJobId(String jobId);
    List<Interview> findByCompanyId(String companyId);
    List<Interview> findByApplicationId(String applicationId);
    Optional<Interview> findByApplicationIdAndRoundNumber(String applicationId, Integer roundNumber);

    // Conflict detection helpers
    List<Interview> findByStudentIdAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
            String studentId, LocalDateTime end, LocalDateTime start);

    List<Interview> findByCompanyIdAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
            String companyId, LocalDateTime end, LocalDateTime start);
}
