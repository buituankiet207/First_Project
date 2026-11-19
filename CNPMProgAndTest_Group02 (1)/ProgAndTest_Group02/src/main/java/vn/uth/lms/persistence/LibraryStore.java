package vn.uth.lms.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import vn.uth.lms.model.*;

/**
 * Data-access layer. Two implementations exist: {@link MemoryStore} (tests / demo without a database)
 * and {@link JdbcStore} (MySQL). Services never talk to a database directly.
 */
public interface LibraryStore {

    /** Runs the work as one atomic unit (database transaction). */
    <T> T tx(Supplier<T> work);

    // ------------------------------------------------------------------ users
    User findUser(int id);
    User findUserByUsername(String username);
    User findUserByEmail(String email);
    Member findMemberByCard(String cardNumber);
    boolean staffCodeExists(String staffCode);
    /** Inserts when userId == 0, otherwise updates (including the member / librarian / admin data). */
    User saveUser(User user);
    /** Members that are not deleted; keyword matches name, username or card number (empty = all). */
    List<Member> searchMembers(String keyword);
    /** Librarians and admins that are not deleted. */
    List<Librarian> listStaff();

    // ---------------------------------------------------------------- catalog
    RefRepository<Category> categories();
    RefRepository<Author> authors();
    RefRepository<Publisher> publishers();

    Book findBook(int id);
    Book findBookByIsbn(String isbn);
    Book saveBook(Book book);
    /** keyword must already be normalized (see TextUtil). categoryId 0 = all categories. */
    PageResult<Book> searchBooks(String keyword, SearchType type, int categoryId, boolean newestFirst, int page, int pageSize);

    BookCopy findCopy(int id);
    BookCopy findCopyByBarcode(String barcode);
    List<BookCopy> copiesOfBook(int bookId);
    /** Inserts when copyId == 0 (the barcode is generated as BC-xxxxxx), otherwise updates. */
    BookCopy saveCopy(BookCopy copy);
    void deleteCopy(int copyId);
    boolean copyHasLoans(int copyId);

    // ------------------------------------------------------------------ loans
    Loan findLoan(int id);
    Loan saveLoan(Loan loan);
    Loan findActiveLoanByCopy(int copyId);
    /** All loans of a member, newest first. */
    List<Loan> loansOfMember(int memberId);
    List<Loan> activeLoansOfMember(int memberId);
    boolean bookHasActiveLoan(int bookId);
    boolean bookHasLoanHistory(int bookId);

    // ------------------------------------------------------------------ fines
    Fine findFine(int id);
    Fine saveFine(Fine fine);
    List<Fine> finesOfLoan(int loanId);
    /** All fines of a member, newest first. */
    List<Fine> finesOfMember(int memberId);

    // --------------------------------------------------------- reservations
    Reservation findReservation(int id);
    Reservation saveReservation(Reservation reservation);
    /** WAITING or READY reservations of a book, oldest first. */
    List<Reservation> activeReservationsOfBook(int bookId);
    List<Reservation> activeReservationsOfMember(int memberId);
    /** All reservations of a member, newest first. */
    List<Reservation> reservationsOfMember(int memberId);
    List<Reservation> expiredReady(LocalDateTime now);

    // ---------------------------------------------------------------- reports
    /** Rows: {bookId, title, isbn, loanCount}, most borrowed first. */
    List<Object[]> mostBorrowed(LocalDate from, LocalDate to, int limit);
    List<Loan> overdueLoans(LocalDate from, LocalDate to, LocalDate today);
    List<Fine> paidFinesBetween(LocalDateTime from, LocalDateTime to);
    /** Keys: totalMembers, activeMembers, newMembers, borrowers, loans. */
    Map<String, Long> memberStats(LocalDate from, LocalDate to);

    // ----------------------------------------------------------------- config
    String getConfig(String key, String defaultValue);
    void setConfig(String key, String value);
}
