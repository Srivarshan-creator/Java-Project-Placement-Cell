package com.placement.service;

import com.placement.dto.JobPostDto;
import com.placement.dto.JobRejectDto;
import com.placement.exception.ResourceNotFoundException;
import com.placement.exception.UnauthorizedActionException;
import com.placement.model.Company;
import com.placement.model.Job;
import com.placement.model.JobStatus;
import com.placement.model.Student;
import com.placement.model.User;
import com.placement.repository.JobRepository;
import com.placement.repository.StudentRepository;
import com.placement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {

    private final JobRepository jobRepository;
    private final CompanyService companyService;
    private final StudentService studentService;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public Job createJob(String companyUserId, JobPostDto dto) {
        Company company = companyService.getCompanyByUserId(companyUserId);

        List<String> branches = dto.getAllowedBranches().stream()
                .map(b -> b.trim().toUpperCase())
                .collect(Collectors.toList());

        Job job = Job.builder()
                .companyId(company.getId())
                .title(dto.getTitle().trim())
                .description(dto.getDescription().trim())
                .packageLpa(dto.getPackageLpa())
                .minCgpa(dto.getMinCgpa())
                .maxBacklogs(dto.getMaxBacklogs())
                .allowedBranches(branches)
                .deadline(dto.getDeadline())
                .status(JobStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return jobRepository.save(job);
    }

    public Job updateJob(String companyUserId, String jobId, JobPostDto dto) {
        Company company = companyService.getCompanyByUserId(companyUserId);
        Job job = getJobById(jobId);

        if (!job.getCompanyId().equals(company.getId())) {
            throw new UnauthorizedActionException("You do not have permission to edit this job posting.");
        }

        List<String> branches = dto.getAllowedBranches().stream()
                .map(b -> b.trim().toUpperCase())
                .collect(Collectors.toList());

        job.setTitle(dto.getTitle().trim());
        job.setDescription(dto.getDescription().trim());
        job.setPackageLpa(dto.getPackageLpa());
        job.setMinCgpa(dto.getMinCgpa());
        job.setMaxBacklogs(dto.getMaxBacklogs());
        job.setAllowedBranches(branches);
        job.setDeadline(dto.getDeadline());
        // Editing resets status to PENDING for re-approval
        job.setStatus(JobStatus.PENDING);
        job.setRejectionReason(null);
        job.setUpdatedAt(LocalDateTime.now());

        return jobRepository.save(job);
    }

    public Job getJobById(String jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + jobId));
    }

    public List<Job> getJobsByCompany(String companyId) {
        return jobRepository.findByCompanyId(companyId);
    }

    public List<Job> getJobsByCompanyUser(String companyUserId) {
        Company company = companyService.getCompanyByUserId(companyUserId);
        return jobRepository.findByCompanyId(company.getId());
    }

    public List<Job> getAllJobs() {
        return jobRepository.findAll();
    }

    public List<Job> getPendingJobs() {
        return jobRepository.findByStatus(JobStatus.PENDING);
    }

    public List<Job> getApprovedJobs() {
        return jobRepository.findByStatusAndDeadlineGreaterThanEqual(JobStatus.APPROVED, LocalDate.now());
    }

    public List<Job> getEligibleJobsForStudent(String studentUserId) {
        Student student = studentService.getStudentByUserId(studentUserId);
        List<Job> activeJobs = jobRepository.findByStatusAndDeadlineGreaterThanEqual(JobStatus.APPROVED, LocalDate.now());

        return activeJobs.stream()
                .filter(job -> studentService.isStudentEligible(student, job))
                .collect(Collectors.toList());
    }

    public Job approveJob(String jobId, String adminEmail) {
        Job job = getJobById(jobId);
        job.setStatus(JobStatus.APPROVED);
        job.setApprovedBy(adminEmail);
        job.setApprovedAt(LocalDateTime.now());
        job.setRejectionReason(null);
        job.setUpdatedAt(LocalDateTime.now());
        Job savedJob = jobRepository.save(job);

        // Notify company
        Company company = companyService.getCompanyById(job.getCompanyId());
        emailService.sendJobApproved(company.getUserId(), company.getContactEmail(), job.getTitle());

        // Notify eligible students
        notifyEligibleStudents(savedJob, company);

        return savedJob;
    }

    public Job rejectJob(String jobId, String adminEmail, JobRejectDto dto) {
        Job job = getJobById(jobId);
        job.setStatus(JobStatus.REJECTED);
        job.setApprovedBy(adminEmail);
        job.setApprovedAt(LocalDateTime.now());
        job.setRejectionReason(dto.getReason().trim());
        job.setUpdatedAt(LocalDateTime.now());
        Job savedJob = jobRepository.save(job);

        // Notify company
        Company company = companyService.getCompanyById(job.getCompanyId());
        emailService.sendJobRejected(company.getUserId(), company.getContactEmail(), job.getTitle(), dto.getReason());

        return savedJob;
    }

    public Job closeJob(String companyUserId, String jobId) {
        Company company = companyService.getCompanyByUserId(companyUserId);
        Job job = getJobById(jobId);

        if (!job.getCompanyId().equals(company.getId())) {
            throw new UnauthorizedActionException("You do not have permission to close this job.");
        }

        job.setStatus(JobStatus.CLOSED);
        job.setUpdatedAt(LocalDateTime.now());
        return jobRepository.save(job);
    }

    private void notifyEligibleStudents(Job job, Company company) {
        List<Student> allStudents = studentRepository.findAll();
        for (Student student : allStudents) {
            if (studentService.isStudentEligible(student, job)) {
                userRepository.findById(student.getUserId()).ifPresent(user -> {
                    emailService.sendNewEligibleJobAlert(
                            user.getId(),
                            user.getEmail(),
                            student.getName(),
                            job.getTitle(),
                            company.getName(),
                            job.getPackageLpa()
                    );
                });
            }
        }
    }
}

