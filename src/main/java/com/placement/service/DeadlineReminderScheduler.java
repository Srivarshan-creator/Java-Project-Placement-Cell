package com.placement.service;

import com.placement.model.Job;
import com.placement.model.JobStatus;
import com.placement.model.Student;
import com.placement.model.User;
import com.placement.repository.JobRepository;
import com.placement.repository.StudentRepository;
import com.placement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeadlineReminderScheduler {

    private final JobRepository jobRepository;
    private final StudentRepository studentRepository;
    private final StudentService studentService;
    private final UserRepository userRepository;
    private final EmailService emailService;

    // Runs every day at 9:00 AM
    @Scheduled(cron = "0 0 9 * * *")
    public void sendDeadlineReminders() {
        log.info("Running daily scheduled task: sendDeadlineReminders");
        LocalDate today = LocalDate.now();
        LocalDate twoDaysLater = today.plusDays(2);

        List<Job> expiringJobs = jobRepository.findByStatusAndDeadlineBetween(JobStatus.APPROVED, today, twoDaysLater);
        if (expiringJobs.isEmpty()) {
            log.info("No approved jobs approaching deadline within 2 days.");
            return;
        }

        List<Student> allStudents = studentRepository.findAll();

        for (Job job : expiringJobs) {
            String deadlineStr = job.getDeadline().format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));
            for (Student student : allStudents) {
                if (studentService.isStudentEligible(student, job)) {
                    userRepository.findById(student.getUserId()).ifPresent(user -> {
                        emailService.sendDeadlineReminder(
                                user.getId(),
                                user.getEmail(),
                                student.getName(),
                                job.getTitle(),
                                deadlineStr
                        );
                    });
                }
            }
        }
    }
}

