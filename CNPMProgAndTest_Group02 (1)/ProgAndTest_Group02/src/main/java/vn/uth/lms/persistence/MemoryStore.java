package vn.uth.lms.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import vn.uth.lms.model.*;
import vn.uth.lms.util.TextUtil;

/** In-memory implementation of the data-access layer (used by unit tests and by the demo mode). */
public class MemoryStore implements LibraryStore {

    private final Map<Integer, User> users = new LinkedHashMap<>();
    private final Map<Integer, Book> books = new LinkedHashMap<>();
    private final Map<Integer, List<Integer>> bookAuthorIds = new HashMap<>();
    private final Map<Integer, String> titleNorm = new HashMap<>();
    private final Map<Integer, BookCopy> copies = new LinkedHashMap<>();
    private final Map<Integer, List<BookCopy>> copiesByBook = new HashMap<>();
    private final Map<Integer, Loan> loans = new LinkedHashMap<>();
    private final Map<Integer, Fine> fines = new LinkedHashMap<>();
    private final Map<Integer, Reservation> reservations = new LinkedHashMap<>();
    private final Map<String, String> config = new HashMap<>();
    private int userSeq, bookSeq, copySeq, loanSeq, fineSeq, reservationSeq;

    private final MemRef<Category> categories = new MemRef<>(id -> (int) books.values().stream().filter(b -> b.getCategoryId() == id).count());
    private final MemRef<Author> authors = new MemRef<>(id -> (int) books.values().stream().filter(b -> bookAuthorIds.getOrDefault(b.getBookId(), List.of()).contains(id)).count());
    private final MemRef<Publisher> publishers = new MemRef<>(id -> (int) books.values().stream().filter(b -> b.getPublisherId() == id).count());

    @Override
    public synchronized <T> T tx(Supplier<T> work) { return work.get(); }

    // ------------------------------------------------------------------ users
    @Override public synchronized User findUser(int id) { return users.get(id); }

    @Override
    public synchronized User findUserByUsername(String username) {
        return users.values().stream().filter(u -> u.getUsername().equalsIgnoreCase(username)).findFirst().orElse(null);
    }

    @Override
    public synchronized User findUserByEmail(String email) {
        return users.values().stream().filter(u -> u.getEmail() != null && u.getEmail().equalsIgnoreCase(email)).findFirst().orElse(null);
    }

    @Override
    public synchronized Member findMemberByCard(String card) {
        return users.values().stream().filter(u -> u instanceof Member m && card != null && card.equalsIgnoreCase(m.getCardNumber()))
                .map(u -> (Member) u).findFirst().orElse(null);
    }

    @Override
    public synchronized boolean staffCodeExists(String code) {
        return users.values().stream().anyMatch(u -> u instanceof Librarian l && code != null && code.equalsIgnoreCase(l.getStaffCode()));
    }

    @Override
    public synchronized User saveUser(User user) {
        if (user.getUserId() == 0) user.setUserId(++userSeq);
        users.put(user.getUserId(), user);
        return user;
    }

    @Override
    public synchronized List<Member> searchMembers(String keyword) {
        String kw = TextUtil.normalize(keyword);
        return users.values().stream().filter(u -> u instanceof Member && !u.isDeleted()).map(u -> (Member) u)
                .filter(m -> kw.isEmpty() || TextUtil.normalize(m.getFullName()).contains(kw)
                        || TextUtil.normalize(m.getUsername()).contains(kw)
                        || (m.getCardNumber() != null && TextUtil.normalize(m.getCardNumber()).contains(kw)))
                .sorted(Comparator.comparingInt(Member::getUserId)).collect(Collectors.toList());
    }

    @Override
    public synchronized List<Librarian> listStaff() {
        return users.values().stream().filter(u -> u instanceof Librarian && !u.isDeleted()).map(u -> (Librarian) u)
                .sorted(Comparator.comparingInt(Librarian::getUserId)).collect(Collectors.toList());
    }

    // ---------------------------------------------------------------- catalog
    @Override public RefRepository<Category> categories() { return categories; }
    @Override public RefRepository<Author> authors() { return authors; }
    @Override public RefRepository<Publisher> publishers() { return publishers; }

    private void fill(Book b) {
        Category c = categories.find(b.getCategoryId());
        b.setCategoryName(c == null ? null : c.getName());
        Publisher p = publishers.find(b.getPublisherId());
        b.setPublisherName(p == null ? null : p.getName());
        List<Author> as = new ArrayList<>();
        for (int id : bookAuthorIds.getOrDefault(b.getBookId(), List.of())) {
            Author a = authors.find(id);
            if (a != null) as.add(a);
        }
        b.setAuthors(as);
        List<BookCopy> cs = copiesByBook.getOrDefault(b.getBookId(), List.of());
        b.setTotalCopies(cs.size());
        b.setAvailableCopies((int) cs.stream().filter(BookCopy::isAvailable).count());
        BookCopy shelf = cs.stream().filter(BookCopy::isAvailable).findFirst().orElse(cs.isEmpty() ? null : cs.get(0));
        b.setShelfLocation(shelf == null ? null : shelf.getShelfLocation());
    }

    @Override
    public synchronized Book findBook(int id) {
        Book b = books.get(id);
        if (b != null) fill(b);
        return b;
    }

    @Override
    public synchronized Book findBookByIsbn(String isbn) {
        String key = TextUtil.isbnKey(isbn);
        Book b = books.values().stream().filter(x -> !x.isDeleted() && TextUtil.isbnKey(x.getIsbn()).equals(key)).findFirst().orElse(null);
        if (b != null) fill(b);
        return b;
    }

    @Override
    public synchronized Book saveBook(Book book) {
        if (book.getBookId() == 0) book.setBookId(++bookSeq);
        books.put(book.getBookId(), book);
        bookAuthorIds.put(book.getBookId(), book.getAuthors().stream().map(Author::getAuthorId).collect(Collectors.toList()));
        titleNorm.put(book.getBookId(), TextUtil.normalize(book.getTitle()));
        fill(book);
        return book;
    }

    @Override
    public synchronized PageResult<Book> searchBooks(String keyword, SearchType type, int categoryId, boolean newestFirst, int page, int pageSize) {
        String kw = keyword == null ? "" : keyword;
        String isbnKw = TextUtil.isbnKey(kw);
        List<Book> found = new ArrayList<>();
        for (Book b : books.values()) {
            if (b.isDeleted() || (categoryId != 0 && b.getCategoryId() != categoryId)) continue;
            if (!kw.isEmpty()) {
                boolean title = titleNorm.getOrDefault(b.getBookId(), "").contains(kw);
                boolean author = bookAuthorIds.getOrDefault(b.getBookId(), List.of()).stream().map(authors::find)
                        .anyMatch(a -> a != null && TextUtil.normalize(a.getName()).contains(kw));
                Category c = categories.find(b.getCategoryId());
                boolean category = c != null && TextUtil.normalize(c.getName()).contains(kw);
                boolean isbn = !isbnKw.isEmpty() && TextUtil.isbnKey(b.getIsbn()).contains(isbnKw);
                boolean ok = switch (type) {
                    case TITLE -> title;
                    case AUTHOR -> author;
                    case CATEGORY -> category;
                    case ISBN -> isbn;
                    default -> title || author || category || isbn;
                };
                if (!ok) continue;
            }
            found.add(b);
        }
        if (newestFirst) found.sort(Comparator.comparingInt(Book::getBookId).reversed());
        else found.sort(Comparator.comparing((Book b) -> titleNorm.getOrDefault(b.getBookId(), "")).thenComparingInt(Book::getBookId));
        int from = Math.min(found.size(), Math.max(0, (page - 1) * pageSize));
        int to = Math.min(found.size(), from + pageSize);
        List<Book> slice = new ArrayList<>(found.subList(from, to));
        slice.forEach(this::fill);
        return new PageResult<>(slice, page, pageSize, found.size());
    }

    @Override public synchronized BookCopy findCopy(int id) { return copies.get(id); }

    @Override
    public synchronized BookCopy findCopyByBarcode(String barcode) {
        return copies.values().stream().filter(c -> c.getBarcode().equalsIgnoreCase(barcode == null ? "" : barcode.trim())).findFirst().orElse(null);
    }

    @Override
    public synchronized List<BookCopy> copiesOfBook(int bookId) {
        return new ArrayList<>(copiesByBook.getOrDefault(bookId, List.of()));
    }

    @Override
    public synchronized BookCopy saveCopy(BookCopy copy) {
        if (copy.getCopyId() == 0) {
            copy.setCopyId(++copySeq);
            copy.setBarcode(String.format("BC-%06d", copy.getCopyId()));
            copiesByBook.computeIfAbsent(copy.getBookId(), k -> new ArrayList<>()).add(copy);
        }
        copies.put(copy.getCopyId(), copy);
        return copy;
    }

    @Override
    public synchronized void deleteCopy(int copyId) {
        BookCopy c = copies.remove(copyId);
        if (c != null) copiesByBook.getOrDefault(c.getBookId(), new ArrayList<>()).remove(c);
    }

    @Override
    public synchronized boolean copyHasLoans(int copyId) { return loans.values().stream().anyMatch(l -> l.getCopyId() == copyId); }

    // ------------------------------------------------------------------ loans
    @Override public synchronized Loan findLoan(int id) { return loans.get(id); }

    @Override
    public synchronized Loan saveLoan(Loan loan) {
        if (loan.getLoanId() == 0) loan.setLoanId(++loanSeq);
        loans.put(loan.getLoanId(), loan);
        return loan;
    }

    @Override
    public synchronized Loan findActiveLoanByCopy(int copyId) {
        return loans.values().stream().filter(l -> l.getCopyId() == copyId && l.getStatus() == LoanStatus.BORROWED).findFirst().orElse(null);
    }

    @Override
    public synchronized List<Loan> loansOfMember(int memberId) {
        return loans.values().stream().filter(l -> l.getMemberId() == memberId)
                .sorted(Comparator.comparing(Loan::getLoanDate).thenComparingInt(Loan::getLoanId).reversed()).collect(Collectors.toList());
    }

    @Override
    public synchronized List<Loan> activeLoansOfMember(int memberId) {
        return loans.values().stream().filter(l -> l.getMemberId() == memberId && l.getStatus() == LoanStatus.BORROWED).collect(Collectors.toList());
    }

    private Set<Integer> copyIdsOfBook(int bookId) {
        return copiesByBook.getOrDefault(bookId, List.of()).stream().map(BookCopy::getCopyId).collect(Collectors.toSet());
    }

    @Override
    public synchronized boolean bookHasActiveLoan(int bookId) {
        Set<Integer> ids = copyIdsOfBook(bookId);
        return loans.values().stream().anyMatch(l -> l.getStatus() == LoanStatus.BORROWED && ids.contains(l.getCopyId()));
    }

    @Override
    public synchronized boolean bookHasLoanHistory(int bookId) {
        Set<Integer> ids = copyIdsOfBook(bookId);
        return loans.values().stream().anyMatch(l -> ids.contains(l.getCopyId()));
    }

    // ------------------------------------------------------------------ fines
    @Override public synchronized Fine findFine(int id) { return fines.get(id); }

    @Override
    public synchronized Fine saveFine(Fine fine) {
        if (fine.getFineId() == 0) fine.setFineId(++fineSeq);
        fines.put(fine.getFineId(), fine);
        return fine;
    }

    @Override
    public synchronized List<Fine> finesOfLoan(int loanId) {
        return fines.values().stream().filter(f -> f.getLoanId() == loanId).collect(Collectors.toList());
    }

    @Override
    public synchronized List<Fine> finesOfMember(int memberId) {
        return fines.values().stream().filter(f -> {
            Loan l = loans.get(f.getLoanId());
            return l != null && l.getMemberId() == memberId;
        }).sorted(Comparator.comparingInt(Fine::getFineId).reversed()).collect(Collectors.toList());
    }

    // --------------------------------------------------------- reservations
    @Override public synchronized Reservation findReservation(int id) { return reservations.get(id); }

    @Override
    public synchronized Reservation saveReservation(Reservation r) {
        if (r.getReservationId() == 0) r.setReservationId(++reservationSeq);
        reservations.put(r.getReservationId(), r);
        return r;
    }

    @Override
    public synchronized List<Reservation> activeReservationsOfBook(int bookId) {
        return reservations.values().stream().filter(r -> r.getBookId() == bookId && r.isActive())
                .sorted(Comparator.comparing(Reservation::getReservedAt).thenComparingInt(Reservation::getReservationId)).collect(Collectors.toList());
    }

    @Override
    public synchronized List<Reservation> activeReservationsOfMember(int memberId) {
        return reservations.values().stream().filter(r -> r.getMemberId() == memberId && r.isActive()).collect(Collectors.toList());
    }

    @Override
    public synchronized List<Reservation> reservationsOfMember(int memberId) {
        return reservations.values().stream().filter(r -> r.getMemberId() == memberId)
                .sorted(Comparator.comparingInt(Reservation::getReservationId).reversed()).collect(Collectors.toList());
    }

    @Override
    public synchronized List<Reservation> expiredReady(LocalDateTime now) {
        return reservations.values().stream().filter(r -> r.isExpired(now)).collect(Collectors.toList());
    }

    // ---------------------------------------------------------------- reports
    @Override
    public synchronized List<Object[]> mostBorrowed(LocalDate from, LocalDate to, int limit) {
        Map<Integer, Long> counts = new HashMap<>();
        for (Loan l : loans.values()) {
            if (l.getLoanDate().isBefore(from) || l.getLoanDate().isAfter(to)) continue;
            BookCopy c = copies.get(l.getCopyId());
            if (c != null) counts.merge(c.getBookId(), 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(limit).map(e -> {
                    Book b = books.get(e.getKey());
                    return new Object[]{e.getKey(), b.getTitle(), b.getIsbn(), e.getValue()};
                }).collect(Collectors.toList());
    }

    @Override
    public synchronized List<Loan> overdueLoans(LocalDate from, LocalDate to, LocalDate today) {
        return loans.values().stream().filter(l -> !l.getDueDate().isBefore(from) && !l.getDueDate().isAfter(to))
                .filter(l -> l.getReturnDate() != null ? l.getReturnDate().isAfter(l.getDueDate())
                        : (l.getStatus() == LoanStatus.BORROWED && today.isAfter(l.getDueDate())))
                .sorted(Comparator.comparing(Loan::getDueDate)).collect(Collectors.toList());
    }

    @Override
    public synchronized List<Fine> paidFinesBetween(LocalDateTime from, LocalDateTime to) {
        return fines.values().stream().filter(f -> f.getStatus() == FineStatus.PAID && f.getPaidAt() != null
                && !f.getPaidAt().isBefore(from) && !f.getPaidAt().isAfter(to))
                .sorted(Comparator.comparing(Fine::getPaidAt)).collect(Collectors.toList());
    }

    @Override
    public synchronized Map<String, Long> memberStats(LocalDate from, LocalDate to) {
        List<Member> ms = users.values().stream().filter(u -> u instanceof Member && !u.isDeleted()).map(u -> (Member) u).collect(Collectors.toList());
        Map<String, Long> r = new LinkedHashMap<>();
        r.put("totalMembers", (long) ms.size());
        r.put("activeMembers", ms.stream().filter(m -> m.getStatus() == AccountStatus.ACTIVE).count());
        r.put("newMembers", ms.stream().filter(m -> m.getCreatedAt() != null && !m.getCreatedAt().toLocalDate().isBefore(from)
                && !m.getCreatedAt().toLocalDate().isAfter(to)).count());
        List<Loan> inPeriod = loans.values().stream().filter(l -> !l.getLoanDate().isBefore(from) && !l.getLoanDate().isAfter(to)).collect(Collectors.toList());
        r.put("borrowers", inPeriod.stream().map(Loan::getMemberId).distinct().count());
        r.put("loans", (long) inPeriod.size());
        return r;
    }

    // ----------------------------------------------------------------- config
    @Override public synchronized String getConfig(String key, String def) { return config.getOrDefault(key, def); }
    @Override public synchronized void setConfig(String key, String value) { config.put(key, value); }

    // ------------------------------------------------------------ reference data
    private static final class MemRef<T extends NamedEntity> implements RefRepository<T> {
        private final Map<Integer, T> map = new LinkedHashMap<>();
        private final java.util.function.Function<Integer, Integer> usage;
        private int seq;

        MemRef(java.util.function.Function<Integer, Integer> usage) { this.usage = usage; }

        @Override public synchronized List<T> list() {
            return map.values().stream().sorted(Comparator.comparing(t -> TextUtil.normalize(t.getName()))).collect(Collectors.toList());
        }
        @Override public synchronized T find(int id) { return map.get(id); }
        @Override public synchronized T findByName(String name) {
            String n = TextUtil.normalize(name);
            return map.values().stream().filter(t -> TextUtil.normalize(t.getName()).equals(n)).findFirst().orElse(null);
        }
        @Override public synchronized T save(T t) {
            if (t.getId() == 0) t.setId(++seq);
            map.put(t.getId(), t);
            return t;
        }
        @Override public synchronized void delete(int id) { map.remove(id); }
        @Override public int usageCount(int id) { return usage.apply(id); }
    }
}
