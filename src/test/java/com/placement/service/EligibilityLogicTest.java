package com.placement.service;

import com.placement.model.Job;
import com.placement.model.JobStatus;
import com.placement.model.Student;
import com.placement.repository.StudentRepository;
import com.placement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public class EligibilityLogicTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private GridFsTemplate gridFsTemplate;

    @InjectMocks
    private StudentService studentService;

    private Student student;
    private Job job;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(studentService, "singleOfferPolicy", true);

        student = Student.builder()
                .id("student-1")
                .userId("user-1")
                .name("Aarav Sharma")
                .branch("CSE")
                .cgpa(8.5)
                .backlogs(0)
                .placed(false)
                .build();

        job = Job.builder()
                .id("job-1")
                .companyId("comp-1")
                .title("Software Engineer")
                .minCgpa(7.5)
                .maxBacklogs(0)
                .allowedBranches(List.of("CSE", "IT"))
                .deadline(LocalDate.now().plusDays(10))
                .status(JobStatus.APPROVED)
                .build();
    }

    @Test
    @DisplayName("Eligible student meets all criteria")
    void testStudentIsEligibleWhenAllCriteriaMet() {
        assertTrue(studentService.isStudentEligible(student, job));
    }

    @Test
    @DisplayName("Ineligible when student CGPA is below job minimum")
    void testIneligibleWhenCgpaLowerThanRequired() {
        student.setCgpa(7.2); // Below 7.5
        assertFalse(studentService.isStudentEligible(student, job));
    }

    @Test
    @DisplayName("Ineligible when student backlogs exceed job limit")
    void testIneligibleWhenBacklogsExceedLimit() {
        student.setBacklogs(1); // Exceeds 0
        assertFalse(studentService.isStudentEligible(student, job));
    }

    @Test
    @DisplayName("Ineligible when student branch is not in allowed branches")
    void testIneligibleWhenBranchNotAllowed() {
        student.setBranch("MECH"); // Not in CSE, IT
        assertFalse(studentService.isStudentEligible(student, job));
    }

    @Test
    @DisplayName("Ineligible when job status is not APPROVED")
    void testIneligibleWhenJobPendingOrRejected() {
        job.setStatus(JobStatus.PENDING);
        assertFalse(studentService.isStudentEligible(student, job));

        job.setStatus(JobStatus.REJECTED);
        assertFalse(studentService.isStudentEligible(student, job));
    }

    @Test
    @DisplayName("Ineligible when job deadline has passed")
    void testIneligibleWhenDeadlinePassed() {
        job.setDeadline(LocalDate.now().minusDays(1));
        assertFalse(studentService.isStudentEligible(student, job));
    }

    @Test
    @DisplayName("Ineligible when student is placed and single-offer policy is active")
    void testIneligibleWhenAlreadyPlacedUnderSingleOfferPolicy() {
        student.setPlaced(true);
        assertFalse(studentService.isStudentEligible(student, job));
    }

    @Test
    @DisplayName("Eligible when student is placed but single-offer policy is disabled")
    void testEligibleWhenPlacedIfPolicyDisabled() {
        ReflectionTestUtils.setField(studentService, "singleOfferPolicy", false);
        student.setPlaced(true);
        assertTrue(studentService.isStudentEligible(student, job));
    }
}

