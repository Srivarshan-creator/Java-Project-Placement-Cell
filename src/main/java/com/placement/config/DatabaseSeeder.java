package com.placement.config;

import com.placement.model.*;
import com.placement.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final CompanyRepository companyRepository;
    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("Database already initialized. Ready for requests.");
            return;
        }

        log.info("Initializing database with demo accounts...");

        // 1. Seed Placement Cell Admin
        User adminUser = User.builder()
                .email("admin@college.edu")
                .passwordHash(passwordEncoder.encode("Admin@123"))
                .role(Role.PLACEMENT_CELL)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();
        userRepository.save(adminUser);

        // 2. Seed 2 Companies
        User googleUser = User.builder()
                .email("contact@google.com")
                .passwordHash(passwordEncoder.encode("Google@123"))
                .role(Role.COMPANY)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();
        User savedGoogle = userRepository.save(googleUser);
        if (savedGoogle == null) {
            log.info("Repository returned null on save (test mock environment). Skipping demo seed.");
            return;
        }
        googleUser = savedGoogle;

        Company google = Company.builder()
                .userId(googleUser.getId())
                .name("Google")
                .contactEmail("contact@google.com")
                .createdAt(LocalDateTime.now())
                .build();
        google = companyRepository.save(google);

        User msUser = User.builder()
                .email("campus@microsoft.com")
                .passwordHash(passwordEncoder.encode("Microsoft@123"))
                .role(Role.COMPANY)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();
        msUser = userRepository.save(msUser);

        Company microsoft = Company.builder()
                .userId(msUser.getId())
                .name("Microsoft")
                .contactEmail("campus@microsoft.com")
                .createdAt(LocalDateTime.now())
                .build();
        microsoft = companyRepository.save(microsoft);

        // 3. Seed 10 Students across CSE, IT, ECE, MECH
        Student divya = createStudent("Divya Nair", "divya.nair@college.edu", "CSE", 9.5, 0, List.of("Machine Learning", "Python", "PyTorch", "Java"), true, 24.0);
        Student aarav = createStudent("Aarav Sharma", "aarav.sharma@college.edu", "CSE", 9.2, 0, List.of("Java", "Spring Boot", "Microservices", "Docker"), false, null);
        Student priya = createStudent("Priya Patel", "priya.patel@college.edu", "IT", 8.7, 0, List.of("Python", "Django", "React", "AWS"), false, null);
        Student rohan = createStudent("Rohan Iyer", "rohan.iyer@college.edu", "CSE", 8.1, 0, List.of("C++", "Data Structures", "System Design", "SQL"), false, null);
        Student ananya = createStudent("Ananya Gupta", "ananya.gupta@college.edu", "ECE", 8.9, 0, List.of("Embedded Systems", "IoT", "C++", "Robotics"), false, null);
        Student manish = createStudent("Manish Rao", "manish.rao@college.edu", "MECH", 8.4, 0, List.of("ANSYS", "MATLAB", "Robotics", "CAD"), true, 8.5);
        Student sneha = createStudent("Sneha Kulkarni", "sneha.kulkarni@college.edu", "IT", 7.8, 0, List.of("JavaScript", "Angular", "Node.js", "MongoDB"), false, null);
        Student vikram = createStudent("Vikram Singh", "vikram.singh@college.edu", "MECH", 7.5, 1, List.of("AutoCAD", "SolidWorks", "Python"), false, null);
        Student pooja = createStudent("Pooja Das", "pooja.das@college.edu", "ECE", 7.2, 0, List.of("VLSI", "MATLAB", "Verilog", "Embedded C"), false, null);
        Student karthik = createStudent("Karthik Reddy", "karthik.reddy@college.edu", "CSE", 6.9, 2, List.of("HTML", "CSS", "SQL", "Java Basics"), false, null);

        // 4. Seed 4 Jobs with mixed statuses
        Job jobGoogleSde = Job.builder()
                .companyId(google.getId())
                .title("Software Development Engineer")
                .description("Build scalable distributed systems and backend cloud architectures. Ideal for students with strong problem solving, algorithmic foundations, and system design expertise.")
                .packageLpa(24.0)
                .minCgpa(8.5)
                .maxBacklogs(0)
                .allowedBranches(List.of("CSE", "IT"))
                .deadline(LocalDate.now().plusDays(25))
                .status(JobStatus.APPROVED)
                .approvedBy("admin@college.edu")
                .approvedAt(LocalDateTime.now().minusDays(5))
                .createdAt(LocalDateTime.now().minusDays(6))
                .updatedAt(LocalDateTime.now().minusDays(5))
                .build();
        jobGoogleSde = jobRepository.save(jobGoogleSde);

        Job jobMsCloud = Job.builder()
                .companyId(microsoft.getId())
                .title("Cloud & DevOps Associate")
                .description("Drive automated deployments, Kubernetes cluster scaling, and Azure cloud infrastructure migrations. Open to passionate engineers across computer science and electronics.")
                .packageLpa(16.0)
                .minCgpa(7.5)
                .maxBacklogs(0)
                .allowedBranches(List.of("CSE", "IT", "ECE"))
                .deadline(LocalDate.now().plusDays(15))
                .status(JobStatus.APPROVED)
                .approvedBy("admin@college.edu")
                .approvedAt(LocalDateTime.now().minusDays(3))
                .createdAt(LocalDateTime.now().minusDays(4))
                .updatedAt(LocalDateTime.now().minusDays(3))
                .build();
        jobMsCloud = jobRepository.save(jobMsCloud);

        Job jobMsHardware = Job.builder()
                .companyId(microsoft.getId())
                .title("Systems Hardware Engineer")
                .description("Design high-efficiency server architectures and thermal management units for hyperscale datacenters. Open to Electronics and Mechanical engineering departments.")
                .packageLpa(14.0)
                .minCgpa(7.0)
                .maxBacklogs(1)
                .allowedBranches(List.of("ECE", "MECH"))
                .deadline(LocalDate.now().plusDays(30))
                .status(JobStatus.PENDING)
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();
        jobMsHardware = jobRepository.save(jobMsHardware);

        Job jobGoogleClosed = Job.builder()
                .companyId(google.getId())
                .title("Technical Solutions Specialist")
                .description("Troubleshoot mission-critical enterprise client integrations, analyze logs, and optimize customer workflows.")
                .packageLpa(9.0)
                .minCgpa(6.5)
                .maxBacklogs(1)
                .allowedBranches(List.of("CSE", "IT", "ECE", "MECH"))
                .deadline(LocalDate.now().minusDays(5))
                .status(JobStatus.CLOSED)
                .approvedBy("admin@college.edu")
                .approvedAt(LocalDateTime.now().minusDays(20))
                .createdAt(LocalDateTime.now().minusDays(25))
                .updatedAt(LocalDateTime.now().minusDays(5))
                .build();
        jobGoogleClosed = jobRepository.save(jobGoogleClosed);

        // 5. Seed Applications with Rounds & Timelines
        List<Round> divyaRounds = new ArrayList<>();
        divyaRounds.add(Round.builder().roundNumber(1).name("Online Coding Challenge").scheduledAt(LocalDateTime.now().minusDays(10)).result(RoundResult.CLEARED).notes("Solved 3/3 problems in 45 mins").build());
        divyaRounds.add(Round.builder().roundNumber(2).name("System Design Round").scheduledAt(LocalDateTime.now().minusDays(6)).result(RoundResult.CLEARED).notes("Excellent distributed caching architecture").build());
        divyaRounds.add(Round.builder().roundNumber(3).name("Bar Raiser & Values").scheduledAt(LocalDateTime.now().minusDays(3)).result(RoundResult.CLEARED).notes("Strong cultural alignment").build());

        Application appDivya = Application.builder()
                .studentId(divya.getId())
                .jobId(jobGoogleSde.getId())
                .companyId(google.getId())
                .branch(divya.getBranch())
                .status(ApplicationStatus.OFFERED)
                .rounds(divyaRounds)
                .offerLpa(24.0)
                .appliedAt(LocalDateTime.now().minusDays(12))
                .build();
        applicationRepository.save(appDivya);

        List<Round> aaravRounds = new ArrayList<>();
        aaravRounds.add(Round.builder().roundNumber(1).name("Online Coding Challenge").scheduledAt(LocalDateTime.now().minusDays(7)).result(RoundResult.CLEARED).notes("Score 100/100").build());
        aaravRounds.add(Round.builder().roundNumber(2).name("Technical Interview 1").scheduledAt(LocalDateTime.now().plusDays(2)).result(RoundResult.PENDING).notes("Scheduled via Google Meet").build());

        Application appAarav = Application.builder()
                .studentId(aarav.getId())
                .jobId(jobGoogleSde.getId())
                .companyId(google.getId())
                .branch(aarav.getBranch())
                .status(ApplicationStatus.IN_PROGRESS)
                .rounds(aaravRounds)
                .appliedAt(LocalDateTime.now().minusDays(9))
                .build();
        applicationRepository.save(appAarav);

        List<Round> priyaRounds = new ArrayList<>();
        priyaRounds.add(Round.builder().roundNumber(1).name("Online Technical Assessment").scheduledAt(LocalDateTime.now().plusDays(3)).result(RoundResult.PENDING).notes("Access credentials will be emailed").build());

        Application appPriya = Application.builder()
                .studentId(priya.getId())
                .jobId(jobMsCloud.getId())
                .companyId(microsoft.getId())
                .branch(priya.getBranch())
                .status(ApplicationStatus.SHORTLISTED)
                .rounds(priyaRounds)
                .appliedAt(LocalDateTime.now().minusDays(4))
                .build();
        applicationRepository.save(appPriya);

        divya.setPlacedAtJobId(jobGoogleSde.getId());
        studentRepository.save(divya);

        log.info("Database seeding successfully completed.");
        log.info("=================================================================");
        log.info("DEMO CREDENTIALS FOR TESTING:");
        log.info("Placement Cell: admin@college.edu / Admin@123");
        log.info("Company (Google): contact@google.com / Google@123");
        log.info("Company (Microsoft): campus@microsoft.com / Microsoft@123");
        log.info("Student (Seeking): aarav.sharma@college.edu / Student@123");
        log.info("Student (Placed): divya.nair@college.edu / Student@123");
        log.info("=================================================================");
    }

    private Student createStudent(String name, String email, String branch, double cgpa, int backlogs, List<String> skills, boolean placed, Double offerLpa) {
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("Student@123"))
                .role(Role.STUDENT)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();
        user = userRepository.save(user);

        Student student = Student.builder()
                .userId(user.getId())
                .name(name)
                .branch(branch)
                .cgpa(cgpa)
                .backlogs(backlogs)
                .skills(skills)
                .placed(placed)
                .offerLpa(offerLpa)
                .updatedAt(LocalDateTime.now())
                .build();

        return studentRepository.save(student);
    }
}
