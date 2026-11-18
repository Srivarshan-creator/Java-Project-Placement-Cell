package com.placement.repository;

import com.placement.model.Application;
import com.placement.model.ApplicationStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationRepository extends MongoRepository<Application, String> {
    List<Application> findByStudentId(String studentId);
    List<Application> findByJobId(String jobId);
    List<Application> findByCompanyId(String companyId);
    List<Application> findByCompanyIdAndJobId(String companyId, String jobId);
    Optional<Application> findByStudentIdAndJobId(String studentId, String jobId);
    boolean existsByStudentIdAndJobId(String studentId, String jobId);
    List<Application> findByStatus(ApplicationStatus status);
    long countByStudentIdAndStatus(String studentId, ApplicationStatus status);
}

