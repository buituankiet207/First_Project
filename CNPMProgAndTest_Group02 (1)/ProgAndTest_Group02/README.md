# Library Management System – Group 02

Programming & Testing stage of the group project (Introduction to Software Engineering, UTH).
The program implements the **Requirement Specification and Design Document v1.2** of Group 02:
16 use cases, 4 actors (Guest, Member, Librarian, Admin), a web graphical user interface and a MySQL database.

| | |
|---|---|
| Language | Java 21 (only the JDK + the MySQL driver at run time, JUnit 5 for tests) |
| GUI | Web UI (HTML5 / CSS3 / JavaScript) served by the program – open `http://localhost:8080` |
| Database | MySQL 8.4 (the in-memory store is available for a quick demo / unit tests) |
| Architecture | **Layered architecture** (Presentation – Business – Persistence – Database); the presentation layer follows **MVC** (both patterns of Chapter 3) |
| Packaging | Maven (`pom.xml`), Docker (`Dockerfile`, `docker-compose.yml`) |

## 1. Run it

### With Docker (recommended)
```bash
docker compose up --build
```
Open **http://localhost:8080**. MySQL and the application start together; the tables and the demo data are created
automatically on the first start. Stop with `Ctrl+C`, remove the data with `docker compose down -v`.

### Without Docker (demo mode, no database)
```bash
mvn -DskipTests package
STORAGE=memory java -jar target/library.jar          # Windows PowerShell:  $env:STORAGE="memory"; java -jar target/library.jar
```

### Demo accounts (password of all accounts: `123456`)
| Username | Role | State / data |
|---|---|---|
| `admin01` | Admin | |
| `staff01` | Librarian | |
| `member01` | Member, card `LIB-000123` | 2 active loans, 2 returned loans |
| `member02` | Member, card `LIB-000122` | **LOCKED** |
| `member03` | Member, no card | **PENDING** (waits for activation) |
| `member04` | Member, card `LIB-000124` | no loans (free to borrow / reserve) |
| `member05` | Member, card `LIB-000125` | 1 **overdue** loan (6 days, copy `BC-000014`) |
| `member06` | Member, card `LIB-000126` | unpaid fine 14,000 VND; has the only copy of "Design Systems" (`BC-000018`) |

Barcodes have the form `BC-000005` (copy id). The e-mail of "Reset password" is printed in the console and shown at
`/api/dev/outbox` (when `DEV_MODE=true`, set in `docker-compose.yml`).

## 2. Tests

```bash
mvn test                                   # 163 JUnit 5 tests on the in-memory store (no database needed)
docker compose --profile test run --rm tests   # the same 163 tests on MySQL (database `library_test`) - verifies the JDBC layer
```
The manual **test cases** (138, in the course template) are in `TestingDocument_Group02.xlsx`; their sample data is
the demo data above. Screenshots of the running program are in `docs/screenshots`.

## 3. Architecture

```
 Browser (View)                  src/main/resources/web   index.html, style.css, app.js
    │  JSON over HTTP
 ┌──▼──────────────────────────────────────────────────────────────────────────────────────┐
 │ Presentation   web/HttpApp, Router (login, roles, security headers)                      │
 │                controller/Auth-, Catalog-, Circulation-, AdminController   (Controller)  │
 ├──────────────────────────────────────────────────────────────────────────────────────────┤
 │ Business       service/AuthService, CatalogService, ReservationService, LoanService,     │
 │                FineService, MemberService, StaffService, ReportService   (use cases)    │
 │ Model          model/  User ◁ Member, Librarian ◁ Admin, Book, BookCopy, Loan, Fine …    │
 ├──────────────────────────────────────────────────────────────────────────────────────────┤
 │ Persistence    persistence/LibraryStore (DAO interface) ◁ JdbcStore, MemoryStore         │
 ├──────────────────────────────────────────────────────────────────────────────────────────┤
 │ Database       MySQL 8.4 (schema.sql)                                                    │
 └──────────────────────────────────────────────────────────────────────────────────────────┘
```
Each use case has one service method that holds the rules of the use-case description (main flow and alternative flows);
controllers only translate HTTP ⇄ service calls; services never touch SQL.

### How this follows Chapter 3 (Software design)
| Chapter 3 topic | In this project |
|---|---|
| **Layered model** (Presentation / Business / Persistence / Database layers; a layer serves the layer above; a whole layer can be replaced) | packages `web`+`controller` → `service`+`model` → `persistence` → MySQL. A layer only calls the layer below. The persistence layer is replaceable: `STORAGE=memory` swaps `JdbcStore` for `MemoryStore` without changing one line above it (the same tests run on both) |
| **MVC model** (Controller receives the request, manipulates the Model, sends data to the View, View responds) | **View** = `web/` (browser: HTML/CSS/JS); **Controller** = `controller/*Controller` + `Router`; **Model** = `model/` entities + `service/` rules + `persistence/` (CRUD). Flow: (1) browser request → (2) controller calls the service → (3) data goes back as JSON → (4) the View draws the page |
| Cohesion / coupling | one service per group of related use cases (high cohesion); layers talk through interfaces (`LibraryStore`, `EmailService`) and plain data (low coupling); controllers never see SQL |
| Why not the others | *Repository (data-centric)*: our subsystems must apply business rules, not share raw data. *Client–Server of several services*: one small system, one server is enough |
| **Database design**: class → table, property → column | one table per entity class (`books`, `loans`, `fines` …) |
| Inheritance → one table per class, children reference the parent key | `users` (common data) ← `members`, `librarians` ← `admins` (same primary key) |
| n–n → intermediate table | `book_authors` (books ↔ authors) |
| 1–n → reference (foreign) key | `book_copies.book_id`, `loans.member_id / copy_id`, `fines.loan_id`, `reservations.member_id / book_id` |
| **Interface design** (golden rules, error handling, "where am I?") | user in control: *Cancel* buttons, confirmation before deleting, *Remove* in the lending list; less memory load: placeholders, suggestions for category / publisher, sensible defaults; consistency: same layout, buttons and messages on every screen; error messages next to the wrong field (highlighted); progress / result messages (toast); navigation shows the current page |

### Class diagram → code
| Class diagram | Code |
|---|---|
| `User` (abstract), `Member`, `Librarian`, `Admin` (extends Librarian) | `model/User, Member, Librarian, Admin` – same attributes and inheritance |
| `Book`, `BookCopy`, `Category`, `Publisher`, `Author`, `Loan`, `Fine`, `Reservation` | `model/…` – same attributes |
| `Loan.renew() / isOverdue() / calculateFine()`, `Fine.calculateAmount() / markPaid()`, `Reservation.create() / cancel() / isExpired()`, `BookCopy.isAvailable() / updateStatus()`, `Book.getAvailableCopies()` | implemented in the entity classes |
| `User.login() / logout() / updateProfile() / resetPassword()` | entity method → `AuthService` |
| `Member.reserveBook() / renewLoan() / viewLoanHistory()` | entity method → `ReservationService`, `LoanService` |
| `Librarian.lendBook() / returnBook() / collectFine() / manageBooks() / manageMembers()` | entity method → `LoanService`, `FineService`, `CatalogService`, `MemberService` |
| `Admin.manageStaff() / viewReports()` | entity method → `StaffService`, `ReportService` |
| `Book.addBook() / updateBook() / deleteBook()`, `Category/Publisher/Author.add… / update… / delete…` | `CatalogService.addBook()`, `updateBook()`, `deleteBook()`, `addCategory()` … |

### Use case → code
| Use case | Service | Screen (route) |
|---|---|---|
| 1 Search book, 2 View book details | `CatalogService.search / getBook` | `#/search`, `#/book/{id}` |
| 3 Register account, 4 Log in, 5 Reset password | `AuthService.register / login / resetPassword` | `#/register`, `#/login`, `#/forgot` |
| 6 Reserve book | `ReservationService` | book details, `#/loans` |
| 7 Renew loan, 8 View loan history | `LoanService.renew / loansOfMember` | `#/loans` |
| 9 Update profile | `AuthService.updateProfile` | `#/profile` |
| 10 Manage books | `CatalogService` | `#/books` |
| 11 Manage members | `MemberService` | `#/members` |
| 12 Lend book, 13 Return book | `LoanService.lend / returnBook` | `#/lend` |
| 14 Collect fine | `FineService.collectFine` | `#/lend` (return result and "Collect fines") |
| 15 Manage staff | `StaffService` | `#/staff` |
| 16 View reports | `ReportService` | `#/reports` |

## 4. Decisions where the specification v1.2 was open or inconsistent

| Topic | Decision implemented |
|---|---|
| Fine rate | stored in the table `config` (`fine_per_day`, default 1,000 VND); the admin changes it in *Reports → Settings* |
| Damaged / lost book | the librarian enters the replacement price when returning; it becomes the fine amount |
| Lock after failed logins | 5 wrong passwords in a row lock the account for 15 minutes (`users.failed_attempts`, `locked_until`); status `LOCKED` (set by a librarian) is separate |
| Reservation life cycle | `WAITING` → `READY` (copy kept as `RESERVED` for 3 days for the first member in the queue) → `FULFILLED`; or `CANCELLED` / `EXPIRED`. Expiry is checked whenever the book data is used |
| Library card | issued at activation (card number typed by the librarian, valid 1 year). A `PENDING` member has no card, so lending is refused because the card is unknown; the "not ACTIVE" case is shown with a `LOCKED` member. An expired card is renewed with *Activate card* |
| Lending | several copies can be lent at once; the limit of 5 counts the loans already active; nothing is saved if one copy is refused |
| Deleting | books, members and staff are *hidden* (flag `deleted`), so loan history stays complete. A book with active loans/reservations, a member with active loans/unpaid fines, the own account and the last admin cannot be deleted |
| Search | one box with "search by" (All, Title, Author, Category, ISBN), optional category filter and sort; ignores case and Vietnamese accents; 10 results per page; empty keyword = all books, newest first |
| Passwords | PBKDF2-HMAC-SHA256 with a random 128-bit salt (≥ 160-bit hash requirement of NFR-SE1); temporary password of 10 characters |
| E-mail | demo implementation prints to the console / outbox; replace `EmailService` with an SMTP implementation for production |

### Database changes compared with the ERD of v1.2
`users` + `failed_attempts`, `locked_until`, `created_at`, `deleted`; `books` + `title_norm`, `deleted`; `categories/authors/publishers` +
`name_norm`; `book_authors` + `author_order`; new table `config`. `fines` references `loans` as 0..* (as in the class diagram).
The full schema is `src/main/resources/schema.sql` (created automatically).

## 5. Project structure
```
ProgAndTest_Group02/
├─ src/main/java/vn/uth/lms/   app/ (Main, wiring, demo data)  controller/  service/  model/  persistence/  web/  util/
├─ src/main/resources/         schema.sql, web/ (index.html, style.css, app.js)
├─ src/test/java/vn/uth/lms/   JUnit 5 tests (domain, services, API over HTTP)
├─ TestingDocument_Group02.xlsx   test cases (course template)
├─ EVIDENCE_Tools_and_Task_Assignment.md
├─ Dockerfile, docker-compose.yml, docker/mysql-init/, pom.xml
└─ docs/screenshots/
```
