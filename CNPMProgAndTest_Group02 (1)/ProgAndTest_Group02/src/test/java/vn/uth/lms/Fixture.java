package vn.uth.lms;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import vn.uth.lms.app.AppContext;
import vn.uth.lms.app.SeedData;
import vn.uth.lms.model.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import vn.uth.lms.persistence.JdbcStore;
import vn.uth.lms.persistence.LibraryStore;
import vn.uth.lms.persistence.MemoryStore;

/**
 * Test data: the demo seed of the application on a movable clock (2026-11-18 09:00).
 * member01 LIB-000123 (2 loans) | member02 LOCKED | member03 PENDING | member04 LIB-000124 (no loans)
 * member05 LIB-000125 (1 overdue loan, 6 days) | member06 LIB-000126 (unpaid fine 14,000 VND).
 *
 * By default the in-memory store is used. When the environment variable TEST_DB_URL is set (see the "tests"
 * service of docker-compose.yml) the same tests run against MySQL, which verifies the JDBC layer too.
 * WARNING: all tables of that database are emptied - use a separate test database.
 */
public class Fixture {
    static {
        System.setProperty("lms.pbkdf2.iterations", "1000");
    }

    public final MutableClock clock = new MutableClock(Instant.parse("2026-11-18T02:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    public final LibraryStore store = createStore();
    public final AppContext app;

    public Fixture() {
        SeedData.seed(store, clock);
        app = new AppContext(store, clock, true);
    }

    private static LibraryStore createStore() {
        String url = System.getenv("TEST_DB_URL");
        if (url == null || url.isBlank()) return new MemoryStore();
        String user = System.getenv().getOrDefault("TEST_DB_USER", "library");
        String password = System.getenv().getOrDefault("TEST_DB_PASSWORD", "library");
        LibraryStore s = new JdbcStore(url, user, password);   // creates the tables when they do not exist
        try (Connection c = DriverManager.getConnection(url, user, password); Statement st = c.createStatement()) {
            st.execute("SET FOREIGN_KEY_CHECKS=0");
            for (String t : new String[]{"fines", "reservations", "loans", "book_copies", "book_authors", "books", "authors",
                    "categories", "publishers", "admins", "librarians", "members", "users", "config"}) st.execute("TRUNCATE TABLE " + t);
            st.execute("SET FOREIGN_KEY_CHECKS=1");
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("Cannot clean the test database", e);
        }
        return s;
    }

    public LocalDate today() { return LocalDate.now(clock); }
    public void advanceDays(int days) { clock.advance(Duration.ofDays(days)); }
    public void advanceMinutes(int minutes) { clock.advance(Duration.ofMinutes(minutes)); }

    public Librarian librarian() { return (Librarian) store.findUserByUsername("staff01"); }
    public Admin admin() { return (Admin) store.findUserByUsername("admin01"); }
    public Member member(String username) { return (Member) store.findUserByUsername(username); }
    public BookCopy copy(String barcode) { return store.findCopyByBarcode(barcode); }

    /** Makes every available copy of the book DAMAGED, so that nobody can borrow it. */
    public void exhaust(int bookId) {
        for (BookCopy c : store.copiesOfBook(bookId)) {
            if (c.getStatus() == CopyStatus.AVAILABLE) { c.updateStatus(CopyStatus.DAMAGED); store.saveCopy(c); }
        }
    }
}
