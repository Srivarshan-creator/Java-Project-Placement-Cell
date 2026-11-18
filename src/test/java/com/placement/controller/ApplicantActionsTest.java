package com.placement.controller;

import com.placement.dto.OfferDto;
import com.placement.dto.RoundResultDto;
import com.placement.dto.ScheduleInterviewDto;
import com.placement.exception.BadRequestException;
import com.placement.exception.UnauthorizedActionException;
import com.placement.model.Application;
import com.placement.model.ApplicationStatus;
import com.placement.model.Company;
import com.placement.model.Interview;
import com.placement.model.Job;
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
import com.placement.service.ApplicationService;
import com.placement.service.CompanyService;
import com.placement.service.EmailService;
import com.placement.service.JobService;
import com.placement.service.StudentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ApplicantActionsTest {

    private static final ZoneId KOLKATA_ZONE = ZoneId.of("Asia/Kolkata");

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private StudentService studentService;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private JobService jobService;

    @Mock
    private CompanyService companyService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private InterviewRepository interviewRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private ApplicationService applicationService;

    private User companyUser;
    private User otherCompanyUser;
    private User adminUser;
    private Company company1;
    private Company company2;
    private Job job1;
    private Student student1;
    private Student student2;
    private Application appliedApp;
    private Application shortlistedApp;
    private Application inProgressApp;
    private Application offeredApp;
    private Application rejectedApp;

    @BeforeEach
    void setUp() {
        companyUser = User.builder().id("user-c1").email("c1@test.com").role(Role.COMPANY).build();
        otherCompanyUser = User.builder().id("user-c2").email("c2@test.com").role(Role.COMPANY).build();
        adminUser = User.builder().id("user-admin").email("admin@college.edu").role(Role.PLACEMENT_CELL).build();

        company1 = Company.builder().id("comp-1").userId("user-c1").name("Alpha Corp").build();
        company2 = Company.builder().id("comp-2").userId("user-c2").name("Beta Corp").build();

        job1 = Job.builder().id("job-1").companyId("comp-1").title("Software Engineer").packageLpa(15.0).build();

        student1 = Student.builder().id("stud-1").userId("user-s1").name("Aarav Sharma").branch("CSE").cgpa(9.2).build();
        student2 = Student.builder().id("stud-2").userId("user-s2").name("Divya Nair").branch("CSE").cgpa(9.5).build();

        appliedApp = Application.builder().id("app-applied").companyId("comp-1").jobId("job-1").studentId("stud-1").status(ApplicationStatus.APPLIED).rounds(new ArrayList<>()).build();
        shortlistedApp = Application.builder().id("app-shortlisted").companyId("comp-1").jobId("job-1").studentId("stud-1").status(ApplicationStatus.SHORTLISTED).rounds(new ArrayList<>()).build();
        inProgressApp = Application.builder().id("app-inprogress").companyId("comp-1").jobId("job-1").studentId("stud-1").status(ApplicationStatus.IN_PROGRESS).rounds(new ArrayList<>()).build();
        offeredApp = Application.builder().id("app-offered").companyId("comp-1").jobId("job-1").studentId("stud-1").status(ApplicationStatus.OFFERED).rounds(new ArrayList<>()).build();
        rejectedApp = Application.builder().id("app-rejected").companyId("comp-1").jobId("job-1").studentId("stud-1").status(ApplicationStatus.REJECTED).rounds(new ArrayList<>()).build();

        lenient().when(userRepository.findById("user-c1")).thenReturn(Optional.of(companyUser));
        lenient().when(userRepository.findById("user-c2")).thenReturn(Optional.of(otherCompanyUser));
        lenient().when(userRepository.findById("user-admin")).thenReturn(Optional.of(adminUser));
        lenient().when(companyService.getCompanyByUserId("user-c1")).thenReturn(company1);
        lenient().when(companyService.getCompanyByUserId("user-c2")).thenReturn(company2);
        lenient().when(jobService.getJobById("job-1")).thenReturn(job1);
        lenient().when(companyService.getCompanyById("comp-1")).thenReturn(company1);
        lenient().when(studentRepository.findById("stud-1")).thenReturn(Optional.of(student1));
        lenient().when(studentRepository.findById("stud-2")).thenReturn(Optional.of(student2));
        lenient().when(studentService.getStudentById("stud-1")).thenReturn(student1);
        lenient().when(studentService.getStudentById("stud-2")).thenReturn(student2);
        lenient().when(applicationRepository.save(any(Application.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("Shortlist applicant - Success for owning company")
    void testShortlistSuccess() {
        when(applicationRepository.findById("app-applied")).thenReturn(Optional.of(appliedApp));

        Application result = applicationService.shortlistApplicant("user-c1", "app-applied");
        assertEquals(ApplicationStatus.SHORTLISTED, result.getStatus());
    }

    @Test
    @DisplayName("Wrong company receives 403 / UnauthorizedActionException")
    void testWrongCompanyForbidden() {
        when(applicationRepository.findById("app-applied")).thenReturn(Optional.of(appliedApp));

        assertThrows(UnauthorizedActionException.class, () -> {
            applicationService.shortlistApplicant("user-c2", "app-applied");
        });
    }

    @Test
    @DisplayName("Placement cell can act on any company's application")
    void testPlacementCellBypassesCompanyOwnership() {
        when(applicationRepository.findById("app-applied")).thenReturn(Optional.of(appliedApp));

        Application result = applicationService.shortlistApplicant("user-admin", "app-applied");
        assertEquals(ApplicationStatus.SHORTLISTED, result.getStatus());
    }

    @Test
    @DisplayName("Invalid transition: Cannot reject already OFFERED applicant")
    void testCannotRejectOfferedApplicant() {
        when(applicationRepository.findById("app-offered")).thenReturn(Optional.of(offeredApp));

        assertThrows(BadRequestException.class, () -> {
            applicationService.rejectApplicant("user-c1", "app-offered");
        });
    }

    @Test
    @DisplayName("Invalid transition: Cannot offer already REJECTED applicant")
    void testCannotOfferRejectedApplicant() {
        when(applicationRepository.findById("app-rejected")).thenReturn(Optional.of(rejectedApp));

        OfferDto dto = new OfferDto(18.0);
        assertThrows(BadRequestException.class, () -> {
            applicationService.makeOffer("user-c1", "app-rejected", dto);
        });
    }

    @Test
    @DisplayName("Invalid transition: Cannot transition from REJECTED to any status")
    void testRejectedIsFinal() {
        when(applicationRepository.findById("app-rejected")).thenReturn(Optional.of(rejectedApp));

        assertThrows(BadRequestException.class, () -> {
            applicationService.updateApplicationStatus("user-c1", "app-rejected", ApplicationStatus.SHORTLISTED);
        });
    }

    @Test
    @DisplayName("Making offer from IN_PROGRESS updates student placed status and LPA")
    void testMakeOfferSuccess() {
        when(applicationRepository.findById("app-inprogress")).thenReturn(Optional.of(inProgressApp));

        OfferDto dto = new OfferDto(24.0);
        Application result = applicationService.makeOffer("user-c1", "app-inprogress", dto);

        assertEquals(ApplicationStatus.OFFERED, result.getStatus());
        assertEquals(24.0, result.getOfferLpa());
        assertTrue(student1.isPlaced());
        assertEquals(24.0, student1.getOfferLpa());
        verify(studentRepository, times(1)).save(student1);
    }

    @Test
    @DisplayName("Record round result CLEARED updates score and feedback")
    void testRecordRoundResultCleared() {
        Round r1 = Round.builder().roundNumber(1).name("Technical OA").result(RoundResult.PENDING).build();
        inProgressApp.getRounds().add(r1);
        when(applicationRepository.findById("app-inprogress")).thenReturn(Optional.of(inProgressApp));

        RoundResultDto dto = RoundResultDto.builder()
                .roundNumber(1)
                .result(RoundResult.CLEARED)
                .score(92.5)
                .feedback("Excellent coding skills")
                .build();

        Application result = applicationService.submitRoundResult("user-c1", "app-inprogress", dto);
        assertEquals(RoundResult.CLEARED, result.getRounds().get(0).getResult());
        assertEquals(92.5, result.getRounds().get(0).getScore());
        assertEquals("Excellent coding skills", result.getRounds().get(0).getFeedback());
    }

    @Test
    @DisplayName("Conflict detection throws BadRequestException naming clashing student and time")
    void testConflictDetection() {
        when(applicationRepository.findById("app-shortlisted")).thenReturn(Optional.of(shortlistedApp));

        LocalDateTime futureTime = LocalDateTime.now(KOLKATA_ZONE).plusDays(2);
        LocalDateTime endTime = futureTime.plusMinutes(45);

        // Mock existing clash for student1
        Interview existingInterview = Interview.builder()
                .id("int-existing")
                .studentId("stud-1")
                .startDateTime(futureTime)
                .endDateTime(endTime)
                .build();

        when(interviewRepository.findByStudentIdAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
                eq("stud-1"), any(LocalDateTime.class), eq(futureTime)))
                .thenReturn(List.of(existingInterview));

        ScheduleInterviewDto dto = ScheduleInterviewDto.builder()
                .applicationIds(List.of("app-shortlisted"))
                .roundNumber(1)
                .roundType("Technical")
                .roundName("Technical Interview")
                .startDateTime(futureTime)
                .durationMinutes(45)
                .mode("ONLINE")
                .slotMode("SAME_SLOT")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> {
            applicationService.scheduleBatchInterviews("user-c1", dto);
        });

        assertTrue(ex.getMessage().contains("Schedule conflict detected for student Aarav Sharma"));
    }

    @Test
    @DisplayName("Auto-slots schedules sequential interviews with break without conflicts")
    void testAutoSlotSequentialGeneration() {
        Application app2 = Application.builder()
                .id("app-2")
                .companyId("comp-1")
                .jobId("job-1")
                .studentId("stud-2")
                .status(ApplicationStatus.SHORTLISTED)
                .rounds(new ArrayList<>())
                .build();

        when(applicationRepository.findById("app-shortlisted")).thenReturn(Optional.of(shortlistedApp));
        when(applicationRepository.findById("app-2")).thenReturn(Optional.of(app2));

        LocalDateTime start = LocalDateTime.now(KOLKATA_ZONE).plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);

        // No clashes
        when(interviewRepository.findByStudentIdAndStartDateTimeLessThanAndEndDateTimeGreaterThan(anyString(), any(), any()))
                .thenReturn(List.of());

        ScheduleInterviewDto dto = ScheduleInterviewDto.builder()
                .applicationIds(List.of("app-shortlisted", "app-2"))
                .roundNumber(1)
                .roundType("Technical")
                .roundName("Coding Round")
                .startDateTime(start)
                .durationMinutes(30)
                .breakMinutes(15)
                .mode("ONLINE")
                .slotMode("AUTO_SLOTS")
                .build();

        List<Application> result = applicationService.scheduleBatchInterviews("user-c1", dto);

        assertEquals(2, result.size());
        assertEquals(ApplicationStatus.IN_PROGRESS, result.get(0).getStatus());
        assertEquals(ApplicationStatus.IN_PROGRESS, result.get(1).getStatus());

        // First student slot: 10:00
        assertEquals(start, result.get(0).getRounds().get(0).getScheduledAt());
        // Second student slot: 10:00 + 30m duration + 15m break = 10:45
        LocalDateTime expectedSlot2 = start.plusMinutes(45);
        assertEquals(expectedSlot2, result.get(1).getRounds().get(0).getScheduledAt());

        // Both interviews saved to interviews collection
        verify(interviewRepository, times(2)).save(any(Interview.class));
    }
}

