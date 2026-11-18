# College Placement Management System

A production-grade, enterprise campus recruitment portal built with **Java 17**, **Spring Boot 3.3.5**, **Maven**, and **MongoDB**. The platform streamlines end-to-end placement workflows across students, hiring companies, and the college placement cell.

---

## 🌟 Key Features

### 1. Student Portal (`ROLE_STUDENT`)
- **Self Registration & Profile Management**: Academic branch, CGPA, active backlogs count, and technical skills.
- **Dynamic Job Matching & Filtering**: Students see **only** jobs that are `APPROVED`, before deadline, and where:
  - `student.cgpa >= job.minCgpa`
  - `student.backlogs <= job.maxBacklogs`
  - `student.branch ∈ job.allowedBranches`
- **Application Engine**:
  - Full server-side re-validation of eligibility upon submission.
  - Duplicate application prevention via MongoDB unique compound index (`studentId` + `jobId`).
  - **Single-Offer Policy Flag**: Configurable policy that prevents students who have already accepted a placement offer from applying to further jobs.
- **My Applications & Interview Timeline**: Real-time visual progress timeline displaying interview rounds (`roundNumber`, `name`, `scheduledAt`, `notes`, and outcome `PENDING`, `CLEARED`, or `FAILED`).
- **Resume Management**: Upload PDF resumes (max 2 MB limit strictly validated) stored directly in **MongoDB GridFS**, with one-click download.

### 2. Recruiting Company Portal (`ROLE_COMPANY`)
- **Exclusive Access**: Accounts are provisioned solely by the Placement Cell; automatic temporary password generation and email dispatch.
- **Job Lifecycle Management**: Post and edit openings (title, description, package in LPA, CGPA cutoff, backlog tolerance, eligible branches, deadline).
  - Any newly created or edited job automatically transitions to `PENDING` status for Placement Cell review.
- **Applicant Tracking & Ownership Security**:
  - Companies can strictly access applicants for their **own** jobs only.
  - Pipeline management: Shortlist, reject, schedule interview rounds, post round outcomes, and extend official job offers (`OFFERED`) with compensation (`offerLpa`).

### 3. Placement Cell Admin Portal (`ROLE_PLACEMENT_CELL`)
- **Job Review & Governance**: Inspect pending jobs with one-click approval or rejection with mandatory feedback dispatched to the company.
- **Company Management**: Provision new corporate recruiters with automated temporary password generation.
- **Interactive Analytics Dashboard**:
  - MongoDB Aggregation Pipeline calculating department-wise performance: total students, placed count, placement rate (%), average package (LPA), and highest package (LPA).
  - Company-wise offer breakdown and live list of unplaced candidates.
  - Interactive charts powered by **Chart.js via CDN** (Placement % comparison, Package comparison, Offer distribution).
- **Institution-wide Governance**: View and filter all students, jobs, and applications across departments.

### 4. Asynchronous Email & Notification Service
- Non-blocking `@Async` email delivery via Spring Mail (using Mailpit or local SMTP).
- Never breaks HTTP requests on SMTP failure; logs every dispatch with status `SENT` or `FAILED` in the MongoDB `notifications` collection.
- Automated `@Scheduled` cron job running daily at 9:00 AM reminding eligible candidates of job deadlines within 2 days.

---

## ⚙️ Configuration & Environment Variables

All settings support standard environment variables with sensible defaults for local development:

| Variable | Description | Default Value |
|---|---|---|
| `MONGODB_URI` | MongoDB connection URI | `mongodb://localhost:27017/placement_db` |
| `MAIL_HOST` | SMTP server host | `localhost` |
| `MAIL_PORT` | SMTP server port | `1025` |
| `MAIL_USERNAME` | SMTP username | *(empty)* |
| `MAIL_PASSWORD` | SMTP password | *(empty)* |
| `SINGLE_OFFER_POLICY` | If `true`, placed students cannot apply to other jobs | `true` |

---

## 🚀 Running the Application (No Docker Needed)

Your system already has MongoDB running as a native Windows service on port `27017`.

### Run the Application
Compile and start the Spring Boot application:
```powershell
.\mvnw.cmd spring-boot:run
```

Once running, access the portal in your browser:
👉 **[http://localhost:8080](http://localhost:8080)**

---

## 🧪 Testing

Run unit tests via:
```powershell
.\mvnw.cmd test
```
Result: **13 tests run, 0 failures, 0 errors, BUILD SUCCESS**.
