package vn.uth.lms.app;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import vn.uth.lms.model.*;
import vn.uth.lms.persistence.LibraryStore;
import vn.uth.lms.util.PasswordHasher;

/** Demo data: accounts (password 123456), a small catalog, loans, one overdue loan and one unpaid fine. */
public final class SeedData {
    private SeedData() {}

    public static final String PASSWORD = "123456";

    public static boolean seed(LibraryStore store, Clock clock) {
        if (store.findUserByUsername("admin01") != null) return false;
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock);
        String hash = PasswordHasher.hash(PASSWORD);

        // ---- staff
        Admin admin = new Admin();
        admin.setAdminLevel(1);
        staff(admin, "admin01", hash, "Tran Quoc Admin", "admin01@library.vn", "0901000001", "ST001", now);
        store.saveUser(admin);
        Librarian staff01 = new Librarian();
        staff(staff01, "staff01", hash, "Le Thi Thu Thu", "staff01@library.vn", "0901000002", "ST002", now);
        store.saveUser(staff01);

        // ---- members
        Member m1 = member(store, "member01", hash, "Nguyen Van A", "vana@gmail.com", "0911000001", "LIB-000123", AccountStatus.ACTIVE, today, now);
        Member m2 = member(store, "member02", hash, "Tran Thi B", "thib@gmail.com", "0911000002", "LIB-000122", AccountStatus.LOCKED, today, now);
        member(store, "member03", hash, "Pham Van C", "vanc@gmail.com", "0911000003", null, AccountStatus.PENDING, today, now);
        member(store, "member04", hash, "Le Thi D", "thid@gmail.com", "0911000004", "LIB-000124", AccountStatus.ACTIVE, today, now);
        Member m5 = member(store, "member05", hash, "Hoang Van E", "vane@gmail.com", "0911000005", "LIB-000125", AccountStatus.ACTIVE, today, now);
        Member m6 = member(store, "member06", hash, "Vo Thi F", "thif@gmail.com", "0911000006", "LIB-000126", AccountStatus.ACTIVE, today, now);

        // ---- catalog
        String[][] books = {
                {"978-0-465-05065-9", "The Design of Everyday Things", "Don Norman", "UI/UX Design", "Basic Books", "2013", "3", "A1", "A classic about human-centered design."},
                {"978-0-13-235088-4", "Clean Code: A Handbook", "Robert C. Martin", "Programming", "Prentice Hall", "2008", "3", "A1", "A comprehensive guide to software craftsmanship, teaching developers how to write clean, readable, and easily maintainable code."},
                {"978-0-321-96551-6", "Don't Make Me Think", "Steve Krug", "UI/UX Design", "New Riders", "2014", "5", "A2", "A common sense approach to web usability."},
                {"978-0-262-03384-8", "Introduction to Algorithms", "Thomas H. Cormen,Charles E. Leiserson,Ronald L. Rivest,Clifford Stein", "Computer Science", "MIT Press", "2009", "2", "B1", "The standard textbook on algorithms."},
                {"978-0-13-475759-9", "Refactoring: Improving the Design of Existing Code", "Martin Fowler", "Software Engineering", "Addison-Wesley", "2018", "4", "B2", "Techniques to improve code without changing its behavior."},
                {"978-3-945749-60-0", "Design Systems", "Alla Kholmatova", "Web Development", "Smashing Magazine", "2017", "1", "C1", "Creating effective design systems for digital products."},
                {"978-1-292-06118-4", "Database Systems", "Thomas Connolly,Carolyn Begg", "Database", "Pearson", "2014", "2", "C2", "A practical approach to design, implementation and management."},
                {"978-1-259-67651-2", "Discrete Mathematics", "Kenneth H. Rosen", "Mathematics", "McGraw-Hill", "2018", "4", "D1", "Discrete mathematics and its applications."},
                {"978-604-0-12345-6", "Lập trình Java", "Nguyễn Văn Hiếu", "Programming", "NXB Giáo dục", "2020", "2", "A3", "Giáo trình lập trình hướng đối tượng với Java."},
        };
        List<Book> saved = new ArrayList<>();
        for (String[] b : books) saved.add(book(store, b));
        Book clean = saved.get(1), algo = saved.get(3), design = saved.get(5), norman = saved.get(0), krug = saved.get(2), fowler = saved.get(4);

        // ---- loans: member01 has 2 active loans and some history
        loan(store, m1, copy(store, clean, 0), staff01, today.minusDays(4), null, LoanStatus.BORROWED);
        loan(store, m1, copy(store, algo, 0), staff01, today.minusDays(2), null, LoanStatus.BORROWED);
        loan(store, m1, copy(store, norman, 0), staff01, today.minusDays(40), today.minusDays(30), LoanStatus.RETURNED);
        loan(store, m1, copy(store, krug, 0), staff01, today.minusDays(60), today.minusDays(50), LoanStatus.RETURNED);
        // member05 has an overdue loan (cannot borrow)
        loan(store, m5, copy(store, fowler, 0), staff01, today.minusDays(20), null, LoanStatus.BORROWED);
        // member06 borrows the only copy of "Design Systems" and has an unpaid fine from an earlier late return
        loan(store, m6, copy(store, design, 0), staff01, today.minusDays(7), null, LoanStatus.BORROWED);
        Loan late = loan(store, m6, copy(store, krug, 1), staff01, today.minusDays(40), today.minusDays(12), LoanStatus.RETURNED);
        Fine fine = new Fine();
        fine.setLoanId(late.getLoanId());
        fine.setAmount(14000);
        fine.setReason(FineReason.OVERDUE);
        fine.setStatus(FineStatus.UNPAID);
        fine.setCreatedAt(now.minusDays(12));
        store.saveFine(fine);
        store.setConfig("fine_per_day", String.valueOf(Rules.DEFAULT_FINE_PER_DAY));
        return true;
    }

    private static void staff(Librarian l, String username, String hash, String name, String email, String phone, String code, LocalDateTime now) {
        l.setUsername(username);
        l.setPasswordHash(hash);
        l.setFullName(name);
        l.setEmail(email);
        l.setPhone(phone);
        l.setStaffCode(code);
        l.setHireDate(now.toLocalDate().minusYears(2));
        l.setStatus(AccountStatus.ACTIVE);
        l.setCreatedAt(now);
    }

    private static Member member(LibraryStore s, String username, String hash, String name, String email, String phone, String card,
                                 AccountStatus status, LocalDate today, LocalDateTime now) {
        Member m = new Member();
        m.setUsername(username);
        m.setPasswordHash(hash);
        m.setFullName(name);
        m.setEmail(email);
        m.setPhone(phone);
        m.setAddress("12 Nguyen Hue, Ho Chi Minh City");
        m.setDateOfBirth(LocalDate.of(2004, 5, 15));
        m.setStatus(status);
        m.setCreatedAt(now.minusDays(30));
        if (card != null) {
            m.setCardNumber(card);
            m.setCardExpiry(today.plusMonths(10));
        }
        return (Member) s.saveUser(m);
    }

    private static Book book(LibraryStore s, String[] f) {
        Book b = new Book();
        b.setIsbn(f[0]);
        b.setTitle(f[1]);
        List<Author> as = new ArrayList<>();
        for (String name : f[2].split(",")) {
            Author a = s.authors().findByName(name);
            if (a == null) { a = new Author(); a.setName(name.trim()); s.authors().save(a); }
            as.add(a);
        }
        b.setAuthors(as);
        Category c = s.categories().findByName(f[3]);
        if (c == null) { c = new Category(); c.setName(f[3]); s.categories().save(c); }
        b.setCategoryId(c.getCategoryId());
        Publisher p = s.publishers().findByName(f[4]);
        if (p == null) { p = new Publisher(); p.setName(f[4]); s.publishers().save(p); }
        b.setPublisherId(p.getPublisherId());
        b.setPublishYear(Integer.parseInt(f[5]));
        b.setDescription(f[8]);
        s.saveBook(b);
        int n = Integer.parseInt(f[6]);
        for (int i = 0; i < n; i++) {
            BookCopy c2 = new BookCopy();
            c2.setBookId(b.getBookId());
            c2.setStatus(CopyStatus.AVAILABLE);
            c2.setShelfLocation("Shelf " + f[7]);
            s.saveCopy(c2);
        }
        return s.findBook(b.getBookId());
    }

    private static BookCopy copy(LibraryStore s, Book b, int index) { return s.copiesOfBook(b.getBookId()).get(index); }

    private static Loan loan(LibraryStore s, Member m, BookCopy copy, Librarian lib, LocalDate loanDate, LocalDate returned, LoanStatus status) {
        Loan l = new Loan();
        l.setMemberId(m.getUserId());
        l.setCopyId(copy.getCopyId());
        l.setLibrarianId(lib.getUserId());
        l.setLoanDate(loanDate);
        l.setDueDate(loanDate.plusDays(Rules.LOAN_DAYS));
        l.setReturnDate(returned);
        l.setStatus(status);
        s.saveLoan(l);
        if (status == LoanStatus.BORROWED) {
            copy.updateStatus(CopyStatus.BORROWED);
            s.saveCopy(copy);
        }
        return l;
    }
}
