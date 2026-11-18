package com.placement.controller;

import com.placement.dto.StudentProfileDto;
import com.placement.exception.DuplicateApplicationException;
import com.placement.exception.EligibilityException;
import com.placement.exception.SingleOfferViolationException;
import com.placement.model.Application;
import com.placement.model.Company;
import com.placement.model.Job;
import com.placement.model.Student;
import com.placement.security.CustomUserDetails;
import com.placement.service.ApplicationService;
import com.placement.service.CompanyService;
import com.placement.service.JobService;
import com.placement.service.StudentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/student")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
@Slf4j
public class StudentController {

    private final StudentService studentService;
    private final JobService jobService;
    private final ApplicationService applicationService;
    private final CompanyService companyService;

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        Student student = studentService.getStudentByUserId(userDetails.getId());
        List<Job> eligibleJobs = jobService.getEligibleJobsForStudent(userDetails.getId());
        List<Application> applications = applicationService.getApplicationsByStudentUser(userDetails.getId());

        model.addAttribute("student", student);
        model.addAttribute("eligibleJobsCount", eligibleJobs.size());
        model.addAttribute("applicationsCount", applications.size());
        model.addAttribute("recentApplications", applications.stream().limit(5).toList());
        model.addAttribute("singleOfferPolicy", studentService.isSingleOfferPolicy());

        // Resolve company names for recent applications
        Map<String, String> companyNames = new HashMap<>();
        companyService.getAllCompanies().forEach(c -> companyNames.put(c.getId(), c.getName()));
        model.addAttribute("companyNames", companyNames);

        return "student/dashboard";
    }

    @GetMapping("/jobs")
    public String eligibleJobs(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        Student student = studentService.getStudentByUserId(userDetails.getId());
        List<Job> eligibleJobs = jobService.getEligibleJobsForStudent(userDetails.getId());
        List<Application> myApplications = applicationService.getApplicationsByStudentUser(userDetails.getId());
        Set<String> appliedJobIds = myApplications.stream()
                .map(Application::getJobId)
                .collect(Collectors.toSet());

        Map<String, String> companyNames = new HashMap<>();
        companyService.getAllCompanies().forEach(c -> companyNames.put(c.getId(), c.getName()));

        model.addAttribute("student", student);
        model.addAttribute("jobs", eligibleJobs);
        model.addAttribute("appliedJobIds", appliedJobIds);
        model.addAttribute("companyNames", companyNames);
        model.addAttribute("singleOfferPolicy", studentService.isSingleOfferPolicy());

        return "student/jobs";
    }

    @GetMapping("/jobs/{id}")
    public String jobDetails(@PathVariable("id") String jobId,
                             @AuthenticationPrincipal CustomUserDetails userDetails,
                             Model model) {
        Student student = studentService.getStudentByUserId(userDetails.getId());
        Job job = jobService.getJobById(jobId);
        Company company = companyService.getCompanyById(job.getCompanyId());

        boolean eligible = studentService.isStudentEligible(student, job);
        List<Application> myApplications = applicationService.getApplicationsByStudentUser(userDetails.getId());
        boolean alreadyApplied = myApplications.stream().anyMatch(a -> a.getJobId().equals(jobId));

        model.addAttribute("student", student);
        model.addAttribute("job", job);
        model.addAttribute("company", company);
        model.addAttribute("isEligible", eligible);
        model.addAttribute("alreadyApplied", alreadyApplied);
        model.addAttribute("singleOfferPolicy", studentService.isSingleOfferPolicy());

        return "student/job-details";
    }

    @PostMapping("/jobs/{id}/apply")
    public String applyToJob(@PathVariable("id") String jobId,
                             @AuthenticationPrincipal CustomUserDetails userDetails,
                             RedirectAttributes redirectAttributes) {
        try {
            applicationService.applyToJob(userDetails.getId(), jobId);
            redirectAttributes.addFlashAttribute("successMessage", "Application submitted successfully! Check 'My Applications' to track progress.");
        } catch (EligibilityException | DuplicateApplicationException | SingleOfferViolationException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Could not submit application: " + e.getMessage());
        }
        return "redirect:/student/applications";
    }

    @GetMapping("/applications")
    public String myApplications(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        Student student = studentService.getStudentByUserId(userDetails.getId());
        List<Application> applications = applicationService.getApplicationsByStudentUser(userDetails.getId());

        Map<String, Job> jobsMap = new HashMap<>();
        jobService.getAllJobs().forEach(j -> jobsMap.put(j.getId(), j));

        Map<String, Company> companiesMap = new HashMap<>();
        companyService.getAllCompanies().forEach(c -> companiesMap.put(c.getId(), c));

        model.addAttribute("student", student);
        model.addAttribute("applications", applications);
        model.addAttribute("jobsMap", jobsMap);
        model.addAttribute("companiesMap", companiesMap);

        return "student/applications";
    }

    @GetMapping("/profile")
    public String profilePage(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        Student student = studentService.getStudentByUserId(userDetails.getId());

        if (!model.containsAttribute("profileDto")) {
            StudentProfileDto dto = StudentProfileDto.builder()
                    .name(student.getName())
                    .branch(student.getBranch())
                    .cgpa(student.getCgpa())
                    .backlogs(student.getBacklogs())
                    .skills(String.join(", ", student.getSkills()))
                    .build();
            model.addAttribute("profileDto", dto);
        }

        model.addAttribute("student", student);
        model.addAttribute("branches", new String[]{"CSE", "IT", "ECE", "MECH", "CIVIL", "EEE"});
        return "student/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@Valid @ModelAttribute("profileDto") StudentProfileDto dto,
                                BindingResult bindingResult,
                                @AuthenticationPrincipal CustomUserDetails userDetails,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        if (bindingResult.hasErrors()) {
            Student student = studentService.getStudentByUserId(userDetails.getId());
            model.addAttribute("student", student);
            model.addAttribute("branches", new String[]{"CSE", "IT", "ECE", "MECH", "CIVIL", "EEE"});
            return "student/profile";
        }

        studentService.updateProfile(userDetails.getId(), dto);
        redirectAttributes.addFlashAttribute("successMessage", "Profile updated successfully!");
        return "redirect:/student/profile";
    }

    @PostMapping("/resume/upload")
    public String uploadResume(@RequestParam("resumeFile") MultipartFile file,
                               @AuthenticationPrincipal CustomUserDetails userDetails,
                               RedirectAttributes redirectAttributes) {
        try {
            studentService.uploadResume(userDetails.getId(), file);
            redirectAttributes.addFlashAttribute("successMessage", "Resume PDF uploaded successfully!");
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error saving file: " + e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/student/profile";
    }

    @GetMapping("/resume/download")
    public ResponseEntity<Resource> downloadMyResume(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Student student = studentService.getStudentByUserId(userDetails.getId());
        GridFsResource resource = studentService.downloadResume(student.getId());

        String filename = student.getResumeFileName() != null ? student.getResumeFileName() : "Resume.pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }

    // ─── My Interviews ────────────────────────────────────────────────────────

    @GetMapping("/interviews")
    public String myInterviews(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        Student student = studentService.getStudentByUserId(userDetails.getId());
        List<com.placement.model.Interview> interviews = applicationService.getInterviewsForStudent(userDetails.getId());

        Map<String, Job> jobsMap = new HashMap<>();
        jobService.getAllJobs().forEach(j -> jobsMap.put(j.getId(), j));

        Map<String, Company> companiesMap = new HashMap<>();
        companyService.getAllCompanies().forEach(c -> companiesMap.put(c.getId(), c));

        model.addAttribute("student", student);
        model.addAttribute("interviews", interviews);
        model.addAttribute("jobsMap", jobsMap);
        model.addAttribute("companiesMap", companiesMap);

        return "student/interviews";
    }

    @PostMapping("/interviews/{id}/confirm")
    public String confirmInterview(@PathVariable("id") String interviewId,
                                   @AuthenticationPrincipal CustomUserDetails userDetails,
                                   RedirectAttributes redirectAttributes) {
        try {
            applicationService.confirmInterview(userDetails.getId(), interviewId);
            redirectAttributes.addFlashAttribute("successMessage", "Interview confirmed! The recruiter has been notified.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error confirming interview: " + e.getMessage());
        }
        return "redirect:/student/interviews";
    }

    @PostMapping("/interviews/{id}/reschedule-request")
    public String requestReschedule(@PathVariable("id") String interviewId,
                                    @RequestParam("reason") String reason,
                                    @RequestParam(value = "preferredTime", required = false)
                                    @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
                                    java.time.LocalDateTime preferredTime,
                                    @AuthenticationPrincipal CustomUserDetails userDetails,
                                    RedirectAttributes redirectAttributes) {
        try {
            applicationService.requestReschedule(userDetails.getId(), interviewId, reason, preferredTime);
            redirectAttributes.addFlashAttribute("successMessage", "Reschedule request submitted to recruiter.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error submitting reschedule request: " + e.getMessage());
        }
        return "redirect:/student/interviews";
    }
}

