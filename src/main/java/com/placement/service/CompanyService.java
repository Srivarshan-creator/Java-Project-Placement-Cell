package com.placement.service;

import com.placement.dto.CompanyCreateDto;
import com.placement.exception.BadRequestException;
import com.placement.exception.ResourceNotFoundException;
import com.placement.model.Company;
import com.placement.model.Role;
import com.placement.model.User;
import com.placement.repository.CompanyRepository;
import com.placement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public Company createCompany(CompanyCreateDto dto) {
        String email = dto.getContactEmail().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("An account with email " + email + " already exists.");
        }

        String tempPassword = generateTemporaryPassword(10);

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(tempPassword))
                .role(Role.COMPANY)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();
        user = userRepository.save(user);

        Company company;
        try {
            company = Company.builder()
                    .userId(user.getId())
                    .name(dto.getName().trim())
                    .contactEmail(email)
                    .createdAt(LocalDateTime.now())
                    .build();
            company = companyRepository.save(company);
        } catch (Exception ex) {
            // Remove orphan user
            userRepository.deleteById(user.getId());
            throw ex;
        }

        // Send email with temporary password
        emailService.sendCompanyAccountCreated(user.getId(), email, company.getName(), tempPassword);

        log.info("Company created: {} with temporary password sent to {}", company.getName(), email);
        return company;
    }

    public Company registerCompany(com.placement.dto.CompanyRegistrationDto dto) {
        String email = dto.getContactEmail().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("An account with email " + email + " already exists.");
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(dto.getPassword()))
                .role(Role.COMPANY)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();
        user = userRepository.save(user);

        Company company;
        try {
            company = Company.builder()
                    .userId(user.getId())
                    .name(dto.getName().trim())
                    .contactEmail(email)
                    .createdAt(LocalDateTime.now())
                    .build();
            company = companyRepository.save(company);
        } catch (Exception ex) {
            // Clean up orphan user
            userRepository.deleteById(user.getId());
            throw ex;
        }

        log.info("Company self-registered: {} ({})", company.getName(), email);
        return company;
    }

    public Company getCompanyByUserId(String userId) {
        return companyRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found for user: " + userId));
    }

    public Company getCompanyById(String id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found with id: " + id));
    }

    public List<Company> getAllCompanies() {
        return companyRepository.findAll();
    }

    private String generateTemporaryPassword(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}

