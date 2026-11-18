package com.placement.service;

import com.placement.dto.OfferDto;
import com.placement.dto.RoundResultDto;
import com.placement.dto.ScheduleInterviewDto;
import com.placement.dto.ScheduleRoundDto;
import com.placement.exception.BadRequestException;
import com.placement.exception.DuplicateApplicationException;
import com.placement.exception.EligibilityException;
import com.placement.exception.ResourceNotFoundException;
import com.placement.exception.SingleOfferViolationException;
import com.placement.exception.UnauthorizedActionException;
import com.placement.model.Application;
import com.placement.model.ApplicationStatus;
import com.placement.model.AuditLog;
import com.placement.model.Company;
import com.placement.model.Interview;
import com.placement.model.InterviewStatus;
import com.placement.model.Job;
import com.placement.model.Notification;
import com.placement.model.Role;
import com.placement.model.Round;
import com.placement.model.RoundResult;
import com.placement.model.Student;
import com.placement.model.User;
import com.placement.repository.ApplicationRepository;
import com.placement.repository.AuditLogRepository;
import com.placement.repository.InterviewRepository;
import com.placement.repository.NotificationRepository;
import com.placement.repository.StudentRepository;
import com.placement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationService {

    private static final ZoneId KOLKATA_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private final ApplicationRepository applicationRepository;
    private final StudentService studentService;
    private final StudentRepository studentRepository;
    private final JobService jobService;
    private final CompanyService companyService;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final InterviewRepository interviewRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogRepository auditLogRepository;

    // ─── Apply to Job ─────────────────────────────────────────────────────────

    public Application applyToJob(String studentUserId, String jobId) {
        Student student = studentService.getStudentByUserId(studentUserId);
        Job job = jobService.getJobById(jobId);

        // 1. Single offer policy check
        if (studentService.isSingleOfferPolicy() && student.isPlaced()) {
            throw new SingleOfferViolationException("You have already secured a placement offer. Under the single-offer policy, you cannot apply to more jobs.");
        }

        // 2. Eligibility validation
        if (!studentService.isStudentEligible(student, job)) {
            throw new EligibilityException("You do not meet the eligibility criteria (CGPA, backlogs, branch, or deadline) for this job.");
        }

        // 3. Duplicate check
        if (applicationRepository.existsByStudentIdAndJobId(student.getId(), job.getId())) {
            throw new DuplicateApplicationException("You have already applied for this job posting.");
        }

        Company company = companyService.getCompanyById(job.getCompanyId());

        Application application = Application.builder()
                .studentId(student.getId())
                .jobId(job.getId())
                .companyId(company.getId())
                .branch(student.getBranch())
                .status(ApplicationStatus.APPLIED)
                .rounds(new ArrayList<>())
                .appliedAt(LocalDateTime.now(KOLKATA_ZONE))
                .build();

        Application savedApp = applicationRepository.save(application);

        // Audit log
        recordAudit(studentUserId, Role.STUDENT.name(), "APPLIED", "APPLICATION", savedApp.getId(),
                "Student " + student.getName() + " applied for " + job.getTitle() + " at " + company.getName());

        // In-app notification
        createInAppNotification(student.getUserId(),
                "Your application for " + job.getTitle() + " at " + company.getName() + " has been received.");

        // Async failure-safe email
        User user = userRepository.findById(student.getUserId()).orElse(null);
        if (user != null) {
            emailService.sendApplicationConfirmation(user.getId(), user.getEmail(), student.getName(), job.getTitle(), company.getName());
        }

        return savedApp;
    }

    // ─── Query Applications ───────────────────────────────────────────────────

    public List<Application> getApplicationsByStudentUser(String studentUserId) {
        Student student = studentService.getStudentByUserId(studentUserId);
        return applicationRepository.findByStudentId(student.getId());
    }

    public List<Application> getApplicantsForJob(String actorUserId, String jobId) {
        Job job = jobService.getJobById(jobId);
        verifyOwnershipOrAdmin(actorUserId, job.getCompanyId());
        return applicationRepository.findByJobId(jobId);
    }

    public List<Application> getApplicationsForCompany(String companyUserId) {
        Company company = companyService.getCompanyByUserId(companyUserId);
        return applicationRepository.findByCompanyId(company.getId());
    }

    public List<Application> getAllApplications() {
        return applicationRepository.findAll();
    }

    public Application getApplicationById(String applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + applicationId));
    }

    // ─── Status Transitions ───────────────────────────────────────────────────

    /**
     * Enforces strict server-side transition state machine:
     * APPLIED -> SHORTLISTED or REJECTED
     * SHORTLISTED -> IN_PROGRESS or REJECTED
     * IN_PROGRESS -> OFFERED or REJECTED
     * OFFERED and REJECTED are final.
     */
    public Application updateApplicationStatus(String actorUserId, String applicationId, ApplicationStatus newStatus) {
        Application application = getApplicationById(applicationId);
        verifyOwnershipOrAdmin(actorUserId, application.getCompanyId());

        ApplicationStatus current = application.getStatus();

        if (current == ApplicationStatus.OFFERED || current == ApplicationStatus.REJECTED) {
            throw new BadRequestException("Application is already " + current.name() + " and cannot be transitioned further.");
        }

        if (current == ApplicationStatus.APPLIED) {
            if (newStatus != ApplicationStatus.SHORTLISTED && newStatus != ApplicationStatus.REJECTED) {
                throw new BadRequestException("Applications in APPLIED status can only be transitioned to SHORTLISTED or REJECTED.");
            }
        } else if (current == ApplicationStatus.SHORTLISTED) {
            if (newStatus != ApplicationStatus.IN_PROGRESS && newStatus != ApplicationStatus.REJECTED) {
                throw new BadRequestException("Applications in SHORTLISTED status can only be transitioned to IN_PROGRESS or REJECTED.");
            }
        } else if (current == ApplicationStatus.IN_PROGRESS) {
            if (newStatus != ApplicationStatus.OFFERED && newStatus != ApplicationStatus.REJECTED) {
                throw new BadRequestException("Applications in IN_PROGRESS status can only be transitioned to OFFERED or REJECTED.");
            }
        }

        application.setStatus(newStatus);
        Application saved = applicationRepository.save(application);

        // Audit & Notification & Email
        Student student = studentRepository.findById(saved.getStudentId()).orElse(null);
        Job job = jobService.getJobById(saved.getJobId());
        Company company = companyService.getCompanyById(saved.getCompanyId());
        User actor = userRepository.findById(actorUserId).orElse(null);

        String actorRole = actor != null ? actor.getRole().name() : "COMPANY";
        recordAudit(actorUserId, actorRole, "STATUS_UPDATE_" + newStatus.name(), "APPLICATION", saved.getId(),
                "Status changed from " + current.name() + " to " + newStatus.name());

        if (student != null) {
            String msg = "Your application status for " + job.getTitle() + " at " + company.getName() + " is now " + newStatus.name() + ".";
            createInAppNotification(student.getUserId(), msg);

            userRepository.findById(student.getUserId()).ifPresent(u -> {
                try {
                    emailService.sendEmail(u.getId(), u.getEmail(), "Application Status Update: " + job.getTitle(), "<p>" + msg + "</p>");
                } catch (Exception ex) {
                    log.warn("Failed sending status change email to {}", u.getEmail(), ex);
                }
            });
        }

        return saved;
    }

    public Application shortlistApplicant(String actorUserId, String applicationId) {
        return updateApplicationStatus(actorUserId, applicationId, ApplicationStatus.SHORTLISTED);
    }

    public Application rejectApplicant(String actorUserId, String applicationId) {
        return updateApplicationStatus(actorUserId, applicationId, ApplicationStatus.REJECTED);
    }

    // ─── Single Round Scheduling ──────────────────────────────────────────────

    public Application scheduleRound(String actorUserId, String applicationId, ScheduleRoundDto dto) {
        Application application = getApplicationById(applicationId);
        verifyOwnershipOrAdmin(actorUserId, application.getCompanyId());

        if (application.getStatus() == ApplicationStatus.REJECTED || application.getStatus() == ApplicationStatus.OFFERED) {
            throw new BadRequestException("Cannot schedule interview for an applicant that is already " + application.getStatus());
        }

        LocalDateTime now = LocalDateTime.now(KOLKATA_ZONE);
        if (dto.getScheduledAt().isBefore(now)) {
            throw new BadRequestException("Interview start time cannot be in the past (" + dto.getScheduledAt().format(TIME_FORMATTER) + ").");
        }

        LocalDateTime endDateTime = dto.getScheduledAt().plusMinutes(45);

        // Conflict check
        List<Interview> clashes = interviewRepository
                .findByStudentIdAndStartDateTimeLessThanAndEndDateTimeGreaterThan(application.getStudentId(), endDateTime, dto.getScheduledAt());
        if (!clashes.isEmpty()) {
            Student s = studentService.getStudentById(application.getStudentId());
            throw new BadRequestException("Schedule conflict detected for student " + s.getName() + " at " + dto.getScheduledAt().format(TIME_FORMATTER));
        }

        Round round = Round.builder()
                .roundNumber(dto.getRoundNumber())
                .name(dto.getName().trim())
                .scheduledAt(dto.getScheduledAt())
                .result(RoundResult.PENDING)
                .notes(dto.getNotes() != null ? dto.getNotes().trim() : null)
                .build();

        List<Round> rounds = application.getRounds();
        rounds.removeIf(r -> r.getRoundNumber().equals(dto.getRoundNumber()));
        rounds.add(round);

        application.setStatus(ApplicationStatus.IN_PROGRESS);
        Application saved = applicationRepository.save(application);

        // Keep interviews collection in sync
        Interview interview = interviewRepository
                .findByApplicationIdAndRoundNumber(application.getId(), dto.getRoundNumber())
                .orElse(Interview.builder()
                        .applicationId(application.getId())
                        .studentId(application.getStudentId())
                        .jobId(application.getJobId())
                        .companyId(application.getCompanyId())
                        .roundNumber(dto.getRoundNumber())
                        .build());

        interview.setRoundType("Technical");
        interview.setRoundName(dto.getName().trim());
        interview.setStartDateTime(dto.getScheduledAt());
        interview.setDurationMinutes(45);
        interview.setEndDateTime(endDateTime);
        interview.setMode("ONLINE");
        interview.setVenueOrLink("Online Video Call");
        interview.setInstructions(dto.getNotes() != null ? dto.getNotes().trim() : "");
        interview.setStatus(InterviewStatus.SCHEDULED);
        interview.setResult(RoundResult.PENDING);
        interview.setUpdatedAt(LocalDateTime.now(KOLKATA_ZONE));

        Interview savedInterview = interviewRepository.save(interview);

        // Audit, Notification, ICS Email
        Student student = studentRepository.findById(application.getStudentId()).orElse(null);
        Job job = jobService.getJobById(application.getJobId());
        Company company = companyService.getCompanyById(application.getCompanyId());

        recordAudit(actorUserId, "RECRUITER", "SCHEDULE_ROUND", "APPLICATION", saved.getId(),
                "Scheduled Round " + dto.getRoundNumber() + " (" + dto.getName() + ") for " + (student != null ? student.getName() : "student"));

        if (student != null) {
            String timeStr = dto.getScheduledAt().format(TIME_FORMATTER);
            createInAppNotification(student.getUserId(),
                    "Interview scheduled for " + job.getTitle() + " (" + dto.getName() + ") at " + timeStr + ".");

            userRepository.findById(student.getUserId()).ifPresent(user -> {
                byte[] icsBytes = generateIcsBytes(round.getName(), company.getName(), dto.getScheduledAt(), endDateTime, "Online");
                emailService.sendRoundScheduledWithIcs(user.getId(), user.getEmail(), student.getName(), job.getTitle(), company.getName(), dto.getName(), timeStr, "interview.ics", icsBytes);
            });
        }

        return saved;
    }

    // ─── Batch Interview Scheduling with Two-Phase Validation ─────────────────

    public List<Application> scheduleBatchInterviews(String actorUserId, ScheduleInterviewDto dto) {
        if (dto.getApplicationIds() == null || dto.getApplicationIds().isEmpty()) {
            throw new BadRequestException("Please select at least one applicant.");
        }

        LocalDateTime now = LocalDateTime.now(KOLKATA_ZONE);
        if (dto.getStartDateTime().isBefore(now)) {
            throw new BadRequestException("Interview start time cannot be in the past (" + dto.getStartDateTime().format(TIME_FORMATTER) + ").");
        }

        int duration = dto.getDurationMinutes() != null && dto.getDurationMinutes() > 0 ? dto.getDurationMinutes() : 45;
        int breakMins = dto.getBreakMinutes() != null && dto.getBreakMinutes() >= 0 ? dto.getBreakMinutes() : 10;
        boolean isAutoSlots = "AUTO_SLOTS".equalsIgnoreCase(dto.getSlotMode());

        // PHASE 1: VALIDATE ENTIRE BATCH (DO NOT SAVE ANYTHING YET)
        List<Application> appsToSchedule = new ArrayList<>();
        List<LocalDateTime> slotStartTimes = new ArrayList<>();
        List<LocalDateTime> slotEndTimes = new ArrayList<>();

        LocalDateTime currentSlot = dto.getStartDateTime();

        for (String appId : dto.getApplicationIds()) {
            Application app = getApplicationById(appId);
            verifyOwnershipOrAdmin(actorUserId, app.getCompanyId());

            if (app.getStatus() == ApplicationStatus.REJECTED || app.getStatus() == ApplicationStatus.OFFERED) {
                Student s = studentRepository.findById(app.getStudentId()).orElse(null);
                throw new BadRequestException("Cannot schedule interview for applicant "
                        + (s != null ? s.getName() : appId) + " because they are already " + app.getStatus().name() + ".");
            }

            LocalDateTime currentEnd = currentSlot.plusMinutes(duration);

            // Check student clash in DB
            List<Interview> studentClashes = interviewRepository
                    .findByStudentIdAndStartDateTimeLessThanAndEndDateTimeGreaterThan(app.getStudentId(), currentEnd, currentSlot);
            if (!studentClashes.isEmpty()) {
                Student s = studentService.getStudentById(app.getStudentId());
                throw new BadRequestException("Schedule conflict detected for student " + s.getName()
                        + " at " + currentSlot.format(TIME_FORMATTER) + ". They already have another interview scheduled.");
            }

            // Check company/panel clash in DB
            if (dto.getPanelNames() != null && !dto.getPanelNames().isBlank()) {
                List<Interview> companyClashes = interviewRepository
                        .findByCompanyIdAndStartDateTimeLessThanAndEndDateTimeGreaterThan(app.getCompanyId(), currentEnd, currentSlot);
                if (!companyClashes.isEmpty() && isAutoSlots) {
                    // Check if panel overlap exists
                    for (Interview clash : companyClashes) {
                        if (clash.getPanelMembers().stream().anyMatch(p -> dto.getPanelNames().contains(p))) {
                            throw new BadRequestException("Interview panel conflict detected at "
                                    + currentSlot.format(TIME_FORMATTER) + " for panel member(s): " + dto.getPanelNames());
                        }
                    }
                }
            }

            appsToSchedule.add(app);
            slotStartTimes.add(currentSlot);
            slotEndTimes.add(currentEnd);

            if (isAutoSlots) {
                currentSlot = currentEnd.plusMinutes(breakMins);
            }
        }

        // PHASE 2: ATOMIC PERSISTENCE (ALL VALIDATED, SAVE ALL)
        List<Application> resultApps = new ArrayList<>();
        List<String> panelList = dto.getPanelNames() != null && !dto.getPanelNames().isBlank()
                ? Arrays.stream(dto.getPanelNames().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()
                : new ArrayList<>();

        for (int i = 0; i < appsToSchedule.size(); i++) {
            Application app = appsToSchedule.get(i);
            LocalDateTime start = slotStartTimes.get(i);
            LocalDateTime end = slotEndTimes.get(i);

            Round round = Round.builder()
                    .roundNumber(dto.getRoundNumber())
                    .name(dto.getRoundName().trim())
                    .scheduledAt(start)
                    .result(RoundResult.PENDING)
                    .notes(dto.getInstructions())
                    .build();

            app.getRounds().removeIf(r -> r.getRoundNumber().equals(dto.getRoundNumber()));
            app.getRounds().add(round);
            app.setStatus(ApplicationStatus.IN_PROGRESS);
            Application savedApp = applicationRepository.save(app);
            resultApps.add(savedApp);

            // Sync with interviews collection
            Interview interview = interviewRepository
                    .findByApplicationIdAndRoundNumber(app.getId(), dto.getRoundNumber())
                    .orElse(Interview.builder()
                            .applicationId(app.getId())
                            .studentId(app.getStudentId())
                            .jobId(app.getJobId())
                            .companyId(app.getCompanyId())
                            .roundNumber(dto.getRoundNumber())
                            .build());

            interview.setRoundType(dto.getRoundType());
            interview.setRoundName(dto.getRoundName().trim());
            interview.setStartDateTime(start);
            interview.setDurationMinutes(duration);
            interview.setEndDateTime(end);
            interview.setMode(dto.getMode());
            interview.setVenueOrLink(dto.getVenueOrLink());
            interview.setPanelMembers(panelList);
            interview.setInstructions(dto.getInstructions());
            interview.setStatus(InterviewStatus.SCHEDULED);
            interview.setResult(RoundResult.PENDING);
            interview.setUpdatedAt(LocalDateTime.now(KOLKATA_ZONE));
            interviewRepository.save(interview);

            // Notifications & Async Email with ICS
            Student student = studentRepository.findById(app.getStudentId()).orElse(null);
            Job job = jobService.getJobById(app.getJobId());
            Company company = companyService.getCompanyById(app.getCompanyId());

            recordAudit(actorUserId, "RECRUITER", "BATCH_INTERVIEW_SCHEDULED", "APPLICATION", app.getId(),
                    "Scheduled " + dto.getRoundType() + " interview at " + start.format(TIME_FORMATTER));

            if (student != null) {
                String timeStr = start.format(TIME_FORMATTER);
                createInAppNotification(student.getUserId(),
                        "Interview scheduled for " + job.getTitle() + " (" + dto.getRoundName() + ") on " + timeStr + ".");

                userRepository.findById(student.getUserId()).ifPresent(user -> {
                    byte[] icsBytes = generateIcsBytes(round.getName(), company.getName(), start, end, dto.getVenueOrLink());
                    emailService.sendRoundScheduledWithIcs(user.getId(), user.getEmail(), student.getName(),
                            job.getTitle(), company.getName(), round.getName(), timeStr, "interview.ics", icsBytes);
                });
            }
        }

        return resultApps;
    }

    // ─── Round Result Submission ──────────────────────────────────────────────

    public Application submitRoundResult(String actorUserId, String applicationId, RoundResultDto dto) {
        Application application = getApplicationById(applicationId);
        verifyOwnershipOrAdmin(actorUserId, application.getCompanyId());

        if (application.getStatus() == ApplicationStatus.REJECTED || application.getStatus() == ApplicationStatus.OFFERED) {
            throw new BadRequestException("Cannot record round results for an application that is already " + application.getStatus());
        }

        Round matchingRound = application.getRounds().stream()
                .filter(r -> r.getRoundNumber().equals(dto.getRoundNumber()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Round " + dto.getRoundNumber() + " not found on this application."));

        matchingRound.setResult(dto.getResult());
        if (dto.getScore() != null) {
            matchingRound.setScore(dto.getScore());
        }
        if (dto.getFeedback() != null && !dto.getFeedback().isBlank()) {
            matchingRound.setFeedback(dto.getFeedback().trim());
        }
        if (dto.getNotes() != null && !dto.getNotes().isBlank()) {
            matchingRound.setNotes(dto.getNotes().trim());
        }

        if (dto.getResult() == RoundResult.FAILED) {
            application.setStatus(ApplicationStatus.REJECTED);
        } else if (dto.getResult() == RoundResult.CLEARED) {
            application.setStatus(ApplicationStatus.IN_PROGRESS);
        }

        Application saved = applicationRepository.save(application);

        // Sync with interview document
        interviewRepository.findByApplicationIdAndRoundNumber(application.getId(), dto.getRoundNumber())
                .ifPresent(interview -> {
                    interview.setResult(dto.getResult());
                    interview.setScore(dto.getScore());
                    interview.setFeedback(dto.getFeedback());
                    interview.setStatus(dto.getResult() == RoundResult.FAILED || dto.getResult() == RoundResult.CLEARED
                            ? InterviewStatus.COMPLETED : InterviewStatus.SCHEDULED);
                    interview.setUpdatedAt(LocalDateTime.now(KOLKATA_ZONE));
                    interviewRepository.save(interview);
                });

        // Audit & Notification & Email
        Student student = studentRepository.findById(application.getStudentId()).orElse(null);
        Job job = jobService.getJobById(application.getJobId());

        recordAudit(actorUserId, "RECRUITER", "ROUND_RESULT_" + dto.getResult().name(), "APPLICATION", saved.getId(),
                "Round " + dto.getRoundNumber() + " result: " + dto.getResult().name() + (dto.getScore() != null ? " (Score: " + dto.getScore() + ")" : ""));

        if (student != null) {
            createInAppNotification(student.getUserId(),
                    "Round " + dto.getRoundNumber() + " (" + matchingRound.getName() + ") result posted: " + dto.getResult().name());

            userRepository.findById(student.getUserId()).ifPresent(user -> {
                emailService.sendRoundResult(user.getId(), user.getEmail(), student.getName(), job.getTitle(), matchingRound.getName(), dto.getResult().name());
            });
        }

        return saved;
    }

    // ─── Advance Round ────────────────────────────────────────────────────────

    public Application advanceRound(String actorUserId, String applicationId) {
        Application application = getApplicationById(applicationId);
        verifyOwnershipOrAdmin(actorUserId, application.getCompanyId());

        if (application.getStatus() == ApplicationStatus.REJECTED || application.getStatus() == ApplicationStatus.OFFERED) {
            throw new BadRequestException("Cannot advance round for an applicant that is already " + application.getStatus());
        }

        application.setStatus(ApplicationStatus.IN_PROGRESS);
        Application saved = applicationRepository.save(application);

        Student student = studentRepository.findById(saved.getStudentId()).orElse(null);
        Job job = jobService.getJobById(saved.getJobId());
        Company company = companyService.getCompanyById(saved.getCompanyId());

        recordAudit(actorUserId, "RECRUITER", "ADVANCE_ROUND", "APPLICATION", saved.getId(), "Advanced to next stage.");

        if (student != null) {
            createInAppNotification(student.getUserId(),
                    "Congratulations! You have advanced to the next stage for " + job.getTitle() + " at " + company.getName());
        }

        return saved;
    }

    // ─── Make Offer ───────────────────────────────────────────────────────────

    public Application makeOffer(String actorUserId, String applicationId, OfferDto dto) {
        Application application = getApplicationById(applicationId);
        verifyOwnershipOrAdmin(actorUserId, application.getCompanyId());

        if (application.getStatus() != ApplicationStatus.IN_PROGRESS) {
            throw new BadRequestException("Offers can only be extended to applicants currently IN_PROGRESS.");
        }

        Student student = studentRepository.findById(application.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException("Student not found for application"));

        Job job = jobService.getJobById(application.getJobId());
        Company company = companyService.getCompanyById(application.getCompanyId());

        application.setStatus(ApplicationStatus.OFFERED);
        application.setOfferLpa(dto.getOfferLpa());
        Application saved = applicationRepository.save(application);

        // Update student placement status
        student.setPlaced(true);
        student.setPlacedAtJobId(job.getId());
        student.setOfferLpa(dto.getOfferLpa());
        student.setUpdatedAt(LocalDateTime.now(KOLKATA_ZONE));
        studentRepository.save(student);

        recordAudit(actorUserId, "RECRUITER", "OFFER_MADE", "APPLICATION", saved.getId(),
                "Offered " + dto.getOfferLpa() + " LPA to " + student.getName() + " for " + job.getTitle());

        createInAppNotification(student.getUserId(),
                "🎉 Congratulations! You have received a placement offer from " + company.getName() + " of " + dto.getOfferLpa() + " LPA!");

        userRepository.findById(student.getUserId()).ifPresent(user -> {
            emailService.sendOfferMade(user.getId(), user.getEmail(), student.getName(), job.getTitle(), company.getName(), dto.getOfferLpa());
        });

        return saved;
    }

    // ─── Student Interview Actions ────────────────────────────────────────────

    public Interview confirmInterview(String studentUserId, String interviewId) {
        Student student = studentService.getStudentByUserId(studentUserId);
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found: " + interviewId));

        if (!interview.getStudentId().equals(student.getId())) {
            throw new UnauthorizedActionException("You can only confirm your own interviews.");
        }

        interview.setStatus(InterviewStatus.CONFIRMED);
        interview.setStudentConfirmation("CONFIRMED");
        interview.setUpdatedAt(LocalDateTime.now(KOLKATA_ZONE));
        Interview saved = interviewRepository.save(interview);

        recordAudit(studentUserId, "STUDENT", "INTERVIEW_CONFIRMED", "INTERVIEW", saved.getId(),
                "Student " + student.getName() + " confirmed interview.");

        return saved;
    }

    public Interview requestReschedule(String studentUserId, String interviewId, String reason, LocalDateTime preferredTime) {
        Student student = studentService.getStudentByUserId(studentUserId);
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found: " + interviewId));

        if (!interview.getStudentId().equals(student.getId())) {
            throw new UnauthorizedActionException("You can only request reschedule for your own interviews.");
        }

        interview.setStatus(InterviewStatus.RESCHEDULE_REQUESTED);
        interview.setStudentConfirmation("RESCHEDULE_REQUESTED");
        interview.setRescheduleReason(reason);
        interview.setPreferredRescheduleTime(preferredTime);
        interview.setUpdatedAt(LocalDateTime.now(KOLKATA_ZONE));
        Interview saved = interviewRepository.save(interview);

        recordAudit(studentUserId, "STUDENT", "INTERVIEW_RESCHEDULE_REQUESTED", "INTERVIEW", saved.getId(),
                "Reschedule requested: " + reason);

        // Notify company
        Company company = companyService.getCompanyById(interview.getCompanyId());
        createInAppNotification(company.getUserId(),
                "Student " + student.getName() + " requested reschedule for " + interview.getRoundName() + ": " + reason);

        return saved;
    }

    public List<Interview> getInterviewsForStudent(String studentUserId) {
        Student student = studentService.getStudentByUserId(studentUserId);
        return interviewRepository.findByStudentIdOrderByStartDateTimeAsc(student.getId());
    }

    // ─── Helper Methods ───────────────────────────────────────────────────────

    public void verifyOwnershipOrAdmin(String actorUserId, String companyId) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + actorUserId));

        if (actor.getRole() == Role.PLACEMENT_CELL) {
            return; // Placement cell has global authority across all companies
        }

        Company company = companyService.getCompanyByUserId(actorUserId);
        if (!company.getId().equals(companyId)) {
            throw new UnauthorizedActionException("You can only manage applications submitted to your own company's jobs.");
        }
    }

    private void recordAudit(String actorUserId, String role, String action, String targetEntity, String targetId, String details) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .actorUserId(actorUserId)
                    .actorRole(role)
                    .action(action)
                    .targetEntity(targetEntity)
                    .targetId(targetId)
                    .details(details)
                    .timestamp(LocalDateTime.now(KOLKATA_ZONE))
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception ex) {
            log.error("Failed recording audit log", ex);
        }
    }

    private void createInAppNotification(String userId, String message) {
        try {
            Notification notif = Notification.builder()
                    .userId(userId)
                    .message(message)
                    .read(false)
                    .createdAt(LocalDateTime.now(KOLKATA_ZONE))
                    .build();
            notificationRepository.save(notif);
        } catch (Exception ex) {
            log.error("Failed creating in-app notification", ex);
        }
    }

    private byte[] generateIcsBytes(String title, String company, LocalDateTime start, LocalDateTime end, String location) {
        DateTimeFormatter icsFmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
        String ics = "BEGIN:VCALENDAR\r\n" +
                "VERSION:2.0\r\n" +
                "PRODID:-//College Placement Portal//EN\r\n" +
                "BEGIN:VEVENT\r\n" +
                "SUMMARY:" + title + " - " + company + "\r\n" +
                "DESCRIPTION:Campus Placement Interview with " + company + "\r\n" +
                "LOCATION:" + (location != null ? location : "Online") + "\r\n" +
                "DTSTART:" + start.format(icsFmt) + "\r\n" +
                "DTEND:" + end.format(icsFmt) + "\r\n" +
                "STATUS:CONFIRMED\r\n" +
                "END:VEVENT\r\n" +
                "END:VCALENDAR\r\n";
        return ics.getBytes(StandardCharsets.UTF_8);
    }
}
