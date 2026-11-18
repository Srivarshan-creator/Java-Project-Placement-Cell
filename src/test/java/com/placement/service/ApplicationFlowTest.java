package com.placement.service;

import com.placement.dto.OfferDto;
import com.placement.exception.DuplicateApplicationException;
import com.placement.exception.EligibilityException;
import com.placement.exception.SingleOfferViolationException;
import com.placement.model.*;
import com.placement.repository.ApplicationRepository;
import com.placement.repository.StudentRepository;
import com.placement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ApplicationFlowTest {

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
    private com.placement.repository.InterviewRepository interviewRepository;

    @Mock
    private com.placement.repository.NotificationRepository notificationRepository;

    @Mock
    private com.placement.repository.AuditLogRepository auditLogRepository;

    @InjectMocks
    private ApplicationService applicationService;

    private Student student;
    private Job job;
    private Company company;
    private User companyUser;

    @BeforeEach
    void setUp() {
        companyUser = User.builder()
                .id("company-user-1")
                .email("hr@techcorp.com")
                .role(Role.COMPANY)
                .build();
        lenient().when(userRepository.findById("company-user-1")).thenReturn(Optional.of(companyUser));
        student = Student.builder()
                .id("student-1")
                .userId("user-1")
                .name("Aarav Sharma")
                .branch("CSE")
                .cgpa(9.0)
                .backlogs(0)
                .placed(false)
                .build();

        company = Company.builder()
                .id("company-1")
                .userId("company-user-1")
                .name("Tech Corp")
                .contactEmail("hr@techcorp.com")
                .build();

        job = Job.builder()
                .id("job-1")
                .companyId(company.getId())
                .title("Software Developer")
                .packageLpa(12.0)
                .minCgpa(8.0)
                .maxBacklogs(0)
                .allowedBranches(List.of("CSE"))
                .deadline(LocalDate.now().plusDays(5))
                .status(JobStatus.APPROVED)
                .build();
    }

    @Test
    @DisplayName("Successful apply creates application with APPLIED status")
    void testSuccessfulApplication() {
        when(studentService.getStudentByUserId("user-1")).thenReturn(student);
        when(jobService.getJobById("job-1")).thenReturn(job);
        when(studentService.isSingleOfferPolicy()).thenReturn(true);
        when(studentService.isStudentEligible(student, job)).thenReturn(true);
        when(applicationRepository.existsByStudentIdAndJobId("student-1", "job-1")).thenReturn(false);
        when(companyService.getCompanyById("company-1")).thenReturn(company);

        Application mockSaved = Application.builder()
                .id("app-1")
                .studentId("student-1")
                .jobId("job-1")
                .companyId("company-1")
                .branch("CSE")
                .status(ApplicationStatus.APPLIED)
                .build();

        when(applicationRepository.save(any(Application.class))).thenReturn(mockSaved);

        Application result = applicationService.applyToJob("user-1", "job-1");

        assertNotNull(result);
        assertEquals(ApplicationStatus.APPLIED, result.getStatus());
        assertEquals("CSE", result.getBranch());
        verify(applicationRepository, times(1)).save(any(Application.class));
    }

    @Test
    @DisplayName("Duplicate application throws DuplicateApplicationException")
    void testDuplicateApplicationThrowsException() {
        when(studentService.getStudentByUserId("user-1")).thenReturn(student);
        when(jobService.getJobById("job-1")).thenReturn(job);
        when(studentService.isSingleOfferPolicy()).thenReturn(true);
        when(studentService.isStudentEligible(student, job)).thenReturn(true);
        when(applicationRepository.existsByStudentIdAndJobId("student-1", "job-1")).thenReturn(true);

        assertThrows(DuplicateApplicationException.class, () -> {
            applicationService.applyToJob("user-1", "job-1");
        });

        verify(applicationRepository, never()).save(any(Application.class));
    }

    @Test
    @DisplayName("Ineligible student applying throws EligibilityException")
    void testIneligibleStudentThrowsException() {
        when(studentService.getStudentByUserId("user-1")).thenReturn(student);
        when(jobService.getJobById("job-1")).thenReturn(job);
        when(studentService.isSingleOfferPolicy()).thenReturn(true);
        when(studentService.isStudentEligible(student, job)).thenReturn(false);

        assertThrows(EligibilityException.class, () -> {
            applicationService.applyToJob("user-1", "job-1");
        });

        verify(applicationRepository, never()).save(any(Application.class));
    }

    @Test
    @DisplayName("Placed student applying with single-offer policy active throws SingleOfferViolationException")
    void testPlacedStudentUnderSingleOfferPolicyThrowsException() {
        student.setPlaced(true);
        when(studentService.getStudentByUserId("user-1")).thenReturn(student);
        when(jobService.getJobById("job-1")).thenReturn(job);
        when(studentService.isSingleOfferPolicy()).thenReturn(true);

        assertThrows(SingleOfferViolationException.class, () -> {
            applicationService.applyToJob("user-1", "job-1");
        });

        verify(applicationRepository, never()).save(any(Application.class));
    }

    @Test
    @DisplayName("Making an offer updates application to OFFERED and marks student placed")
    void testMakeOfferFlow() {
        Application existingApp = Application.builder()
                .id("app-1")
                .studentId("student-1")
                .jobId("job-1")
                .companyId(company.getId())
                .status(ApplicationStatus.IN_PROGRESS)
                .build();

        when(applicationRepository.findById("app-1")).thenReturn(Optional.of(existingApp));
        when(companyService.getCompanyByUserId("company-user-1")).thenReturn(company);
        when(studentRepository.findById("student-1")).thenReturn(Optional.of(student));
        when(jobService.getJobById("job-1")).thenReturn(job);
        when(companyService.getCompanyById(company.getId())).thenReturn(company);
        when(applicationRepository.save(any(Application.class))).thenAnswer(i -> i.getArgument(0));

        OfferDto offerDto = new OfferDto(14.5);
        Application updated = applicationService.makeOffer("company-user-1", "app-1", offerDto);

        assertEquals(ApplicationStatus.OFFERED, updated.getStatus());
        assertEquals(14.5, updated.getOfferLpa());
        assertTrue(student.isPlaced());
        assertEquals(14.5, student.getOfferLpa());
        verify(studentRepository, times(1)).save(student);
    }
}

