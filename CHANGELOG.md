# CHANGELOG

All notable changes to the College Placement Management System are documented here.

## [Unreleased] - 2026-11-11

### Fixed
- **Image Bug 1**: Corrected action buttons in `company/applicants.html` for Shortlisted candidates so Schedule, Result, Offer, and Reject options are always accessible and styled.
- **Image Bug 2**: Enhanced email existence handling during company/student onboarding, ensuring consistent lowercase trimming, distinct error reporting, and no orphaned user records.
- **MongoDB Compatibility**: Removed all `@Transactional` annotations across services for standalone MongoDB deployment compatibility.

### Added
- Comprehensive automated integration/smoke test suite covering all roles and endpoints.
- Route map documentation in `ROUTE_MAP.md`.


## [1.1.0] - 2026-11-11

### Fixed
- **Horizontal Scroll Eliminated on Company Applicants View**: Streamlined table layout in `applicants.html` by combining Candidate & Branch tags, using fluid 100% width, compacting Academics/Skills/Rounds padding, and removing the rigid `min-width: 1000px` and `max-width: 1120px` constraints.
- **Empty Actions Column**: Resolved Thymeleaf 3.1 strict attribute restriction where `th:onclick` with dynamic string concatenation threw `TemplateProcessingException`, aborting rendering of the Actions cell. Switched to `data-dialog-id` attributes and standard event handlers.
- **Student Profile SpEL Property**: Replaced non-existent `rollNumber` lookup with valid `branch` and `cgpa` fields in `applicants.html`.
- **Database Seeder Test Resilience**: Added safe null checks in `DatabaseSeeder.java` to prevent NPEs when repository mocks return null during unit/smoke test contexts.

### Added
- **Full Actions by Status**: Explicit, role-checked action buttons for APPLIED, SHORTLISTED, IN_PROGRESS, OFFERED, and REJECTED statuses.
- **Multi-Select & Sticky Action Bar**: Table checkbox column with Select-All, sticky bottom bar with "Shortlist Selected", "Reject Selected", and "Schedule Interview for Selected".
- **Batch & Auto-Slot Interview Scheduling**: Support for `SAME_SLOT` and sequential `AUTO_SLOTS` (custom slot length and break minutes) with conflict detection, Asia/Kolkata timezone verification, and dynamic live preview table.
- **Sync with `interviews` Collection & `.ics` Invites**: Every interview is recorded in the `interviews` collection with status, score, and feedback, and calendar invites (.ics) are emailed asynchronously to students.
- **Student "My Interviews" Workspace**: Accessible at `/student/interviews` with attendance confirmation and reschedule requests.
- **Automated Test Suite**: Added `ApplicantActionsTest` covering valid and invalid state transitions, conflict detection, auto-slot generation, and role ownership checks (28 total tests passing).
