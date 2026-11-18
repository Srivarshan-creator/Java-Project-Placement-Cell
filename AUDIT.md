# AUDIT LOG

## Audit Entry 1: Standalone MongoDB @Transactional Incompatibility
- **Bug**: `@Transactional` annotations used across service classes (`ApplicationService`, `CompanyService`, `JobService`, `StudentService`).
- **File(s)**:
  - `src/main/java/com/placement/service/ApplicationService.java`
  - `src/main/java/com/placement/service/CompanyService.java`
  - `src/main/java/com/placement/service/JobService.java`
  - `src/main/java/com/placement/service/StudentService.java`
- **Cause**: Standalone MongoDB does not support multi-document transactions unless running as a replica set. Using `@Transactional` risks runtime failures or misleading transaction semantics.
- **Fix**: Remove `@Transactional` annotations from all service classes and handle rollbacks / orphan cleanups explicitly where needed.
- **How verified**: Maven test suite and regression tests.

## Audit Entry 2: Duplicate Email Error Handling & Orphan User Prevention
- **Bug**: When an email already exists (e.g., student or company registration), generic or unhelpful exceptions occur. Furthermore, if student/company entity creation fails after user creation, orphan `User` records remain blocking future registrations.
- **File(s)**:
  - `src/main/java/com/placement/service/StudentService.java`
  - `src/main/java/com/placement/service/CompanyService.java`
  - `src/main/java/com/placement/exception/GlobalExceptionHandler.java`
- **Cause**: Standalone MongoDB lack of transactions means `userRepository.save(user)` followed by an exception leaves the user saved. Also, email duplicate checking must be case-insensitive, trimmed, and mapped to field-level validation errors or clear flash messages.
- **Fix**: Normalization (trim + lowercase), orphan user deletion on downstream failure, duplicate key exception translation.
- **How verified**: Targeted tests for duplicate registrations and case-insensitivity (`srivarshan191007@gmail.com` vs `SriVarshan191007@gmail.com`).

## Audit Entry 3: Shortlisted / In-Progress Candidates Missing Workflow Actions in Company Applicants View
- **Bug**: In `applicants.html`, when an applicant is in `SHORTLISTED` state, the Actions column did not properly display action buttons or schedule buttons, leaving an empty cell or missing primary next-step buttons.
- **File(s)**:
  - `src/main/resources/templates/company/applicants.html`
- **Cause**: Incomplete condition logic in Thymeleaf template for actions when an applicant is `SHORTLISTED` or `IN_PROGRESS`.
- **Fix**: Provide clear, accessible action buttons (Schedule Round, Post Result, Extend Offer, Reject) across `SHORTLISTED` and `IN_PROGRESS` statuses, with proper status indicators for `OFFERED` and `REJECTED`.
- **How verified**: Manual template inspection and MockMvc web view rendering tests.


## Audit Entry 4: Empty Actions Column Due to Thymeleaf 3.1 Event Handler Expression Restriction & Non-Existent Property
- **Bug**: The Actions column was completely empty for every applicant on `/company/jobs/{id}/applicants` across all applicant statuses.
- **File(s)**:
  - `src/main/resources/templates/company/applicants.html`
  - `src/main/java/com/placement/controller/CompanyController.java`
  - `src/main/java/com/placement/service/ApplicationService.java`
- **Root Cause**:
  1. **Thymeleaf 3.1 Attribute Constraint**: Every action button in `applicants.html` used `th:onclick="'openDialog(\'scheduleDialog-' + ${app.id} + '\')'"`. Thymeleaf 3.1 strictly disallows non-numeric, non-boolean dynamic string evaluation inside event handler attributes (such as `th:onclick`) for XSS security. It threw `TemplateProcessingException: Only variable expressions returning numbers or booleans are allowed in this context...`.
  2. **Committed Response Abort**: Because the exception was thrown while rendering the `<td>` of the Actions column after the student, branch, CGPA, and rounds columns had already flushed to the HTTP socket, Spring Boot could not render an error page (`Cannot render error page for request ... as the response has already been committed`). In the user's browser, the Actions cell ended up completely blank.
  3. **Non-existent Model Property**: In the profile dialog, `${studentsMap[app.studentId].rollNumber}` caused SpEL `EL1008E: Property or field 'rollNumber' cannot be found on object of type 'com.placement.model.Student'`.
- **Fix**:
  1. Replaced all `th:onclick` variable concatenations with HTML5 standard data attributes `th:attr="data-dialog-id='scheduleDialog-' + ${app.id}"` and standard `onclick="openDialog(this.getAttribute('data-dialog-id'))"`, with close buttons using `onclick="this.closest('dialog').close()"`.
  2. Fixed candidate profile view to use existing fields (`branch`, `cgpa`, `skills`).
  3. Implemented full actions per status (`APPLIED`: Shortlist, Reject; `SHORTLISTED`: Schedule, Reject; `IN_PROGRESS`: Schedule, Result, Advance, Offer, Reject; `OFFERED`/`REJECTED`: view-only status badges).
  4. Added multi-select checkboxes, Select-All, sticky batch action bar, and batch scheduling modal with live auto-slot preview and conflict detection.
  5. Implemented student-side "My Interviews" page (`/student/interviews`) with confirmation and reschedule requests.
- **How Verified**:
  - Full automated Maven test suite: 28 tests passing (`ApplicantActionsTest`, `RouteSecuritySmokeTest`, `ApplicationFlowTest`, `EligibilityLogicTest`).
  - Python HTTP client integration script performing full end-to-end recruiter and student flow against running server on port 8080.

## Audit Entry 5: Horizontal Scroll on Company Job Applicants Page
- **Bug**: Recruiters were forced to scroll horizontally on the Job Applicants page (`/company/jobs/{id}/applicants`) to view all table columns and reach the Actions column.
- **File(s)**:
  - `src/main/resources/templates/company/applicants.html`
- **Cause**:
  1. The outer page container `.notion-page-content.wide` was constrained to a maximum width of `1120px` while having 64px padding.
  2. The table had a hardcoded `min-width: 1000px` combined with separate columns for Branch, CGPA/Backlogs, Skills, Resume, Status, Rounds Timeline, and Actions, causing the total required width to exceed the container width on standard desktop/laptop viewports.
- **Fix**:
  1. Replaced `.notion-page-content.wide` with full-width responsive container `.applicants-container` (`max-width: 100%; padding: 24px 28px 80px 28px;`).
  2. Combined candidate name and branch badge into a unified **Candidate** column, saving ~70px of horizontal space.
  3. Replaced rigid pixel column constraints with proportional, fluid widths (`width: 100%; table-layout: auto;`).
  4. Streamlined padding (`padding: 9px 10px;`) and compact action buttons (`padding: 4px 7px; font-size: 11.5px;`).
- **How Verified**: Verified live with Python HTTP inspection and browser rendering against the running server; all 28 automated tests remain green.
