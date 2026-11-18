package com.placement.controller;

import com.placement.dto.CompanyCreateDto;
import com.placement.dto.JobRejectDto;
import com.placement.dto.PlacementDashboardMetricsDto;
import com.placement.exception.BadRequestException;
import com.placement.model.Application;
import com.placement.model.Company;
import com.placement.model.Job;
import com.placement.model.JobStatus;
import com.placement.model.Student;
import com.placement.security.CustomUserDetails;
import com.placement.service.AnalyticsService;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('PLACEMENT_CELL')")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final AnalyticsService analyticsService;
    private final JobService jobService;
    private final CompanyService companyService;
    private final StudentService studentService;
    private final ApplicationService applicationService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        PlacementDashboardMetricsDto metrics = analyticsService.getPlacementDashboardMetrics();
        List<Job> pendingJobs = jobService.getPendingJobs();

        model.addAttribute("metrics", metrics);
        model.addAttribute("pendingJobsCount", pendingJobs.size());
        model.addAttribute("recentPendingJobs", pendingJobs.stream().limit(5).toList());

        Map<String, String> companyNames = new HashMap<>();
        companyService.getAllCompanies().forEach(c -> companyNames.put(c.getId(), c.getName()));
        model.addAttribute("companyNames", companyNames);

        return "admin/dashboard";
    }

    @GetMapping("/jobs/pending")
    public String pendingJobs(Model model) {
        List<Job> pendingJobs = jobService.getPendingJobs();
        Map<String, Company> companiesMap = new HashMap<>();
        companyService.getAllCompanies().forEach(c -> companiesMap.put(c.getId(), c));

        model.addAttribute("jobs", pendingJobs);
        model.addAttribute("companiesMap", companiesMap);
        model.addAttribute("rejectDto", new JobRejectDto());

        return "admin/pending-jobs";
    }

    @PostMapping("/jobs/approve/{id}")
    public String approveJob(@PathVariable("id") String jobId,
                             @AuthenticationPrincipal CustomUserDetails userDetails,
                             RedirectAttributes redirectAttributes) {
        Job job = jobService.approveJob(jobId, userDetails.getUsername());
        redirectAttributes.addFlashAttribute("successMessage", "Job '" + job.getTitle() + "' approved! Eligible students have been notified via email.");
        return "redirect:/admin/jobs/pending";
    }

    @PostMapping("/jobs/reject/{id}")
    public String rejectJob(@PathVariable("id") String jobId,
                            @Valid @ModelAttribute("rejectDto") JobRejectDto dto,
                            BindingResult bindingResult,
                            @AuthenticationPrincipal CustomUserDetails userDetails,
                            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Rejection reason is required (min 5 characters).");
            return "redirect:/admin/jobs/pending";
        }

        Job job = jobService.rejectJob(jobId, userDetails.getUsername(), dto);
        redirectAttributes.addFlashAttribute("successMessage", "Job '" + job.getTitle() + "' has been rejected with feedback sent to the company.");
        return "redirect:/admin/jobs/pending";
    }

    @GetMapping("/jobs")
    public String allJobs(@RequestParam(value = "status", required = false) String statusFilter,
                          @RequestParam(value = "companyId", required = false) String companyIdFilter,
                          Model model) {
        List<Job> jobs = jobService.getAllJobs();

        if (statusFilter != null && !statusFilter.isBlank()) {
            jobs = jobs.stream()
                    .filter(j -> j.getStatus().name().equalsIgnoreCase(statusFilter))
                    .toList();
        }

        if (companyIdFilter != null && !companyIdFilter.isBlank()) {
            jobs = jobs.stream()
                    .filter(j -> j.getCompanyId().equals(companyIdFilter))
                    .toList();
        }

        Map<String, Company> companiesMap = new HashMap<>();
        companyService.getAllCompanies().forEach(c -> companiesMap.put(c.getId(), c));

        model.addAttribute("jobs", jobs);
        model.addAttribute("companiesMap", companiesMap);
        model.addAttribute("companies", companyService.getAllCompanies());
        model.addAttribute("statusFilter", statusFilter);
        model.addAttribute("companyIdFilter", companyIdFilter);
        model.addAttribute("statuses", JobStatus.values());

        return "admin/all-jobs";
    }

    @GetMapping("/companies")
    public String allCompanies(Model model) {
        List<Company> companies = companyService.getAllCompanies();
        Map<String, Long> jobCounts = new HashMap<>();
        for (Company c : companies) {
            jobCounts.put(c.getId(), (long) jobService.getJobsByCompany(c.getId()).size());
        }

        model.addAttribute("companies", companies);
        model.addAttribute("jobCounts", jobCounts);
        return "admin/companies";
    }

    @GetMapping("/companies/new")
    public String newCompanyForm(Model model) {
        if (!model.containsAttribute("companyDto")) {
            model.addAttribute("companyDto", new CompanyCreateDto());
        }
        return "admin/company-form";
    }

    @PostMapping("/companies/new")
    public String createCompany(@Valid @ModelAttribute("companyDto") CompanyCreateDto dto,
                                BindingResult bindingResult,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "admin/company-form";
        }

        try {
            Company company = companyService.createCompany(dto);
            redirectAttributes.addFlashAttribute("successMessage", "Company account created for '" + company.getName() + "'. Login credentials sent to " + company.getContactEmail());
            return "redirect:/admin/companies";
        } catch (BadRequestException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/companies/new";
        }
    }

    @GetMapping("/students")
    public String allStudents(@RequestParam(value = "branch", required = false) String branchFilter,
                             @RequestParam(value = "placed", required = false) Boolean placedFilter,
                             Model model) {
        List<Student> students = studentService.getAllStudents();

        if (branchFilter != null && !branchFilter.isBlank()) {
            students = students.stream()
                    .filter(s -> s.getBranch().equalsIgnoreCase(branchFilter))
                    .toList();
        }

        if (placedFilter != null) {
            students = students.stream()
                    .filter(s -> s.isPlaced() == placedFilter)
                    .toList();
        }

        model.addAttribute("students", students);
        model.addAttribute("branchFilter", branchFilter);
        model.addAttribute("placedFilter", placedFilter);
        model.addAttribute("branches", new String[]{"CSE", "IT", "ECE", "MECH", "CIVIL", "EEE"});

        return "admin/students";
    }

    @GetMapping("/applications")
    public String allApplications(@RequestParam(value = "status", required = false) String statusFilter,
                                 Model model) {
        List<Application> applications = applicationService.getAllApplications();

        if (statusFilter != null && !statusFilter.isBlank()) {
            applications = applications.stream()
                    .filter(a -> a.getStatus().name().equalsIgnoreCase(statusFilter))
                    .toList();
        }

        Map<String, Student> studentsMap = new HashMap<>();
        studentService.getAllStudents().forEach(s -> studentsMap.put(s.getId(), s));

        Map<String, Job> jobsMap = new HashMap<>();
        jobService.getAllJobs().forEach(j -> jobsMap.put(j.getId(), j));

        Map<String, Company> companiesMap = new HashMap<>();
        companyService.getAllCompanies().forEach(c -> companiesMap.put(c.getId(), c));

        model.addAttribute("applications", applications);
        model.addAttribute("studentsMap", studentsMap);
        model.addAttribute("jobsMap", jobsMap);
        model.addAttribute("companiesMap", companiesMap);
        model.addAttribute("statusFilter", statusFilter);

        return "admin/applications";
    }

    @GetMapping("/students/{studentId}/resume")
    public ResponseEntity<Resource> downloadStudentResume(@PathVariable("studentId") String studentId) {
        Student student = studentService.getStudentById(studentId);
        GridFsResource resource = studentService.downloadResume(studentId);

        String filename = student.getResumeFileName() != null ? student.getResumeFileName() : (student.getName().replace(" ", "_") + "_Resume.pdf");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }
}

