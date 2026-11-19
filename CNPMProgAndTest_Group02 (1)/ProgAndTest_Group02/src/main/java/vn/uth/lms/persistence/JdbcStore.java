package vn.uth.lms.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import vn.uth.lms.model.*;
import vn.uth.lms.util.AppException;
import vn.uth.lms.util.TextUtil;

/** MySQL implementation of the data-access layer (plain JDBC, prepared statements only). */
public class JdbcStore implements LibraryStore {

    @FunctionalInterface
    private interface RowMapper<T> { T map(ResultSet rs) throws SQLException; }

    private final String url, user, password;
    private final ThreadLocal<Connection> txConn = new ThreadLocal<>();
    private final JdbcRef<Category> categories;
    private final JdbcRef<Author> authors;
    private final JdbcRef<Publisher> publishers;

    public JdbcStore(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.categories = new JdbcRef<>("categories", "category_id", false,
                rs -> new Category(rs.getInt("category_id"), rs.getString("name")),
                "SELECT COUNT(*) FROM books WHERE category_id=?", null);
        this.authors = new JdbcRef<>("authors", "author_id", false,
                rs -> new Author(rs.getInt("author_id"), rs.getString("name")),
                "SELECT COUNT(*) FROM book_authors WHERE author_id=?", null);
        this.publishers = new JdbcRef<>("publishers", "publisher_id", true, rs -> {
            Publisher p = new Publisher();
            p.setPublisherId(rs.getInt("publisher_id"));
            p.setName(rs.getString("name"));
            p.setAddress(rs.getString("address"));
            return p;
        }, "SELECT COUNT(*) FROM books WHERE publisher_id=?", null);
        waitForDatabase();
        initSchema();
    }

    // ------------------------------------------------------------ connection handling
    private void waitForDatabase() {
        try { Class.forName("com.mysql.cj.jdbc.Driver"); } catch (ClassNotFoundException ignored) { /* found via ServiceLoader */ }
        SQLException last = null;
        for (int i = 0; i < 60; i++) {
            try (Connection ignored = DriverManager.getConnection(url, user, password)) {
                return;
            } catch (SQLException e) {
                last = e;
                System.out.println("Waiting for the database ... (" + e.getMessage() + ")");
                try { Thread.sleep(2000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }
        throw new IllegalStateException("Cannot connect to the database " + url, last);
    }

    private void initSchema() {
        try (InputStream in = JdbcStore.class.getResourceAsStream("/schema.sql")) {
            String script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection c = DriverManager.getConnection(url, user, password); Statement st = c.createStatement()) {
                for (String sql : script.split(";")) if (!sql.isBlank()) st.execute(sql);
            }
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Cannot create the database schema", e);
        }
    }

    @Override
    public <T> T tx(Supplier<T> work) {
        if (txConn.get() != null) return work.get();
        Connection c = null;
        try {
            c = DriverManager.getConnection(url, user, password);
            c.setAutoCommit(false);
            txConn.set(c);
            T result = work.get();
            c.commit();
            return result;
        } catch (RuntimeException e) {
            rollback(c);
            throw e;
        } catch (SQLException e) {
            rollback(c);
            throw unavailable(e);
        } finally {
            txConn.remove();
            if (c != null) try { c.close(); } catch (SQLException ignored) { /* nothing to do */ }
        }
    }

    private static void rollback(Connection c) {
        if (c != null) try { c.rollback(); } catch (SQLException ignored) { /* nothing to do */ }
    }

    private static AppException unavailable(SQLException e) {
        e.printStackTrace();
        return new AppException(503, "The database is not available. Please try again later");
    }

    private String lock() { return txConn.get() != null ? " FOR UPDATE" : ""; }

    private <T> T withConnection(java.util.function.Function<Connection, T> f) {
        Connection c = txConn.get();
        boolean own = c == null;
        try {
            if (own) c = DriverManager.getConnection(url, user, password);
            return f.apply(c);
        } catch (SQLException e) {
            throw unavailable(e);
        } finally {
            if (own && c != null) try { c.close(); } catch (SQLException ignored) { /* nothing to do */ }
        }
    }

    private static void bind(PreparedStatement ps, Object... p) throws SQLException {
        for (int i = 0; i < p.length; i++) {
            Object v = p[i];
            if (v == null) ps.setNull(i + 1, Types.NULL);
            else if (v instanceof Enum<?> e) ps.setString(i + 1, e.name());
            else if (v instanceof LocalDate d) ps.setDate(i + 1, java.sql.Date.valueOf(d));
            else if (v instanceof LocalDateTime t) ps.setTimestamp(i + 1, Timestamp.valueOf(t));
            else if (v instanceof Boolean b) ps.setBoolean(i + 1, b);
            else ps.setObject(i + 1, v);
        }
    }

    private <T> List<T> query(String sql, RowMapper<T> mapper, Object... p) {
        return withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                bind(ps, p);
                try (ResultSet rs = ps.executeQuery()) {
                    List<T> out = new ArrayList<>();
                    while (rs.next()) out.add(mapper.map(rs));
                    return out;
                }
            } catch (SQLException e) { throw unavailable(e); }
        });
    }

    private <T> T one(String sql, RowMapper<T> mapper, Object... p) {
        List<T> l = query(sql, mapper, p);
        return l.isEmpty() ? null : l.get(0);
    }

    private long count(String sql, Object... p) {
        Long v = one(sql, rs -> rs.getLong(1), p);
        return v == null ? 0 : v;
    }

    private int update(String sql, Object... p) {
        return withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                bind(ps, p);
                return ps.executeUpdate();
            } catch (SQLException e) {
                if (e instanceof SQLIntegrityConstraintViolationException)
                    throw AppException.conflict("The data conflicts with an existing record (duplicate or still in use)");
                throw unavailable(e);
            }
        });
    }

    private int insert(String sql, Object... p) {
        return withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                bind(ps, p);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    return rs.getInt(1);
                }
            } catch (SQLException e) {
                if (e instanceof SQLIntegrityConstraintViolationException)
                    throw AppException.conflict("The data conflicts with an existing record (duplicate or still in use)");
                throw unavailable(e);
            }
        });
    }

    private static LocalDate date(ResultSet rs, String col) throws SQLException {
        java.sql.Date d = rs.getDate(col);
        return d == null ? null : d.toLocalDate();
    }

    private static LocalDateTime time(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toLocalDateTime();
    }

    private static String like(String s) {
        return "%" + s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static String placeholders(int n) {
        return String.join(",", Collections.nCopies(n, "?"));
    }

    // ================================================================== users
    private static final String USER_SELECT =
            "SELECT u.*, m.address, m.date_of_birth, m.card_number, m.card_expiry, l.staff_code, l.hire_date, a.admin_level, "
                    + "CASE WHEN a.user_id IS NOT NULL THEN 'ADMIN' WHEN l.user_id IS NOT NULL THEN 'LIBRARIAN' ELSE 'MEMBER' END AS role "
                    + "FROM users u LEFT JOIN members m ON m.user_id=u.user_id LEFT JOIN librarians l ON l.user_id=u.user_id "
                    + "LEFT JOIN admins a ON a.user_id=u.user_id ";

    private static User mapUser(ResultSet rs) throws SQLException {
        String role = rs.getString("role");
        User u;
        if (role.equals("ADMIN")) {
            Admin a = new Admin();
            a.setAdminLevel(rs.getInt("admin_level"));
            a.setStaffCode(rs.getString("staff_code"));
            a.setHireDate(date(rs, "hire_date"));
            u = a;
        } else if (role.equals("LIBRARIAN")) {
            Librarian l = new Librarian();
            l.setStaffCode(rs.getString("staff_code"));
            l.setHireDate(date(rs, "hire_date"));
            u = l;
        } else {
            Member m = new Member();
            m.setAddress(rs.getString("address"));
            m.setDateOfBirth(date(rs, "date_of_birth"));
            m.setCardNumber(rs.getString("card_number"));
            m.setCardExpiry(date(rs, "card_expiry"));
            u = m;
        }
        u.setUserId(rs.getInt("user_id"));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setFullName(rs.getString("full_name"));
        u.setEmail(rs.getString("email"));
        u.setPhone(rs.getString("phone"));
        u.setStatus(AccountStatus.valueOf(rs.getString("status")));
        u.setFailedAttempts(rs.getInt("failed_attempts"));
        u.setLockedUntil(time(rs, "locked_until"));
        u.setCreatedAt(time(rs, "created_at"));
        u.setDeleted(rs.getBoolean("deleted"));
        return u;
    }

    @Override public User findUser(int id) { return one(USER_SELECT + "WHERE u.user_id=?", JdbcStore::mapUser, id); }
    @Override public User findUserByUsername(String username) { return one(USER_SELECT + "WHERE u.username=?", JdbcStore::mapUser, username); }
    @Override public User findUserByEmail(String email) { return one(USER_SELECT + "WHERE u.email=?", JdbcStore::mapUser, email); }

    @Override
    public Member findMemberByCard(String card) {
        return (Member) one(USER_SELECT + "WHERE m.card_number=?", JdbcStore::mapUser, card);
    }

    @Override public boolean staffCodeExists(String code) { return count("SELECT COUNT(*) FROM librarians WHERE staff_code=?", code) > 0; }

    @Override
    public User saveUser(User u) {
        return tx(() -> {
            if (u.getCreatedAt() == null) u.setCreatedAt(LocalDateTime.now());
            if (u.getUserId() == 0) {
                u.setUserId(insert("INSERT INTO users(username,password_hash,full_name,email,phone,status,failed_attempts,locked_until,created_at,deleted) "
                                + "VALUES(?,?,?,?,?,?,?,?,?,?)", u.getUsername(), u.getPasswordHash(), u.getFullName(), u.getEmail(), u.getPhone(),
                        u.getStatus(), u.getFailedAttempts(), u.getLockedUntil(), u.getCreatedAt(), u.isDeleted()));
                if (u instanceof Member m) {
                    update("INSERT INTO members(user_id,address,date_of_birth,card_number,card_expiry) VALUES(?,?,?,?,?)",
                            m.getUserId(), m.getAddress(), m.getDateOfBirth(), m.getCardNumber(), m.getCardExpiry());
                } else if (u instanceof Librarian l) {
                    update("INSERT INTO librarians(user_id,staff_code,hire_date) VALUES(?,?,?)", l.getUserId(), l.getStaffCode(), l.getHireDate());
                    if (u instanceof Admin a) update("INSERT INTO admins(user_id,admin_level) VALUES(?,?)", a.getUserId(), a.getAdminLevel());
                }
            } else {
                update("UPDATE users SET username=?,password_hash=?,full_name=?,email=?,phone=?,status=?,failed_attempts=?,locked_until=?,deleted=? WHERE user_id=?",
                        u.getUsername(), u.getPasswordHash(), u.getFullName(), u.getEmail(), u.getPhone(), u.getStatus(), u.getFailedAttempts(),
                        u.getLockedUntil(), u.isDeleted(), u.getUserId());
                if (u instanceof Member m) {
                    update("UPDATE members SET address=?,date_of_birth=?,card_number=?,card_expiry=? WHERE user_id=?",
                            m.getAddress(), m.getDateOfBirth(), m.getCardNumber(), m.getCardExpiry(), m.getUserId());
                } else if (u instanceof Librarian l) {
                    update("UPDATE librarians SET staff_code=?,hire_date=? WHERE user_id=?", l.getStaffCode(), l.getHireDate(), l.getUserId());
                    if (u instanceof Admin a) update("UPDATE admins SET admin_level=? WHERE user_id=?", a.getAdminLevel(), a.getUserId());
                }
            }
            return u;
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Member> searchMembers(String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        List<User> rows = kw.isEmpty()
                ? query(USER_SELECT + "WHERE m.user_id IS NOT NULL AND u.deleted=0 ORDER BY u.user_id LIMIT 500", JdbcStore::mapUser)
                : query(USER_SELECT + "WHERE m.user_id IS NOT NULL AND u.deleted=0 AND (u.full_name LIKE ? OR u.username LIKE ? OR m.card_number LIKE ?) "
                        + "ORDER BY u.user_id LIMIT 500", JdbcStore::mapUser, like(kw), like(kw), like(kw));
        List<Member> out = new ArrayList<>();
        rows.forEach(u -> out.add((Member) u));
        return out;
    }

    @Override
    public List<Librarian> listStaff() {
        List<Librarian> out = new ArrayList<>();
        query(USER_SELECT + "WHERE l.user_id IS NOT NULL AND u.deleted=0 ORDER BY u.user_id", JdbcStore::mapUser).forEach(u -> out.add((Librarian) u));
        return out;
    }

    // ============================================================ categories etc.
    @Override public RefRepository<Category> categories() { return categories; }
    @Override public RefRepository<Author> authors() { return authors; }
    @Override public RefRepository<Publisher> publishers() { return publishers; }

    private final class JdbcRef<T extends NamedEntity> implements RefRepository<T> {
        private final String table, idCol, usageSql;
        private final boolean hasAddress;
        private final RowMapper<T> mapper;

        JdbcRef(String table, String idCol, boolean hasAddress, RowMapper<T> mapper, String usageSql, Object unused) {
            this.table = table;
            this.idCol = idCol;
            this.hasAddress = hasAddress;
            this.mapper = mapper;
            this.usageSql = usageSql;
        }

        @Override public List<T> list() { return query("SELECT * FROM " + table + " ORDER BY name_norm", mapper); }
        @Override public T find(int id) { return one("SELECT * FROM " + table + " WHERE " + idCol + "=?", mapper, id); }
        @Override public T findByName(String name) { return one("SELECT * FROM " + table + " WHERE name_norm=?", mapper, TextUtil.normalize(name)); }

        @Override
        public T save(T t) {
            String norm = TextUtil.normalize(t.getName());
            if (t.getId() == 0) {
                int id = hasAddress
                        ? insert("INSERT INTO " + table + "(name,name_norm,address) VALUES(?,?,?)", t.getName(), norm, ((Publisher) t).getAddress())
                        : insert("INSERT INTO " + table + "(name,name_norm) VALUES(?,?)", t.getName(), norm);
                t.setId(id);
            } else if (hasAddress) {
                update("UPDATE " + table + " SET name=?,name_norm=?,address=? WHERE " + idCol + "=?", t.getName(), norm, ((Publisher) t).getAddress(), t.getId());
            } else {
                update("UPDATE " + table + " SET name=?,name_norm=? WHERE " + idCol + "=?", t.getName(), norm, t.getId());
            }
            return t;
        }

        @Override public void delete(int id) { update("DELETE FROM " + table + " WHERE " + idCol + "=?", id); }
        @Override public int usageCount(int id) { return (int) count(usageSql, id); }
    }

    // ================================================================== books
    private static final String BOOK_SELECT =
            "SELECT b.*, c.name AS category_name, p.name AS publisher_name, "
                    + "(SELECT COUNT(*) FROM book_copies bc WHERE bc.book_id=b.book_id) AS total_copies, "
                    + "(SELECT COUNT(*) FROM book_copies bc WHERE bc.book_id=b.book_id AND bc.status='AVAILABLE') AS available_copies, "
                    + "(SELECT bc.shelf_location FROM book_copies bc WHERE bc.book_id=b.book_id ORDER BY (bc.status='AVAILABLE') DESC, bc.copy_id LIMIT 1) AS shelf "
                    + "FROM books b JOIN categories c ON c.category_id=b.category_id JOIN publishers p ON p.publisher_id=b.publisher_id ";

    private static Book mapBook(ResultSet rs) throws SQLException {
        Book b = new Book();
        b.setBookId(rs.getInt("book_id"));
        b.setIsbn(rs.getString("isbn"));
        b.setTitle(rs.getString("title"));
        b.setPublishYear(rs.getInt("publish_year"));
        b.setDescription(rs.getString("description"));
        b.setCategoryId(rs.getInt("category_id"));
        b.setPublisherId(rs.getInt("publisher_id"));
        b.setCategoryName(rs.getString("category_name"));
        b.setPublisherName(rs.getString("publisher_name"));
        b.setTotalCopies(rs.getInt("total_copies"));
        b.setAvailableCopies(rs.getInt("available_copies"));
        b.setShelfLocation(rs.getString("shelf"));
        b.setDeleted(rs.getBoolean("deleted"));
        return b;
    }

    private void loadAuthors(List<Book> books) {
        if (books.isEmpty()) return;
        Map<Integer, Book> byId = new HashMap<>();
        for (Book b : books) { byId.put(b.getBookId(), b); b.setAuthors(new ArrayList<>()); }
        Object[] ids = byId.keySet().toArray();
        query("SELECT ba.book_id, a.author_id, a.name FROM book_authors ba JOIN authors a ON a.author_id=ba.author_id "
                        + "WHERE ba.book_id IN (" + placeholders(ids.length) + ") ORDER BY ba.book_id, ba.author_order, a.author_id",
                rs -> {
                    byId.get(rs.getInt("book_id")).getAuthors().add(new Author(rs.getInt("author_id"), rs.getString("name")));
                    return null;
                }, ids);
    }

    @Override
    public Book findBook(int id) {
        Book b = one(BOOK_SELECT + "WHERE b.book_id=?", JdbcStore::mapBook, id);
        if (b != null) loadAuthors(List.of(b));
        return b;
    }

    @Override
    public Book findBookByIsbn(String isbn) {
        Book b = one(BOOK_SELECT + "WHERE b.deleted=0 AND REPLACE(b.isbn,'-','')=?", JdbcStore::mapBook, TextUtil.isbnKey(isbn));
        if (b != null) loadAuthors(List.of(b));
        return b;
    }

    @Override
    public Book saveBook(Book b) {
        return tx(() -> {
            String norm = TextUtil.normalize(b.getTitle());
            if (b.getBookId() == 0) {
                b.setBookId(insert("INSERT INTO books(isbn,title,title_norm,publish_year,description,category_id,publisher_id,deleted) VALUES(?,?,?,?,?,?,?,?)",
                        b.getIsbn(), b.getTitle(), norm, b.getPublishYear(), b.getDescription(), b.getCategoryId(), b.getPublisherId(), b.isDeleted()));
            } else {
                update("UPDATE books SET isbn=?,title=?,title_norm=?,publish_year=?,description=?,category_id=?,publisher_id=?,deleted=? WHERE book_id=?",
                        b.getIsbn(), b.getTitle(), norm, b.getPublishYear(), b.getDescription(), b.getCategoryId(), b.getPublisherId(), b.isDeleted(), b.getBookId());
            }
            update("DELETE FROM book_authors WHERE book_id=?", b.getBookId());
            int pos = 0;
            for (Author a : b.getAuthors()) update("INSERT INTO book_authors(book_id,author_id,author_order) VALUES(?,?,?)", b.getBookId(), a.getAuthorId(), pos++);
            return findBook(b.getBookId());
        });
    }

    @Override
    public PageResult<Book> searchBooks(String keyword, SearchType type, int categoryId, boolean newestFirst, int page, int pageSize) {
        StringBuilder from = new StringBuilder("FROM books b JOIN categories c ON c.category_id=b.category_id WHERE b.deleted=0");
        List<Object> params = new ArrayList<>();
        if (categoryId != 0) { from.append(" AND b.category_id=?"); params.add(categoryId); }
        String kw = keyword == null ? "" : keyword;
        if (!kw.isEmpty()) {
            String titleLike = like(kw);
            String isbnKey = TextUtil.isbnKey(kw);
            String authorCond = "EXISTS (SELECT 1 FROM book_authors ba JOIN authors a ON a.author_id=ba.author_id WHERE ba.book_id=b.book_id AND a.name_norm LIKE ?)";
            String isbnCond = isbnKey.isEmpty() ? "1=0" : "REPLACE(b.isbn,'-','') LIKE ?";
            switch (type) {
                case TITLE -> { from.append(" AND b.title_norm LIKE ?"); params.add(titleLike); }
                case AUTHOR -> { from.append(" AND ").append(authorCond); params.add(titleLike); }
                case CATEGORY -> { from.append(" AND c.name_norm LIKE ?"); params.add(titleLike); }
                case ISBN -> { from.append(" AND ").append(isbnCond); if (!isbnKey.isEmpty()) params.add(like(isbnKey)); }
                default -> {
                    from.append(" AND (b.title_norm LIKE ? OR ").append(authorCond).append(" OR c.name_norm LIKE ? OR ").append(isbnCond).append(")");
                    params.add(titleLike); params.add(titleLike); params.add(titleLike);
                    if (!isbnKey.isEmpty()) params.add(like(isbnKey));
                }
            }
        }
        long total = count("SELECT COUNT(*) " + from, params.toArray());
        List<Object> p2 = new ArrayList<>(params);
        p2.add(pageSize);
        p2.add(Math.max(0, (page - 1) * pageSize));
        List<Integer> ids = query("SELECT b.book_id " + from + (newestFirst ? " ORDER BY b.book_id DESC" : " ORDER BY b.title_norm, b.book_id")
                + " LIMIT ? OFFSET ?", rs -> rs.getInt(1), p2.toArray());
        List<Book> books = new ArrayList<>();
        if (!ids.isEmpty()) {
            Map<Integer, Book> byId = new HashMap<>();
            query(BOOK_SELECT + "WHERE b.book_id IN (" + placeholders(ids.size()) + ")", JdbcStore::mapBook, ids.toArray()).forEach(b -> byId.put(b.getBookId(), b));
            for (int id : ids) books.add(byId.get(id));
            loadAuthors(books);
        }
        return new PageResult<>(books, page, pageSize, total);
    }

    // ================================================================== copies
    private static BookCopy mapCopy(ResultSet rs) throws SQLException {
        BookCopy c = new BookCopy();
        c.setCopyId(rs.getInt("copy_id"));
        c.setBookId(rs.getInt("book_id"));
        c.setBarcode(rs.getString("barcode"));
        c.setStatus(CopyStatus.valueOf(rs.getString("status")));
        c.setShelfLocation(rs.getString("shelf_location"));
        return c;
    }

    @Override public BookCopy findCopy(int id) { return one("SELECT * FROM book_copies WHERE copy_id=?" + lock(), JdbcStore::mapCopy, id); }

    @Override
    public BookCopy findCopyByBarcode(String barcode) {
        return one("SELECT * FROM book_copies WHERE barcode=?" + lock(), JdbcStore::mapCopy, barcode == null ? "" : barcode.trim());
    }

    @Override public List<BookCopy> copiesOfBook(int bookId) { return query("SELECT * FROM book_copies WHERE book_id=? ORDER BY copy_id", JdbcStore::mapCopy, bookId); }

    @Override
    public BookCopy saveCopy(BookCopy c) {
        return tx(() -> {
            if (c.getCopyId() == 0) {
                c.setCopyId(insert("INSERT INTO book_copies(barcode,status,shelf_location,book_id) VALUES(?,?,?,?)",
                        "TMP-" + System.nanoTime(), c.getStatus(), c.getShelfLocation(), c.getBookId()));
                c.setBarcode(String.format("BC-%06d", c.getCopyId()));
                update("UPDATE book_copies SET barcode=? WHERE copy_id=?", c.getBarcode(), c.getCopyId());
            } else {
                update("UPDATE book_copies SET status=?,shelf_location=? WHERE copy_id=?", c.getStatus(), c.getShelfLocation(), c.getCopyId());
            }
            return c;
        });
    }

    @Override public void deleteCopy(int copyId) { update("DELETE FROM book_copies WHERE copy_id=?", copyId); }
    @Override public boolean copyHasLoans(int copyId) { return count("SELECT COUNT(*) FROM loans WHERE copy_id=?", copyId) > 0; }

    // ================================================================== loans
    private static Loan mapLoan(ResultSet rs) throws SQLException {
        Loan l = new Loan();
        l.setLoanId(rs.getInt("loan_id"));
        l.setMemberId(rs.getInt("member_id"));
        l.setCopyId(rs.getInt("copy_id"));
        l.setLibrarianId(rs.getInt("librarian_id"));
        l.setLoanDate(date(rs, "loan_date"));
        l.setDueDate(date(rs, "due_date"));
        l.setReturnDate(date(rs, "return_date"));
        l.setRenewCount(rs.getInt("renew_count"));
        l.setStatus(LoanStatus.valueOf(rs.getString("status")));
        return l;
    }

    @Override public Loan findLoan(int id) { return one("SELECT * FROM loans WHERE loan_id=?" + lock(), JdbcStore::mapLoan, id); }

    @Override
    public Loan saveLoan(Loan l) {
        if (l.getLoanId() == 0) {
            l.setLoanId(insert("INSERT INTO loans(member_id,copy_id,librarian_id,loan_date,due_date,return_date,renew_count,status) VALUES(?,?,?,?,?,?,?,?)",
                    l.getMemberId(), l.getCopyId(), l.getLibrarianId(), l.getLoanDate(), l.getDueDate(), l.getReturnDate(), l.getRenewCount(), l.getStatus()));
        } else {
            update("UPDATE loans SET due_date=?,return_date=?,renew_count=?,status=? WHERE loan_id=?",
                    l.getDueDate(), l.getReturnDate(), l.getRenewCount(), l.getStatus(), l.getLoanId());
        }
        return l;
    }

    @Override
    public Loan findActiveLoanByCopy(int copyId) {
        return one("SELECT * FROM loans WHERE copy_id=? AND status='BORROWED'" + lock(), JdbcStore::mapLoan, copyId);
    }

    @Override public List<Loan> loansOfMember(int memberId) { return query("SELECT * FROM loans WHERE member_id=? ORDER BY loan_date DESC, loan_id DESC", JdbcStore::mapLoan, memberId); }
    @Override public List<Loan> activeLoansOfMember(int memberId) { return query("SELECT * FROM loans WHERE member_id=? AND status='BORROWED'", JdbcStore::mapLoan, memberId); }

    @Override
    public boolean bookHasActiveLoan(int bookId) {
        return count("SELECT COUNT(*) FROM loans l JOIN book_copies c ON c.copy_id=l.copy_id WHERE c.book_id=? AND l.status='BORROWED'", bookId) > 0;
    }

    @Override
    public boolean bookHasLoanHistory(int bookId) {
        return count("SELECT COUNT(*) FROM loans l JOIN book_copies c ON c.copy_id=l.copy_id WHERE c.book_id=?", bookId) > 0;
    }

    // ================================================================== fines
    private static Fine mapFine(ResultSet rs) throws SQLException {
        Fine f = new Fine();
        f.setFineId(rs.getInt("fine_id"));
        f.setLoanId(rs.getInt("loan_id"));
        f.setAmount(rs.getDouble("amount"));
        f.setReason(FineReason.valueOf(rs.getString("reason")));
        f.setStatus(FineStatus.valueOf(rs.getString("status")));
        f.setCreatedAt(time(rs, "created_at"));
        f.setPaidAt(time(rs, "paid_at"));
        return f;
    }

    @Override public Fine findFine(int id) { return one("SELECT * FROM fines WHERE fine_id=?" + lock(), JdbcStore::mapFine, id); }

    @Override
    public Fine saveFine(Fine f) {
        if (f.getFineId() == 0) {
            f.setFineId(insert("INSERT INTO fines(loan_id,amount,reason,status,created_at,paid_at) VALUES(?,?,?,?,?,?)",
                    f.getLoanId(), f.getAmount(), f.getReason(), f.getStatus(), f.getCreatedAt(), f.getPaidAt()));
        } else {
            update("UPDATE fines SET amount=?,status=?,paid_at=? WHERE fine_id=?", f.getAmount(), f.getStatus(), f.getPaidAt(), f.getFineId());
        }
        return f;
    }

    @Override public List<Fine> finesOfLoan(int loanId) { return query("SELECT * FROM fines WHERE loan_id=? ORDER BY fine_id", JdbcStore::mapFine, loanId); }

    @Override
    public List<Fine> finesOfMember(int memberId) {
        return query("SELECT f.* FROM fines f JOIN loans l ON l.loan_id=f.loan_id WHERE l.member_id=? ORDER BY f.fine_id DESC", JdbcStore::mapFine, memberId);
    }

    // ========================================================== reservations
    private static Reservation mapReservation(ResultSet rs) throws SQLException {
        Reservation r = new Reservation();
        r.setReservationId(rs.getInt("reservation_id"));
        r.setMemberId(rs.getInt("member_id"));
        r.setBookId(rs.getInt("book_id"));
        r.setReservedAt(time(rs, "reserved_at"));
        r.setExpiresAt(time(rs, "expires_at"));
        r.setStatus(ReservationStatus.valueOf(rs.getString("status")));
        return r;
    }

    @Override public Reservation findReservation(int id) { return one("SELECT * FROM reservations WHERE reservation_id=?" + lock(), JdbcStore::mapReservation, id); }

    @Override
    public Reservation saveReservation(Reservation r) {
        if (r.getReservationId() == 0) {
            r.setReservationId(insert("INSERT INTO reservations(member_id,book_id,reserved_at,expires_at,status) VALUES(?,?,?,?,?)",
                    r.getMemberId(), r.getBookId(), r.getReservedAt(), r.getExpiresAt(), r.getStatus()));
        } else {
            update("UPDATE reservations SET expires_at=?,status=? WHERE reservation_id=?", r.getExpiresAt(), r.getStatus(), r.getReservationId());
        }
        return r;
    }

    @Override
    public List<Reservation> activeReservationsOfBook(int bookId) {
        return query("SELECT * FROM reservations WHERE book_id=? AND status IN ('WAITING','READY') ORDER BY reserved_at, reservation_id", JdbcStore::mapReservation, bookId);
    }

    @Override
    public List<Reservation> activeReservationsOfMember(int memberId) {
        return query("SELECT * FROM reservations WHERE member_id=? AND status IN ('WAITING','READY') ORDER BY reserved_at, reservation_id", JdbcStore::mapReservation, memberId);
    }

    @Override
    public List<Reservation> reservationsOfMember(int memberId) {
        return query("SELECT * FROM reservations WHERE member_id=? ORDER BY reservation_id DESC", JdbcStore::mapReservation, memberId);
    }

    @Override
    public List<Reservation> expiredReady(LocalDateTime now) {
        return query("SELECT * FROM reservations WHERE status='READY' AND expires_at IS NOT NULL AND expires_at < ?", JdbcStore::mapReservation, now);
    }

    // ================================================================ reports
    @Override
    public List<Object[]> mostBorrowed(LocalDate from, LocalDate to, int limit) {
        return query("SELECT b.book_id, b.title, b.isbn, COUNT(*) AS cnt FROM loans l JOIN book_copies c ON c.copy_id=l.copy_id "
                        + "JOIN books b ON b.book_id=c.book_id WHERE l.loan_date BETWEEN ? AND ? GROUP BY b.book_id, b.title, b.isbn "
                        + "ORDER BY cnt DESC, b.book_id LIMIT ?",
                rs -> new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3), rs.getLong(4)}, from, to, limit);
    }

    @Override
    public List<Loan> overdueLoans(LocalDate from, LocalDate to, LocalDate today) {
        return query("SELECT * FROM loans WHERE due_date BETWEEN ? AND ? AND ((return_date IS NOT NULL AND return_date > due_date) "
                + "OR (return_date IS NULL AND status='BORROWED' AND ? > due_date)) ORDER BY due_date", JdbcStore::mapLoan, from, to, today);
    }

    @Override
    public List<Fine> paidFinesBetween(LocalDateTime from, LocalDateTime to) {
        return query("SELECT * FROM fines WHERE status='PAID' AND paid_at BETWEEN ? AND ? ORDER BY paid_at", JdbcStore::mapFine, from, to);
    }

    @Override
    public Map<String, Long> memberStats(LocalDate from, LocalDate to) {
        String base = "SELECT COUNT(*) FROM members m JOIN users u ON u.user_id=m.user_id WHERE u.deleted=0";
        Map<String, Long> r = new LinkedHashMap<>();
        r.put("totalMembers", count(base));
        r.put("activeMembers", count(base + " AND u.status='ACTIVE'"));
        r.put("newMembers", count(base + " AND DATE(u.created_at) BETWEEN ? AND ?", from, to));
        r.put("borrowers", count("SELECT COUNT(DISTINCT member_id) FROM loans WHERE loan_date BETWEEN ? AND ?", from, to));
        r.put("loans", count("SELECT COUNT(*) FROM loans WHERE loan_date BETWEEN ? AND ?", from, to));
        return r;
    }

    // ================================================================= config
    @Override
    public String getConfig(String key, String defaultValue) {
        String v = one("SELECT cfg_value FROM config WHERE cfg_key=?", rs -> rs.getString(1), key);
        return v == null ? defaultValue : v;
    }

    @Override
    public void setConfig(String key, String value) {
        update("INSERT INTO config(cfg_key,cfg_value) VALUES(?,?) ON DUPLICATE KEY UPDATE cfg_value=VALUES(cfg_value)", key, value);
    }
}
