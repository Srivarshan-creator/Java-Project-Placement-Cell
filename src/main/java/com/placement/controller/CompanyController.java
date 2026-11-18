package com.placement.controller;

import com.placement.dto.JobPostDto;
import com.placement.dto.OfferDto;
import com.placement.dto.RoundResultDto;
import com.placement.dto.ScheduleRoundDto;
import com.placement.model.Application;
import com.placement.model.ApplicationStatus;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/company")
@PreAuthorize("hasAnyRole('COMPANY', 'PLACEMENT_CELL')")
@RequiredArgsConstructor
@Slf4j
public class CompanyController {

    private final CompanyService companyService;
    private final JobService jobService;
    private final ApplicationService applicationService;
    private final StudentService studentService;

    // ─── Dashboard ───────────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        Company company = companyService.getCompanyByUserId(userDetails.getId());
        List<Job> jobs = jobService.getJobsByCompany(company.getId());
        List<Application> applications = applicationService.getApplicationsForCompany(userDetails.getId());

        long activeJobsCount = jobs.stream()
                .filter(j -> j.getStatus() == com.placement.model.JobStatus.APPROVED).count();
        long totalApplicants = applications.size();
        long shortlistedCount = applications.stream()
                .filter(a -> a.getStatus() == ApplicationStatus.SHORTLISTED
                          || a.getStatus() == ApplicationStatus.IN_PROGRESS).count();
        long offeredCount = applications.stream()
                .filter(a -> a.getStatus() == ApplicationStatus.OFFERED).count();

        model.addAttribute("company", company);
        model.addAttribute("jobsCount", jobs.size());
        model.addAttribute("activeJobsCount", activeJobsCount);
        model.addAttribute("totalApplicants", totalApplicants);
        model.addAttribute("shortlistedCount", shortlistedCount);
        model.addAttribute("offeredCount", offeredCount);
        model.addAttribute("recentJobs", jobs.stream().limit(5).toList());

        return "company/dashboard";
    }

    // ─── Jobs ─────────────────────────────────────────────────────────────────

    @GetMapping("/jobs")
    public String myJobs(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        Company company = companyService.getCompanyByUserId(userDetails.getId());
        List<Job> jobs = jobService.getJobsByCompany(company.getId());

        Map<String, Long> applicantCounts = new HashMap<>();
        for (Job job : jobs) {
            applicantCounts.put(job.getId(),
                    (long) applicationService.getApplicantsForJob(userDetails.getId(), job.getId()).size());
        }

        model.addAttribute("company", company);
        model.addAttribute("jobs", jobs);
        model.addAttribute("applicantCounts", applicantCounts);

        return "company/jobs";
    }

    @GetMapping("/jobs/new")
    public String newJobForm(Model model) {
        if (!model.containsAttribute("jobDto")) {
            model.addAttribute("jobDto", new JobPostDto());
        }
        model.addAttribute("branches", new String[]{"CSE", "IT", "ECE", "MECH", "CIVIL", "EEE"});
        return "company/job-form";
    }

    @PostMapping("/jobs/new")
    public String createJob(@Valid @ModelAttribute("jobDto") JobPostDto dto,
                            BindingResult bindingResult,
                            @AuthenticationPrincipal CustomUserDetails userDetails,
                            RedirectAttributes redirectAttributes,
                            Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("branches", new String[]{"CSE", "IT", "ECE", "MECH", "CIVIL", "EEE"});
            return "company/job-form";
        }

        jobService.createJob(userDetails.getId(), dto);
        redirectAttributes.addFlashAttribute("successMessage",
                "Job posting submitted successfully! It is now pending approval by the Placement Cell.");
        return "redirect:/company/jobs";
    }

    @GetMapping("/jobs/edit/{id}")
    public String editJobForm(@PathVariable("id") String jobId,
                              @AuthenticationPrincipal CustomUserDetails userDetails,
                              Model model) {
        Job job = jobService.getJobById(jobId);
        Company company = companyService.getCompanyByUserId(userDetails.getId());

        if (!job.getCompanyId().equals(company.getId())) {
            return "redirect:/company/jobs";
        }

        if (!model.containsAttribute("jobDto")) {
            JobPostDto dto = JobPostDto.builder()
                    .title(job.getTitle())
                    .description(job.getDescription())
                    .packageLpa(job.getPackageLpa())
                    .deadline(job.getDeadline())
                    .allowedBranches(job.getAllowedBranches())
                    .minCgpa(job.getMinCgpa())
                    .maxBacklogs(job.getMaxBacklogs())
                    .build();
            model.addAttribute("jobDto", dto);
        }

        model.addAttribute("job", job);
        model.addAttribute("branches", new String[]{"CSE", "IT", "ECE", "MECH", "CIVIL", "EEE"});
        model.addAttribute("isEdit", true);
        return "company/job-form";
    }

    @PostMapping("/jobs/edit/{id}")
    public String updateJob(@PathVariable("id") String jobId,
                            @Valid @ModelAttribute("jobDto") JobPostDto dto,
                            BindingResult bindingResult,
                            @AuthenticationPrincipal CustomUserDetails userDetails,
                            RedirectAttributes redirectAttributes,
                            Model model) {
        if (bindingResult.hasErrors()) {
            Job job = jobService.getJobById(jobId);
            model.addAttribute("job", job);
            model.addAttribute("branches", new String[]{"CSE", "IT", "ECE", "MECH", "CIVIL", "EEE"});
            model.addAttribute("isEdit", true);
            return "company/job-form";
        }

        jobService.updateJob(userDetails.getId(), jobId, dto);
        redirectAttributes.addFlashAttribute("successMessage",
                "Job updated! Since changes were made, it has been resubmitted for Placement Cell approval.");
        return "redirect:/company/jobs";
    }

    @PostMapping("/jobs/{id}/close")
    public String closeJob(@PathVariable("id") String jobId,
                           @AuthenticationPrincipal CustomUserDetails userDetails,
                           RedirectAttributes redirectAttributes) {
        jobService.closeJob(userDetails.getId(), jobId);
        redirectAttributes.addFlashAttribute("successMessage", "Job posting closed successfully.");
        return "redirect:/company/jobs";
    }

    // ─── Applicants ───────────────────────────────────────────────────────────

    @GetMapping("/jobs/{id}/applicants")
    public String viewApplicants(@PathVariable("id") String jobId,
                                 @RequestParam(value = "status", required = false) String statusFilter,
                                 @AuthenticationPrincipal CustomUserDetails userDetails,
                                 Model model) {
        Job job = jobService.getJobById(jobId);
        List<Application> allApplicants = applicationService.getApplicantsForJob(userDetails.getId(), jobId);

        // Precompute counts for status tabs
        Map<String, Long> statusCounts = new HashMap<>();
        statusCounts.put("ALL", (long) allApplicants.size());
        for (ApplicationStatus st : ApplicationStatus.values()) {
            long count = allApplicants.stream().filter(a -> a.getStatus() == st).count();
            statusCounts.put(st.name(), count);
        }

        // Filter list for the active tab
        List<Application> filteredApplicants = allApplicants;
        if (statusFilter != null && !statusFilter.isBlank()) {
            filteredApplicants = allApplicants.stream()
                    .filter(a -> a.getStatus().name().equalsIgnoreCase(statusFilter))
                    .toList();
        }

        // Build student lookup map
        Map<String, Student> studentsMap = new HashMap<>();
        for (Application app : allApplicants) {
            try {
                studentsMap.put(app.getStudentId(), studentService.getStudentById(app.getStudentId()));
            } catch (Exception ignored) {
                // student record missing – skip gracefully
            }
        }

        model.addAttribute("job", job);
        model.addAttribute("applicants", filteredApplicants);
        model.addAttribute("totalCount", allApplicants.size());
        model.addAttribute("statusCounts", statusCounts);
        model.addAttribute("studentsMap", studentsMap);
        model.addAttribute("statusFilter", statusFilter != null ? statusFilter : "");
        model.addAttribute("scheduleRoundDto", new ScheduleRoundDto());
        model.addAttribute("roundResultDto", new RoundResultDto());
        model.addAttribute("offerDto", new OfferDto());
        model.addAttribute("batchScheduleDto", new com.placement.dto.ScheduleInterviewDto());

        return "company/applicants";
    }

    // ─── Batch operations ─────────────────────────────────────────────────────

    @PostMapping("/jobs/{jobId}/applicants/batch-status")
    public String batchUpdateStatus(@PathVariable("jobId") String jobId,
                                    @RequestParam("status") ApplicationStatus status,
                                    @RequestParam("applicationIds") List<String> applicationIds,
                                    @RequestParam(value = "currentFilter", required = false) String currentFilter,
                                    @AuthenticationPrincipal CustomUserDetails userDetails,
                                    RedirectAttributes redirectAttributes) {
        if (applicationIds == null || applicationIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "No applicants were selected.");
            return "redirect:/company/jobs/" + jobId + "/applicants"
                   + (currentFilter != null && !currentFilter.isBlank() ? "?status=" + currentFilter : "");
        }

        int updated = 0;
        for (String appId : applicationIds) {
            try {
                applicationService.updateApplicationStatus(userDetails.getId(), appId, status);
                updated++;
            } catch (Exception ignored) {
                // skip invalid transitions silently
            }
        }
        redirectAttributes.addFlashAttribute("successMessage",
                "Successfully updated " + updated + " applicant(s) to " + status.name() + ".");
        return "redirect:/company/jobs/" + jobId + "/applicants"
               + (currentFilter != null && !currentFilter.isBlank() ? "?status=" + currentFilter : "");
    }

    @PostMapping("/jobs/{jobId}/applicants/batch-schedule")
    public String batchScheduleInterviews(@PathVariable("jobId") String jobId,
                                          @ModelAttribute("batchScheduleDto") com.placement.dto.ScheduleInterviewDto dto,
                                          @RequestParam(value = "currentFilter", required = false) String currentFilter,
                                          @AuthenticationPrincipal CustomUserDetails userDetails,
                                          RedirectAttributes redirectAttributes) {
        try {
            List<Application> updated = applicationService.scheduleBatchInterviews(userDetails.getId(), dto);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Successfully scheduled interviews for " + updated.size()
                    + " applicant(s) and sent email calendar invites (.ics)!");
        } catch (com.placement.exception.BadRequestException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (Exception ex) {
            log.error("Batch schedule error", ex);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error scheduling interviews: " + ex.getMessage());
        }
        return "redirect:/company/jobs/" + jobId + "/applicants"
               + (currentFilter != null && !currentFilter.isBlank() ? "?status=" + currentFilter : "");
    }

    // ─── Explicit applicant actions ──────────────────────────────────────────

    @PostMapping("/applications/{id}/shortlist")
    public String shortlistApplicant(@PathVariable("id") String applicationId,
                                     @RequestParam("jobId") String jobId,
                                     @RequestParam(value = "currentFilter", required = false) String currentFilter,
                                     @AuthenticationPrincipal CustomUserDetails userDetails,
                                     RedirectAttributes redirectAttributes) {
        try {
            applicationService.shortlistApplicant(userDetails.getId(), applicationId);
            redirectAttributes.addFlashAttribute("successMessage", "Applicant successfully shortlisted!");
        } catch (com.placement.exception.BadRequestException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (Exception ex) {
            log.error("Shortlist error", ex);
            redirectAttributes.addFlashAttribute("errorMessage", "Error shortlisting applicant: " + ex.getMessage());
        }
        return "redirect:/company/jobs/" + jobId + "/applicants"
               + (currentFilter != null && !currentFilter.isBlank() ? "?status=" + currentFilter : "");
    }

    @PostMapping("/applications/{id}/reject")
    public String rejectApplicant(@PathVariable("id") String applicationId,
                                  @RequestParam("jobId") String jobId,
                                  @RequestParam(value = "currentFilter", required = false) String currentFilter,
                                  @AuthenticationPrincipal CustomUserDetails userDetails,
                                  RedirectAttributes redirectAttributes) {
        try {
            applicationService.rejectApplicant(userDetails.getId(), applicationId);
            redirectAttributes.addFlashAttribute("successMessage", "Applicant rejected.");
        } catch (com.placement.exception.BadRequestException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (Exception ex) {
            log.error("Reject error", ex);
            redirectAttributes.addFlashAttribute("errorMessage", "Error rejecting applicant: " + ex.getMessage());
        }
        return "redirect:/company/jobs/" + jobId + "/applicants"
               + (currentFilter != null && !currentFilter.isBlank() ? "?status=" + currentFilter : "");
    }

    @PostMapping("/applications/{id}/advance")
    public String advanceRound(@PathVariable("id") String applicationId,
                               @RequestParam("jobId") String jobId,
                               @RequestParam(value = "currentFilter", required = false) String currentFilter,
                               @AuthenticationPrincipal CustomUserDetails userDetails,
                               RedirectAttributes redirectAttributes) {
        try {
            applicationService.advanceRound(userDetails.getId(), applicationId);
            redirectAttributes.addFlashAttribute("successMessage", "Candidate successfully advanced to the next round!");
        } catch (com.placement.exception.BadRequestException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (Exception ex) {
            log.error("Advance round error", ex);
            redirectAttributes.addFlashAttribute("errorMessage", "Error advancing round: " + ex.getMessage());
        }
        return "redirect:/company/jobs/" + jobId + "/applicants"
               + (currentFilter != null && !currentFilter.isBlank() ? "?status=" + currentFilter : "");
    }

    @PostMapping("/applications/batch-shortlist")
    public String batchShortlist(@RequestParam("jobId") String jobId,
                                 @RequestParam("applicationIds") List<String> applicationIds,
                                 @RequestParam(value = "currentFilter", required = false) String currentFilter,
                                 @AuthenticationPrincipal CustomUserDetails userDetails,
                                 RedirectAttributes redirectAttributes) {
        return batchUpdateStatus(jobId, ApplicationStatus.SHORTLISTED, applicationIds, currentFilter, userDetails, redirectAttributes);
    }

    @PostMapping("/applications/batch-reject")
    public String batchReject(@RequestParam("jobId") String jobId,
                              @RequestParam("applicationIds") List<String> applicationIds,
                              @RequestParam(value = "currentFilter", required = false) String currentFilter,
                              @AuthenticationPrincipal CustomUserDetails userDetails,
                              RedirectAttributes redirectAttributes) {
        return batchUpdateStatus(jobId, ApplicationStatus.REJECTED, applicationIds, currentFilter, userDetails, redirectAttributes);
    }

    @PostMapping("/applications/batch-schedule")
    public String batchScheduleGlobal(@RequestParam("jobId") String jobId,
                                      @ModelAttribute("batchScheduleDto") com.placement.dto.ScheduleInterviewDto dto,
                                      @RequestParam(value = "currentFilter", required = false) String currentFilter,
                                      @AuthenticationPrincipal CustomUserDetails userDetails,
                                      RedirectAttributes redirectAttributes) {
        return batchScheduleInterviews(jobId, dto, currentFilter, userDetails, redirectAttributes);
    }

    // ─── Single-applicant actions ─────────────────────────────────────────────

    @PostMapping("/applications/{id}/status")
    public String updateStatus(@PathVariable("id") String applicationId,
                               @RequestParam("status") ApplicationStatus status,
                               @RequestParam("jobId") String jobId,
                               @RequestParam(value = "currentFilter", required = false) String currentFilter,
                               @AuthenticationPrincipal CustomUserDetails userDetails,
                               RedirectAttributes redirectAttributes) {
        try {
            applicationService.updateApplicationStatus(userDetails.getId(), applicationId, status);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Applicant status updated to " + status.name() + ".");
        } catch (com.placement.exception.BadRequestException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (Exception ex) {
            log.error("Update status error", ex);
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating status: " + ex.getMessage());
        }
        return "redirect:/company/jobs/" + jobId + "/applicants"
               + (currentFilter != null && !currentFilter.isBlank() ? "?status=" + currentFilter : "");
    }

    @PostMapping("/applications/{id}/rounds")
    public String scheduleRound(@PathVariable("id") String applicationId,
                                @RequestParam("jobId") String jobId,
                                @RequestParam(value = "currentFilter", required = false) String currentFilter,
                                @Valid @ModelAttribute("scheduleRoundDto") ScheduleRoundDto dto,
                                BindingResult bindingResult,
                                @AuthenticationPrincipal CustomUserDetails userDetails,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error scheduling round: " + bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/company/jobs/" + jobId + "/applicants"
                   + (currentFilter != null && !currentFilter.isBlank() ? "?status=" + currentFilter : "");
        }

        try {
            applicationService.scheduleRound(userDetails.getId(), applicationId, dto);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Round " + dto.getRoundNumber() + " (" + dto.getName()
                    + ") scheduled and student notified with .ics calendar invite!");
        } catch (com.placement.exception.BadRequestException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (Exception ex) {
            log.error("Schedule round error", ex);
            redirectAttributes.addFlashAttribute("errorMessage", "Error scheduling round: " + ex.getMessage());
        }
        return "redirect:/company/jobs/" + jobId + "/applicants"
               + (currentFilter != null && !currentFilter.isBlank() ? "?status=" + currentFilter : "");
    }

    @PostMapping("/applications/{id}/round-result")
    public String submitRoundResult(@PathVariable("id") String applicationId,
                                    @RequestParam("jobId") String jobId,
                                    @RequestParam(value = "currentFilter", required = false) String currentFilter,
                                    @Valid @ModelAttribute("roundResultDto") RoundResultDto dto,
                                    BindingResult bindingResult,
                                    @AuthenticationPrincipal CustomUserDetails userDetails,
                                    RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error submitting result: " + bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/company/jobs/" + jobId + "/applicants"
                   + (currentFilter != null && !currentFilter.isBlank() ? "?status=" + currentFilter : "");
        }

        try {
            applicationService.submitRoundResult(userDetails.getId(), applicationId, dto);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Round " + dto.getRoundNumber() + " result posted and student notified via email!");
        } catch (com.placement.exception.BadRequestException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (Exception ex) {
            log.error("Submit result error", ex);
            redirectAttributes.addFlashAttribute("errorMessage", "Error submitting result: " + ex.getMessage());
        }
        return "redirect:/company/jobs/" + jobId + "/applicants"
               + (currentFilter != null && !currentFilter.isBlank() ? "?status=" + currentFilter : "");
    }

    @PostMapping("/applications/{id}/offer")
    public String makeOffer(@PathVariable("id") String applicationId,
                            @RequestParam("jobId") String jobId,
                            @RequestParam(value = "currentFilter", required = false) String currentFilter,
                            @Valid @ModelAttribute("offerDto") OfferDto dto,
                            BindingResult bindingResult,
                            @AuthenticationPrincipal CustomUserDetails userDetails,
                            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error making offer: " + bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/company/jobs/" + jobId + "/applicants"
                   + (currentFilter != null && !currentFilter.isBlank() ? "?status=" + currentFilter : "");
        }

        try {
            applicationService.makeOffer(userDetails.getId(), applicationId, dto);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Placement offer of " + dto.getOfferLpa() + " LPA made! Student status updated to PLACED.");
        } catch (com.placement.exception.BadRequestException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (Exception ex) {
            log.error("Make offer error", ex);
            redirectAttributes.addFlashAttribute("errorMessage", "Error making offer: " + ex.getMessage());
        }
        return "redirect:/company/jobs/" + jobId + "/applicants"
               + (currentFilter != null && !currentFilter.isBlank() ? "?status=" + currentFilter : "");
    }

    // ─── Resume download ──────────────────────────────────────────────────────

    @GetMapping("/students/{studentId}/resume")
    public ResponseEntity<Resource> downloadStudentResume(@PathVariable("studentId") String studentId) {
        Student student = studentService.getStudentById(studentId);
        GridFsResource resource = studentService.downloadResume(studentId);

        String filename = student.getResumeFileName() != null
                ? student.getResumeFileName()
                : (student.getName().replace(" ", "_") + "_Resume.pdf");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }
}
