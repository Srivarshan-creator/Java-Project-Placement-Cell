# Complete Application Route Map

This document catalogs every URL, HTTP method, required role/authorization, associated controller method, Thymeleaf template rendered, and service operations invoked.

---

## 1. Authentication & Public Endpoints (`AuthController`)

| HTTP Method | URL Path | Allowed Role | Controller Method | Template Rendered | Services Called | Notes |
|---|---|---|---|---|---|---|
| `GET` | `/` | Any | `home()` | *Redirects to dashboard or `/login`* | N/A | Landing router based on authenticated principal |
| `GET` | `/login` | Anonymous / Any | `loginPage()` | `auth/login.html` | N/A | Role selection & login form |
| `POST` | `/login` | Public | *Spring Security filter* | *Redirects via `RoleBasedAuthenticationSuccessHandler`* | `CustomUserDetailsService` | Form login processing |
| `POST` | `/logout` | Authenticated | *Spring Security filter* | *Redirects to `/login?logout=true`* | N/A | Session invalidation and cookie clearing |
| `GET` | `/register` | Anonymous / Any | `registerPage()` | `auth/register.html` | N/A | Student self-registration form |
| `POST` | `/register` | Public | `handleRegister()` | `auth/register.html` (on error) or redirect `/login` | `StudentService.registerStudent` | Validates & persists student + user |
| `GET` | `/register/company` | Anonymous / Any | `registerCompanyPage()` | `auth/register-company.html` | N/A | Company self-registration form |
| `POST` | `/register/company` | Public | `handleRegisterCompany()` | `auth/register-company.html` (on error) or redirect `/login` | `CompanyService.registerCompany` | Validates & persists company + user |

---

## 2. Student Workspace (`StudentController`, `@PreAuthorize("hasRole('STUDENT')")`)

| HTTP Method | URL Path | Allowed Role | Controller Method | Template Rendered | Services Called | Notes |
|---|---|---|---|---|---|---|
| `GET` | `/student/dashboard` | `STUDENT` | `dashboard()` | `student/dashboard.html` | `StudentService`, `JobService`, `ApplicationService` | KPI metrics, latest eligible jobs & recent applications |
| `GET` | `/student/jobs` | `STUDENT` | `eligibleJobs()` | `student/jobs.html` | `StudentService`, `JobService`, `CompanyService` | Filtered list of eligible approved jobs |
| `GET` | `/student/jobs/{id}` | `STUDENT` | `jobDetails()` | `student/job-details.html` | `JobService`, `CompanyService`, `StudentService`, `ApplicationService` | Job spec, eligibility badge, apply form |
| `POST` | `/student/jobs/{id}/apply` | `STUDENT` | `applyToJob()` | *Redirects to `/student/applications`* | `ApplicationService.applyToJob` | Server-side eligibility & duplicate validation |
| `GET` | `/student/applications` | `STUDENT` | `myApplications()` | `student/applications.html` | `StudentService`, `ApplicationService`, `JobService`, `CompanyService` | Application progress tracker with round details |
| `GET` | `/student/profile` | `STUDENT` | `profilePage()` | `student/profile.html` | `StudentService` | Profile details, skills & resume download/upload |
| `POST` | `/student/profile` | `STUDENT` | `updateProfile()` | `student/profile.html` (on error) or redirect `/student/profile` | `StudentService.updateProfile` | Updates CGPA, backlogs, skills |
| `POST` | `/student/resume/upload` | `STUDENT` | `uploadResume()` | *Redirects to `/student/profile`* | `StudentService.uploadResume` | Uploads PDF (max 2MB) to GridFS |
| `GET` | `/student/resume/download` | `STUDENT` | `downloadMyResume()` | *PDF binary stream* | `StudentService.downloadResume` | Stream own resume PDF |

---

## 3. Company Recruiter Workspace (`CompanyController`, `@PreAuthorize("hasRole('COMPANY')")`)

| HTTP Method | URL Path | Allowed Role | Controller Method | Template Rendered | Services Called | Notes |
|---|---|---|---|---|---|---|
| `GET` | `/company/dashboard` | `COMPANY` | `dashboard()` | `company/dashboard.html` | `CompanyService`, `JobService`, `ApplicationService` | Overview metrics (jobs, applicants, offers) |
| `GET` | `/company/jobs` | `COMPANY` | `myJobs()` | `company/jobs.html` | `CompanyService`, `JobService`, `ApplicationService` | Company's posted jobs with status & applicant counts |
| `GET` | `/company/jobs/new` | `COMPANY` | `newJobForm()` | `company/job-form.html` | N/A | Form to post new job opening |
| `POST` | `/company/jobs/new` | `COMPANY` | `createJob()` | `company/job-form.html` (on error) or redirect `/company/jobs` | `JobService.createJob` | Sets status PENDING for placement cell approval |
| `GET` | `/company/jobs/edit/{id}` | `COMPANY` | `editJobForm()` | `company/job-form.html` | `JobService`, `CompanyService` | Form to edit existing job |
| `POST` | `/company/jobs/edit/{id}` | `COMPANY` | `updateJob()` | `company/job-form.html` (on error) or redirect `/company/jobs` | `JobService.updateJob` | Updates job, resets status to PENDING |
| `POST` | `/company/jobs/{id}/close` | `COMPANY` | `closeJob()` | *Redirects to `/company/jobs`* | `JobService.closeJob` | Closes job from further applications |
| `GET` | `/company/jobs/{id}/applicants` | `COMPANY` | `viewApplicants()` | `company/applicants.html` | `JobService`, `ApplicationService`, `StudentService` | Applicant list with filters & action modals |
| `POST` | `/company/applications/{id}/status`| `COMPANY` | `updateStatus()` | *Redirects to `/company/jobs/{jobId}/applicants`* | `ApplicationService.updateApplicationStatus` | Update status (e.g. SHORTLISTED, REJECTED) |
| `POST` | `/company/applications/{id}/rounds`| `COMPANY` | `scheduleRound()` | *Redirects to `/company/jobs/{jobId}/applicants`* | `ApplicationService.scheduleRound` | Add round & send email notification |
| `POST` | `/company/applications/{id}/round-result`| `COMPANY` | `submitRoundResult()` | *Redirects to `/company/jobs/{jobId}/applicants`* | `ApplicationService.submitRoundResult` | Record CLEARED/FAILED/PENDING |
| `POST` | `/company/applications/{id}/offer` | `COMPANY` | `makeOffer()` | *Redirects to `/company/jobs/{jobId}/applicants`* | `ApplicationService.makeOffer` | Extend offer, marks student PLACED |
| `GET` | `/company/students/{studentId}/resume`| `COMPANY` | `downloadStudentResume()`| *PDF binary stream* | `StudentService.downloadResume` | Stream applicant PDF resume |

---

## 4. Placement Cell Administration (`AdminController`, `@PreAuthorize("hasRole('PLACEMENT_CELL')")`)

| HTTP Method | URL Path | Allowed Role | Controller Method | Template Rendered | Services Called | Notes |
|---|---|---|---|---|---|---|
| `GET` | `/admin/dashboard` | `PLACEMENT_CELL` | `dashboard()` | `admin/dashboard.html` | `AnalyticsService`, `JobService`, `CompanyService` | Branch KPIs, aggregates, pending jobs queue |
| `GET` | `/admin/jobs/pending` | `PLACEMENT_CELL` | `pendingJobs()` | `admin/pending-jobs.html` | `JobService`, `CompanyService` | List of jobs awaiting review |
| `POST` | `/admin/jobs/approve/{id}`| `PLACEMENT_CELL` | `approveJob()` | *Redirects to `/admin/jobs/pending`* | `JobService.approveJob` | Approves job, notifies eligible students via email |
| `POST` | `/admin/jobs/reject/{id}` | `PLACEMENT_CELL` | `rejectJob()` | *Redirects to `/admin/jobs/pending`* | `JobService.rejectJob` | Rejects job with feedback reason sent to company |
| `GET` | `/admin/jobs` | `PLACEMENT_CELL` | `allJobs()` | `admin/all-jobs.html` | `JobService`, `CompanyService` | Search/filter all institutional jobs |
| `GET` | `/admin/companies` | `PLACEMENT_CELL` | `allCompanies()` | `admin/companies.html` | `CompanyService`, `JobService` | Registered partner companies & job counts |
| `GET` | `/admin/companies/new` | `PLACEMENT_CELL` | `newCompanyForm()`| `admin/company-form.html` | N/A | Form to onboard company |
| `POST` | `/admin/companies/new` | `PLACEMENT_CELL` | `createCompany()` | `admin/company-form.html` (on error) or redirect `/admin/companies` | `CompanyService.createCompany` | Creates user, company, emails temp password |
| `GET` | `/admin/students` | `PLACEMENT_CELL` | `allStudents()` | `admin/students.html` | `StudentService` | Filterable directory of students |
| `GET` | `/admin/applications` | `PLACEMENT_CELL` | `allApplications()` | `admin/applications.html` | `ApplicationService`, `StudentService`, `JobService`, `CompanyService` | Global audit log of all placement applications |
| `GET` | `/admin/students/{studentId}/resume`| `PLACEMENT_CELL` | `downloadStudentResume()`| *PDF binary stream* | `StudentService.downloadResume` | Stream student resume PDF |

---

## 5. Error Pages

| Error Status | Template Rendered | Handler |
|---|---|---|
| 403 Forbidden | `error/403.html` | `GlobalExceptionHandler.handleAccessDenied`, `handleUnauthorizedAction` |
| 404 Not Found | `error/404.html` | `GlobalExceptionHandler.handleNotFound` |
| 500 Internal Error | `error/500.html` | `GlobalExceptionHandler.handleGeneralException` |

