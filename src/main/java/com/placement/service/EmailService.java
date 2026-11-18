package com.placement.service;

import com.placement.model.EmailStatus;
import com.placement.model.Notification;
import com.placement.repository.NotificationRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final NotificationRepository notificationRepository;

    @Async
    public void sendEmail(String userId, String toEmail, String subject, String htmlContent) {
        sendEmailWithAttachment(userId, toEmail, subject, htmlContent, null, null);
    }

    @Async
    public void sendEmailWithAttachment(String userId, String toEmail, String subject, String htmlContent, String attachmentFilename, byte[] attachmentBytes) {
        Notification notification = Notification.builder()
                .userId(userId)
                .message(subject)
                .read(false)
                .createdAt(LocalDateTime.now())
                .emailStatus(EmailStatus.PENDING)
                .build();

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom("placement-cell@college.edu");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            if (attachmentFilename != null && attachmentBytes != null) {
                helper.addAttachment(attachmentFilename, new org.springframework.core.io.ByteArrayResource(attachmentBytes), "text/calendar");
            }

            mailSender.send(message);
            notification.setEmailStatus(EmailStatus.SENT);
            log.info("Email successfully sent to: {} with subject: {}", toEmail, subject);
        } catch (Exception ex) {
            notification.setEmailStatus(EmailStatus.FAILED);
            log.error("Failed to send email to: {}. Error: {}", toEmail, ex.getMessage());
        } finally {
            try {
                notificationRepository.save(notification);
            } catch (Exception e) {
                log.error("Failed to save notification record for user: {}. Error: {}", userId, e.getMessage());
            }
        }
    }

    public void sendCompanyAccountCreated(String userId, String toEmail, String companyName, String tempPassword) {
        String subject = "Welcome to College Placement Portal - Company Account Details";
        String content = """
            <div style="font-family: Arial, sans-serif; padding: 20px; color: #333;">
                <h2 style="color: #0d6efd;">Welcome %s!</h2>
                <p>Your company account on the College Placement Portal has been created by the Placement Cell.</p>
                <div style="background: #f8f9fa; padding: 15px; border-left: 4px solid #0d6efd; margin: 20px 0;">
                    <p><strong>Login Email:</strong> %s</p>
                    <p><strong>Temporary Password:</strong> <code style="font-size: 1.1em; color: #d63384;">%s</code></p>
                </div>
                <p>Please log in and update your job postings.</p>
                <p>Best regards,<br/><strong>College Placement Cell</strong></p>
            </div>
            """.formatted(companyName, toEmail, tempPassword);
        sendEmail(userId, toEmail, subject, content);
    }

    public void sendJobApproved(String userId, String companyEmail, String jobTitle) {
        String subject = "Job Posting Approved: " + jobTitle;
        String content = """
            <div style="font-family: Arial, sans-serif; padding: 20px; color: #333;">
                <h2 style="color: #198754;">Job Posting Approved!</h2>
                <p>Good news! Your job posting <strong>%s</strong> has been reviewed and approved by the Placement Cell.</p>
                <p>Eligible students can now view the job and submit applications.</p>
                <p>Best regards,<br/><strong>College Placement Cell</strong></p>
            </div>
            """.formatted(jobTitle);
        sendEmail(userId, companyEmail, subject, content);
    }

    public void sendJobRejected(String userId, String companyEmail, String jobTitle, String reason) {
        String subject = "Job Posting Requires Changes: " + jobTitle;
        String content = """
            <div style="font-family: Arial, sans-serif; padding: 20px; color: #333;">
                <h2 style="color: #dc3545;">Job Posting Rejected</h2>
                <p>Your job posting <strong>%s</strong> has been rejected by the Placement Cell.</p>
                <div style="background: #fff3cd; padding: 15px; border-left: 4px solid #ffc107; margin: 20px 0;">
                    <p><strong>Reason for rejection:</strong> %s</p>
                </div>
                <p>Please revise the details and resubmit for approval.</p>
                <p>Best regards,<br/><strong>College Placement Cell</strong></p>
            </div>
            """.formatted(jobTitle, reason);
        sendEmail(userId, companyEmail, subject, content);
    }

    public void sendNewEligibleJobAlert(String userId, String studentEmail, String studentName, String jobTitle, String companyName, Double packageLpa) {
        String subject = "New Placement Opportunity: " + jobTitle + " at " + companyName;
        String content = """
            <div style="font-family: Arial, sans-serif; padding: 20px; color: #333;">
                <h2 style="color: #0d6efd;">New Job Alert!</h2>
                <p>Hello %s,</p>
                <p>A new job posting matching your eligibility criteria has just been published!</p>
                <div style="background: #f8f9fa; padding: 15px; border-left: 4px solid #0d6efd; margin: 20px 0;">
                    <p><strong>Company:</strong> %s</p>
                    <p><strong>Role:</strong> %s</p>
                    <p><strong>Package:</strong> %.2f LPA</p>
                </div>
                <p>Log in to your placement portal to view full details and apply before the deadline.</p>
                <p>Best regards,<br/><strong>College Placement Cell</strong></p>
            </div>
            """.formatted(studentName, companyName, jobTitle, packageLpa);
        sendEmail(userId, studentEmail, subject, content);
    }

    public void sendApplicationConfirmation(String userId, String studentEmail, String studentName, String jobTitle, String companyName) {
        String subject = "Application Confirmed: " + jobTitle + " at " + companyName;
        String content = """
            <div style="font-family: Arial, sans-serif; padding: 20px; color: #333;">
                <h2 style="color: #198754;">Application Received</h2>
                <p>Hello %s,</p>
                <p>Your application for the position of <strong>%s</strong> at <strong>%s</strong> has been successfully submitted.</p>
                <p>You can track the progress of your application and interview rounds in your dashboard.</p>
                <p>Best regards,<br/><strong>College Placement Cell</strong></p>
            </div>
            """.formatted(studentName, jobTitle, companyName);
        sendEmail(userId, studentEmail, subject, content);
    }

    public void sendRoundScheduled(String userId, String studentEmail, String studentName, String jobTitle, String companyName, String roundName, String scheduledTime) {
        sendRoundScheduledWithIcs(userId, studentEmail, studentName, jobTitle, companyName, roundName, scheduledTime, null, null);
    }

    public void sendRoundScheduledWithIcs(String userId, String studentEmail, String studentName, String jobTitle, String companyName, String roundName, String scheduledTime, String icsFilename, byte[] icsBytes) {
        String subject = "Interview Scheduled: " + roundName + " for " + jobTitle;
        String content = """
            <div style="font-family: Arial, sans-serif; padding: 20px; color: #333;">
                <h2 style="color: #0d6efd;">Interview Round Scheduled</h2>
                <p>Hello %s,</p>
                <p><strong>%s</strong> has scheduled an interview round for your application to <strong>%s</strong>.</p>
                <div style="background: #e7f1ff; padding: 15px; border-left: 4px solid #0d6efd; margin: 20px 0;">
                    <p><strong>Round:</strong> %s</p>
                    <p><strong>Scheduled Time:</strong> %s</p>
                </div>
                <p>Please be ready on time. Check your dashboard for additional notes or instructions. A calendar invite (.ics) is attached.</p>
                <p>Best regards,<br/><strong>College Placement Cell</strong></p>
            </div>
            """.formatted(studentName, companyName, jobTitle, roundName, scheduledTime);
        sendEmailWithAttachment(userId, studentEmail, subject, content, icsFilename, icsBytes);
    }

    public void sendRoundResult(String userId, String studentEmail, String studentName, String jobTitle, String roundName, String result) {
        String subject = "Interview Result: " + roundName + " - " + jobTitle;
        String color = "CLEARED".equalsIgnoreCase(result) ? "#198754" : "#dc3545";
        String content = """
            <div style="font-family: Arial, sans-serif; padding: 20px; color: #333;">
                <h2>Interview Round Update</h2>
                <p>Hello %s,</p>
                <p>The result for <strong>%s</strong> (%s) has been posted:</p>
                <p style="font-size: 1.2em; color: %s;"><strong>Status: %s</strong></p>
                <p>Log in to your dashboard to view complete timeline details.</p>
                <p>Best regards,<br/><strong>College Placement Cell</strong></p>
            </div>
            """.formatted(studentName, roundName, jobTitle, color, result);
        sendEmail(userId, studentEmail, subject, content);
    }

    public void sendOfferMade(String userId, String studentEmail, String studentName, String jobTitle, String companyName, Double offerLpa) {
        String subject = "Congratulations! Job Offer from " + companyName;
        String content = """
            <div style="font-family: Arial, sans-serif; padding: 20px; color: #333;">
                <h2 style="color: #198754;">🎉 Congratulations %s!</h2>
                <p>You have been selected and offered a position at <strong>%s</strong>!</p>
                <div style="background: #d1e7dd; padding: 15px; border-left: 4px solid #198754; margin: 20px 0;">
                    <p><strong>Role:</strong> %s</p>
                    <p><strong>Offered Package:</strong> %.2f LPA</p>
                </div>
                <p>The college and placement team congratulate you on this milestone achievement!</p>
                <p>Best regards,<br/><strong>College Placement Cell</strong></p>
            </div>
            """.formatted(studentName, companyName, jobTitle, offerLpa);
        sendEmail(userId, studentEmail, subject, content);
    }

    public void sendDeadlineReminder(String userId, String studentEmail, String studentName, String jobTitle, String deadlineStr) {
        String subject = "Reminder: Application Deadline Approaching for " + jobTitle;
        String content = """
            <div style="font-family: Arial, sans-serif; padding: 20px; color: #333;">
                <h2 style="color: #fd7e14;">⏳ Application Deadline Approaching!</h2>
                <p>Hello %s,</p>
                <p>This is a reminder that the application deadline for <strong>%s</strong> is coming up soon on <strong>%s</strong>.</p>
                <p>Don't miss this opportunity. Visit the placement portal to submit your application.</p>
                <p>Best regards,<br/><strong>College Placement Cell</strong></p>
            </div>
            """.formatted(studentName, jobTitle, deadlineStr);
        sendEmail(userId, studentEmail, subject, content);
    }
}

