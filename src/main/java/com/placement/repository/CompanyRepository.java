package com.placement.repository;

import com.placement.model.Company;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyRepository extends MongoRepository<Company, String> {
    Optional<Company> findByUserId(String userId);
    Optional<Company> findByContactEmail(String contactEmail);
    boolean existsByContactEmail(String contactEmail);
}

