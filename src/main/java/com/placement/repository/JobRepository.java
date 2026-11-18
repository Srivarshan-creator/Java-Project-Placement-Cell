package com.placement.repository;

import com.placement.model.Job;
import com.placement.model.JobStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface JobRepository extends MongoRepository<Job, String> {
    List<Job> findByCompanyId(String companyId);
    List<Job> findByStatus(JobStatus status);
    List<Job> findByStatusAndDeadlineGreaterThanEqual(JobStatus status, LocalDate date);
    List<Job> findByStatusAndDeadlineBetween(JobStatus status, LocalDate start, LocalDate end);
}

