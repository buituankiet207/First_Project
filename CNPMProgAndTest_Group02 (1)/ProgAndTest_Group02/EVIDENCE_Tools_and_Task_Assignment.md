# Evidence of tool usage and task assignment – Group 02

Project: **Library Management System** · Stage: Programming & Testing (E-Learning 5)
Team: Nguyễn Bảo Phúc (leader), Nguyễn Đức Doanh, Bùi Tuấn Kiệt, Dương Gia Ân, Trần Anh Tú, Thái Đạt Tường

> **Before submitting:** the parts marked **[TEAM TO ADD]** need real screenshots / links from the team's own GitHub
> repository and tools. They cannot be generated; please replace each placeholder with the real evidence.

## 1. Task assignment (same split as the use-case table of the specification v1.2)

| Member | Use cases | Main code | Unit tests | Manual test cases (xlsx) |
|---|---|---|---|---|
| Dương Gia Ân | 1 Search book, 2 View book details, 3 Register account | `CatalogService.search/getBook`, `AuthService.register`, `controller/CatalogController`, UI `#/search`, `#/book`, `#/register` | `CatalogServiceTest` (search part), `AuthServiceTest` (register) | TC01–TC27 |
| Nguyễn Đức Doanh | 4 Log in, 5 Reset password, 15 Manage staff | `AuthService.login/resetPassword`, `SessionManager`, `StaffService`, `PasswordHasher`, UI `#/login`, `#/forgot`, `#/staff` | `AuthServiceTest` (login, reset), `MemberAndStaffServiceTest` (staff), `UtilTest` | TC28–TC45, TC117–TC126, TC136–TC138 |
| Bùi Tuấn Kiệt | 6 Reserve book, 7 Renew loan, 8 View loan history | `ReservationService`, `LoanService.renew/loansOfMember`, UI `#/loans` | `ReservationServiceTest`, `LoanServiceTest` (renew, history) | TC46–TC61 |
| Trần Anh Tú | 9 Update profile, 10 Manage books, 11 Manage members | `AuthService.updateProfile`, `CatalogService` (manage), `MemberService`, UI `#/profile`, `#/books`, `#/members` | `CatalogServiceTest` (manage), `AuthServiceTest` (profile), `MemberAndStaffServiceTest` (members) | TC62–TC86 |
| Thái Đạt Tường | 12 Lend book, 13 Return book, 14 Collect fine | `LoanService.lend/returnBook`, `FineService`, `CirculationController`, UI `#/lend` | `LoanServiceTest` (lend, return, fine) | TC87–TC116 |
| Nguyễn Bảo Phúc (leader) | 16 View reports; integration, Docker, GitHub, architecture | `ReportService`, `HttpApp`, `Router`, `Main`, `Dockerfile`, `docker-compose.yml`, `pom.xml` | `ReportServiceTest`, `ApiIntegrationTest`, `DomainTest` | TC127–TC135 |

**[TEAM TO ADD]** Screenshot of the GitHub Projects / Issues board (or the task list) showing the same assignment.

## 2. Tools applied

| Stage | Tool | Used for | Evidence |
|---|---|---|---|
| Team, code, documents | **GitHub** | repository, branches per member, pull requests, issues | **[TEAM TO ADD]** screenshots: commit history, branch list, a merged pull request, issue list |
| Analysis & design | diagrams.net | DFD, use case, class diagram, data model (already in the specification v1.2) | `RequirementSpecificationAndDesignDocument_Group02.docx` |
| GUI design | Figma | screen mock-ups | **[TEAM TO ADD]** link / screenshot |
| Implementation | JDK 21 + IDE (VS Code / IntelliJ …) | Java source in `src/main/java` | **[TEAM TO ADD]** screenshot of the IDE with the project |
| Build | Maven | `pom.xml`, one runnable jar (`target/library.jar`) | `pom.xml` |
| Test cases | **Excel** (course template) | 138 manual test cases | `TestingDocument_Group02.xlsx` |
| Unit testing | **JUnit 5** | 163 automated tests (domain, services, HTTP API) | see section 3 |
| UI testing | headless Chromium (Playwright) | 25 end-to-end steps through the web screens | screenshots in `docs/screenshots/` |
| Bug tracking | Jira / Bugzilla / GitHub Issues | defects found while testing | section 4 (and **[TEAM TO ADD]** screenshot of the tracker) |
| Packaging & deployment | **Docker**, Docker Compose | image of the application + MySQL, test profile | `Dockerfile`, `docker-compose.yml`; **[TEAM TO ADD]** screenshot of `docker compose up` and of the program at `localhost:8080` |

## 3. Test execution results (recorded while building this package)

| Suite | Result |
|---|---|
| JUnit 5 tests on the in-memory store | **163 run, 163 passed, 0 failed** (domain 10, util 8, auth 23, catalog 28, reservation 13, loan/fine 35, member/staff 20, report 8, HTTP API 18) |
| Browser end-to-end run (guest, member, librarian, admin) | **25 of 25 steps passed**, no JavaScript errors; screenshots `docs/screenshots/01…12` |
| Same JUnit suite on MySQL | run with `docker compose --profile test run --rm tests` — **[TEAM TO ADD]** screenshot of the result |
| Manual test cases (`TestingDocument_Group02.xlsx`) | **[TEAM TO ADD]** execution result per test case, if the course asks for it |

## 4. Defects found and fixed during testing

| ID | Found by | Defect | Fix | Status |
|---|---|---|---|---|
| BUG-01 | browser test | the word "null" appeared under result tables and slips (empty values passed to the DOM) | empty values are ignored when building pages (`app.js`) | fixed, retested |
| BUG-02 | code review before the browser run | after a successful lending the loan slip failed to render the lent books | slip built from the server answer | fixed, retested |
| BUG-03 | code review before the browser run | after collecting a fine in the *Return* screen the "Collect payment" button stayed active | row switches to PAID after payment | fixed, retested |
| BUG-04 | unit test run | a test looked up a deleted account by its old username (test error, not product error) | test keeps the id before deleting | fixed |

**[TEAM TO ADD]** further defects found by the team while running the manual test cases.
