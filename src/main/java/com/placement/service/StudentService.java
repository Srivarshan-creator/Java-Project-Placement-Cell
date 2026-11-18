package com.placement.service;

import com.mongodb.client.gridfs.model.GridFSFile;
import com.placement.dto.StudentProfileDto;
import com.placement.dto.StudentRegistrationDto;
import com.placement.exception.BadRequestException;
import com.placement.exception.ResourceNotFoundException;
import com.placement.model.Job;
import com.placement.model.JobStatus;
import com.placement.model.Role;
import com.placement.model.Student;
import com.placement.model.User;
import com.placement.repository.StudentRepository;
import com.placement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;
    private final GridFsTemplate gridFsTemplate;

    @Value("${placement.policy.single-offer-only:true}")
    private boolean singleOfferPolicy;

    public Student registerStudent(StudentRegistrationDto dto) {
        String normalizedEmail = dto.getEmail().toLowerCase().trim();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new BadRequestException("An account with email " + normalizedEmail + " already exists.");
        }

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(dto.getPassword()))
                .role(Role.STUDENT)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();
        user = userRepository.save(user);

        List<String> skillList = parseSkills(dto.getSkills());

        Student student;
        try {
            student = Student.builder()
                    .userId(user.getId())
                    .name(dto.getName().trim())
                    .branch(dto.getBranch().trim().toUpperCase())
                    .cgpa(dto.getCgpa())
                    .backlogs(dto.getBacklogs())
                    .skills(skillList)
                    .placed(false)
                    .updatedAt(LocalDateTime.now())
                    .build();
            student = studentRepository.save(student);
        } catch (Exception ex) {
            userRepository.deleteById(user.getId());
            throw ex;
        }

        return student;
    }

    public Student getStudentByUserId(String userId) {
        return studentRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found for user: " + userId));
    }

    public Student getStudentById(String id) {
        return studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + id));
    }

    public List<Student> getAllStudents() {
        return studentRepository.findAll();
    }

    public List<Student> getStudentsByBranch(String branch) {
        if (branch == null || branch.isBlank()) {
            return studentRepository.findAll();
        }
        return studentRepository.findByBranch(branch.trim().toUpperCase());
    }

    public Student updateProfile(String userId, StudentProfileDto dto) {
        Student student = getStudentByUserId(userId);
        student.setName(dto.getName().trim());
        student.setBranch(dto.getBranch().trim().toUpperCase());
        student.setCgpa(dto.getCgpa());
        student.setBacklogs(dto.getBacklogs());
        student.setSkills(parseSkills(dto.getSkills()));
        student.setUpdatedAt(LocalDateTime.now());
        return studentRepository.save(student);
    }

    public void uploadResume(String userId, MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new BadRequestException("Uploaded file cannot be empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equalsIgnoreCase("application/pdf")) {
            throw new BadRequestException("Only PDF resumes are supported");
        }
        if (file.getSize() > 2 * 1024 * 1024) {
            throw new BadRequestException("Resume PDF file size cannot exceed 2 MB");
        }

        Student student = getStudentByUserId(userId);

        // Delete existing resume file from GridFS if present
        if (student.getResumeFileId() != null) {
            try {
                gridFsTemplate.delete(Query.query(Criteria.where("_id").is(student.getResumeFileId())));
            } catch (Exception e) {
                log.warn("Could not delete old resume file: {}", e.getMessage());
            }
        }

        try (InputStream inputStream = file.getInputStream()) {
            ObjectId fileId = gridFsTemplate.store(
                    inputStream,
                    file.getOriginalFilename(),
                    file.getContentType()
            );
            student.setResumeFileId(fileId.toHexString());
            student.setResumeFileName(file.getOriginalFilename());
            student.setUpdatedAt(LocalDateTime.now());
            studentRepository.save(student);
        }
    }

    public GridFsResource downloadResume(String studentId) {
        Student student = getStudentById(studentId);
        if (student.getResumeFileId() == null) {
            throw new ResourceNotFoundException("Resume has not been uploaded yet");
        }

        GridFSFile file = gridFsTemplate.findOne(Query.query(Criteria.where("_id").is(student.getResumeFileId())));
        if (file == null) {
            throw new ResourceNotFoundException("Resume file not found in storage");
        }

        return gridFsTemplate.getResource(file);
    }

    public boolean isStudentEligible(Student student, Job job) {
        if (student == null || job == null) {
            return false;
        }

        // Job must be APPROVED
        if (job.getStatus() != JobStatus.APPROVED) {
            return false;
        }

        // Job deadline must not be passed
        if (job.getDeadline() != null && job.getDeadline().isBefore(LocalDate.now())) {
            return false;
        }

        // Single-offer policy check: if student is placed and single-offer policy is enabled, not eligible
        if (singleOfferPolicy && student.isPlaced()) {
            return false;
        }

        // CGPA check
        if (student.getCgpa() == null || student.getCgpa() < job.getMinCgpa()) {
            return false;
        }

        // Backlogs check
        if (student.getBacklogs() == null || student.getBacklogs() > job.getMaxBacklogs()) {
            return false;
        }

        // Branch check
        if (student.getBranch() == null || job.getAllowedBranches() == null) {
            return false;
        }

        String studentBranch = student.getBranch().trim().toUpperCase();
        boolean branchMatch = job.getAllowedBranches().stream()
                .anyMatch(b -> b.trim().equalsIgnoreCase(studentBranch));

        return branchMatch;
    }

    public boolean isSingleOfferPolicy() {
        return singleOfferPolicy;
    }

    private List<String> parseSkills(String skillsString) {
        if (skillsString == null || skillsString.isBlank()) {
            return List.of();
        }
        return Arrays.stream(skillsString.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}

