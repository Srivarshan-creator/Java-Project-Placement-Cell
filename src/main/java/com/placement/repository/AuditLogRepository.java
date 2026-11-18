package com.placement.repository;

import com.placement.model.AuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends MongoRepository<AuditLog, String> {
    List<AuditLog> findByTargetIdOrderByTimestampDesc(String targetId);
    List<AuditLog> findTop50ByOrderByTimestampDesc();
}

