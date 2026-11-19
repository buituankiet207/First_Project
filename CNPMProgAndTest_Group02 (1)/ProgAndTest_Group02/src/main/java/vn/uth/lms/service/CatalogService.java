package vn.uth.lms.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import vn.uth.lms.model.*;
import vn.uth.lms.persistence.LibraryStore;
import vn.uth.lms.persistence.PageResult;
import vn.uth.lms.persistence.RefRepository;
import vn.uth.lms.util.AppException;
import vn.uth.lms.util.TextUtil;

/** Use cases: Search book, View book details, Manage books (books, copies, authors, categories, publishers). */
public class CatalogService {
    private final LibraryStore store;
    private final Clock clock;
    private final ReservationService reservations;

    public CatalogService(LibraryStore store, Clock clock, ReservationService reservations) {
        this.store = store;
        this.clock = clock;
        this.reservations = reservations;
    }

    // ------------------------------------------------------------ search
    public PageResult<Book> search(String query, String type, int categoryId, String sort, int page) {
        reservations.refresh();
        String kw = TextUtil.normalize(query);
        SearchType t;
        try { t = TextUtil.isBlank(type) ? SearchType.ALL : SearchType.valueOf(type.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { t = SearchType.ALL; }
        boolean newest = TextUtil.isBlank(sort) ? kw.isEmpty() : sort.equalsIgnoreCase("newest");
        return store.searchBooks(kw, t, categoryId, newest, Math.max(1, page), Rules.PAGE_SIZE);
    }

    public Book getBook(int id) {
        reservations.refresh();
        Book b = store.findBook(id);
        if (b == null || b.isDeleted()) throw AppException.notFound("This book no longer exists");
        return b;
    }

    // ------------------------------------------------------------ books
    public Book addBook(BookInput in) {
        validate(in, true);
        return store.tx(() -> {
            if (store.findBookByIsbn(in.isbn().trim()) != null) throw AppException.conflict("ISBN already exists").field("isbn");
            Book b = new Book();
            apply(b, in);
            store.saveBook(b);
            int n = in.copies() == null ? 0 : in.copies();
            for (int i = 0; i < n; i++) {
                BookCopy c = new BookCopy();
                c.setBookId(b.getBookId());
                c.setStatus(CopyStatus.AVAILABLE);
                c.setShelfLocation(TextUtil.trim(in.shelfLocation()));
                store.saveCopy(c);
            }
            return store.findBook(b.getBookId());
        });
    }

    public Book updateBook(int id, BookInput in) {
        validate(in, false);
        return store.tx(() -> {
            Book b = store.findBook(id);
            if (b == null || b.isDeleted()) throw AppException.notFound("Book not found");
            Book same = store.findBookByIsbn(in.isbn().trim());
            if (same != null && same.getBookId() != id) throw AppException.conflict("ISBN already exists").field("isbn");
            apply(b, in);
            store.saveBook(b);
            return store.findBook(id);
        });
    }

    /** Deleting is refused while the book is borrowed or reserved; otherwise it is hidden (history is kept). */
    public void deleteBook(int id) {
        store.tx(() -> {
            Book b = store.findBook(id);
            if (b == null || b.isDeleted()) throw AppException.notFound("Book not found");
            if (store.bookHasActiveLoan(id)) throw AppException.conflict("The book cannot be deleted because it has active loans");
            if (!store.activeReservationsOfBook(id).isEmpty())
                throw AppException.conflict("The book cannot be deleted because it has active reservations");
            b.setDeleted(true);
            b.setIsbn(b.getIsbn() + "#" + id);
            store.saveBook(b);
            return null;
        });
    }

    private void validate(BookInput in, boolean adding) {
        if (TextUtil.isBlank(in.isbn())) throw AppException.badRequest("ISBN is required").field("isbn");
        if (!in.isbn().trim().matches("[0-9Xx-]{10,17}")) throw AppException.badRequest("ISBN must contain 10-13 digits (hyphens allowed)").field("isbn");
        if (TextUtil.isBlank(in.title())) throw AppException.badRequest("Title is required").field("title");
        if (in.authors() == null || in.authors().stream().allMatch(TextUtil::isBlank)) throw AppException.badRequest("At least one author is required").field("authors");
        if (TextUtil.isBlank(in.category())) throw AppException.badRequest("Category is required").field("category");
        if (TextUtil.isBlank(in.publisher())) throw AppException.badRequest("Publisher is required").field("publisher");
        int maxYear = LocalDate.now(clock).getYear() + 1;
        if (in.publishYear() == null || in.publishYear() < 1000 || in.publishYear() > maxYear)
            throw AppException.badRequest("Publish year must be between 1000 and " + maxYear).field("publishYear");
        if (adding && in.copies() != null && (in.copies() < 0 || in.copies() > 500))
            throw AppException.badRequest("Number of copies must be between 0 and 500").field("copies");
    }

    private void apply(Book b, BookInput in) {
        b.setIsbn(in.isbn().trim());
        b.setTitle(in.title().trim());
        b.setPublishYear(in.publishYear());
        b.setDescription(TextUtil.trim(in.description()));
        b.setCategoryId(findOrCreate(store.categories(), in.category(), Category::new).getCategoryId());
        Publisher p = store.publishers().findByName(in.publisher());
        if (p == null) {
            p = new Publisher();
            p.setName(in.publisher().trim());
            store.publishers().save(p);
        }
        b.setPublisherId(p.getPublisherId());
        List<Author> as = new ArrayList<>();
        for (String name : in.authors()) {
            if (TextUtil.isBlank(name)) continue;
            Author a = findOrCreate(store.authors(), name, Author::new);
            if (as.stream().noneMatch(x -> x.getAuthorId() == a.getAuthorId())) as.add(a);
        }
        b.setAuthors(as);
    }

    private <T extends NamedEntity> T findOrCreate(RefRepository<T> repo, String name, java.util.function.Supplier<T> factory) {
        T t = repo.findByName(name);
        if (t == null) {
            t = factory.get();
            t.setName(name.trim());
            repo.save(t);
        }
        return t;
    }

    // ----------------------------------------------------------- copies
    public List<BookCopy> copies(int bookId) {
        getBook(bookId);
        return store.copiesOfBook(bookId);
    }

    public BookCopy addCopy(int bookId, String shelfLocation) {
        return store.tx(() -> {
            getBook(bookId);
            BookCopy c = new BookCopy();
            c.setBookId(bookId);
            c.setStatus(CopyStatus.AVAILABLE);
            c.setShelfLocation(TextUtil.trim(shelfLocation));
            store.saveCopy(c);
            reservations.onCopyFreed(c);
            return c;
        });
    }

    /** status may be AVAILABLE, DAMAGED or LOST; BORROWED / RESERVED are managed by the system. */
    public BookCopy updateCopy(int copyId, String status, String shelfLocation) {
        return store.tx(() -> {
            BookCopy c = store.findCopy(copyId);
            if (c == null) throw AppException.notFound("Copy not found");
            c.setShelfLocation(TextUtil.trim(shelfLocation));
            if (!TextUtil.isBlank(status) && !status.equalsIgnoreCase(c.getStatus().name())) {
                CopyStatus ns;
                try { ns = CopyStatus.valueOf(status.trim().toUpperCase()); }
                catch (IllegalArgumentException e) { throw AppException.badRequest("Unknown copy status").field("status"); }
                if (c.getStatus() == CopyStatus.BORROWED || c.getStatus() == CopyStatus.RESERVED)
                    throw AppException.conflict("The copy is " + c.getStatus() + ": return or release it before changing its status");
                if (ns == CopyStatus.BORROWED || ns == CopyStatus.RESERVED)
                    throw AppException.badRequest("BORROWED and RESERVED are set by the system").field("status");
                c.updateStatus(ns);
                store.saveCopy(c);
                if (ns == CopyStatus.AVAILABLE) reservations.onCopyFreed(c);
            } else {
                store.saveCopy(c);
            }
            return c;
        });
    }

    public void deleteCopy(int copyId) {
        store.tx(() -> {
            BookCopy c = store.findCopy(copyId);
            if (c == null) throw AppException.notFound("Copy not found");
            if (c.getStatus() == CopyStatus.BORROWED || c.getStatus() == CopyStatus.RESERVED)
                throw AppException.conflict("The copy is " + c.getStatus() + " and cannot be deleted");
            if (store.copyHasLoans(copyId))
                throw AppException.conflict("The copy has loan history and cannot be deleted; mark it as LOST instead");
            store.deleteCopy(copyId);
            return null;
        });
    }

    // ---------------------------------------------- categories / authors / publishers
    public List<Category> listCategories() { return store.categories().list(); }
    public List<Author> listAuthors() { return store.authors().list(); }
    public List<Publisher> listPublishers() { return store.publishers().list(); }

    public Category addCategory(String name) { return addNamed(store.categories(), name, Category::new, "Category"); }
    public Author addAuthor(String name) { return addNamed(store.authors(), name, Author::new, "Author"); }
    public Publisher addPublisher(String name, String address) {
        Publisher p = addNamed(store.publishers(), name, Publisher::new, "Publisher");
        p.setAddress(TextUtil.trim(address));
        return store.publishers().save(p);
    }

    public Category updateCategory(int id, String name) { return renameNamed(store.categories(), id, name, "Category"); }
    public Author updateAuthor(int id, String name) { return renameNamed(store.authors(), id, name, "Author"); }
    public Publisher updatePublisher(int id, String name, String address) {
        Publisher p = renameNamed(store.publishers(), id, name, "Publisher");
        p.setAddress(TextUtil.trim(address));
        return store.publishers().save(p);
    }

    public void deleteCategory(int id) { deleteNamed(store.categories(), id, "Category"); }
    public void deleteAuthor(int id) { deleteNamed(store.authors(), id, "Author"); }
    public void deletePublisher(int id) { deleteNamed(store.publishers(), id, "Publisher"); }

    private <T extends NamedEntity> T addNamed(RefRepository<T> repo, String name, java.util.function.Supplier<T> factory, String label) {
        if (TextUtil.isBlank(name)) throw AppException.badRequest(label + " name is required").field("name");
        return store.tx(() -> {
            if (repo.findByName(name) != null) throw AppException.conflict(label + " already exists").field("name");
            T t = factory.get();
            t.setName(name.trim());
            return repo.save(t);
        });
    }

    private <T extends NamedEntity> T renameNamed(RefRepository<T> repo, int id, String name, String label) {
        if (TextUtil.isBlank(name)) throw AppException.badRequest(label + " name is required").field("name");
        return store.tx(() -> {
            T t = repo.find(id);
            if (t == null) throw AppException.notFound(label + " not found");
            T same = repo.findByName(name);
            if (same != null && same.getId() != id) throw AppException.conflict(label + " already exists").field("name");
            t.setName(name.trim());
            return repo.save(t);
        });
    }

    private <T extends NamedEntity> void deleteNamed(RefRepository<T> repo, int id, String label) {
        store.tx(() -> {
            if (repo.find(id) == null) throw AppException.notFound(label + " not found");
            int used = repo.usageCount(id);
            if (used > 0) throw AppException.conflict(label + " is used by " + used + " book(s) and cannot be deleted");
            repo.delete(id);
            return null;
        });
    }
}
