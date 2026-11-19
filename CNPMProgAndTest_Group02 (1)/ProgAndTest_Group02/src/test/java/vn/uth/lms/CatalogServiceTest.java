package vn.uth.lms;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.uth.lms.model.*;
import vn.uth.lms.persistence.PageResult;
import vn.uth.lms.service.BookInput;
import vn.uth.lms.util.AppException;

/** Use cases: Search book, View book details, Manage books. */
class CatalogServiceTest {
    Fixture f;

    @BeforeEach
    void setUp() { f = new Fixture(); }

    private AppException fails(Runnable r) { return assertThrows(AppException.class, r::run); }

    private PageResult<Book> search(String q, String type) { return f.app.catalog.search(q, type, 0, null, 1); }

    private BookInput input(String isbn, String title, int copies) {
        return new BookInput(isbn, title, List.of("Joshua Bloch"), "Programming", "Addison-Wesley", 2018, "desc", copies, "Shelf Z1");
    }

    // ------------------------------------------------------------------ search
    @Test
    void searchByTitle() {
        PageResult<Book> r = search("clean code", "TITLE");
        assertEquals(1, r.getTotal());
        assertEquals("Clean Code: A Handbook", r.getItems().get(0).getTitle());
    }

    @Test
    void searchByAuthor() {
        assertEquals(2, search("martin", "AUTHOR").getTotal());
    }

    @Test
    void searchByCategory() {
        assertEquals(2, search("programming", "CATEGORY").getTotal());
    }

    @Test
    void searchByIsbnWithOrWithoutHyphens() {
        assertEquals(1, search("978-0-13-235088-4", "ISBN").getTotal());
        assertEquals(1, search("9780132350884", "ISBN").getTotal());
    }

    @Test
    void searchIgnoresCaseAndAccents() {
        assertEquals(1, search("cLeAn CoDe", "ALL").getTotal());
        assertEquals("Lập trình Java", search("LAP TRINH", "ALL").getItems().get(0).getTitle());
        assertEquals(1, search("nguyen van hieu", "AUTHOR").getTotal());
    }

    @Test
    void emptyKeywordListsAllBooksNewestFirst() {
        PageResult<Book> r = search("", "ALL");
        assertEquals(9, r.getTotal());
        assertEquals("Lập trình Java", r.getItems().get(0).getTitle());
    }

    @Test
    void keywordResultsAreSortedByTitle() {
        PageResult<Book> r = search("design", "ALL");
        assertEquals(4, r.getTotal());
        assertEquals("Design Systems", r.getItems().get(0).getTitle());
        assertEquals("The Design of Everyday Things", r.getItems().get(3).getTitle());
    }

    @Test
    void unknownKeywordGivesNoResult() {
        assertEquals(0, search("zzzxxx999", "ALL").getTotal());
        assertEquals(0, search("' OR 1=1 --", "ALL").getTotal());
    }

    @Test
    void categoryFilterNarrowsTheResult() {
        int programming = f.store.categories().findByName("Programming").getCategoryId();
        assertEquals(2, f.app.catalog.search("", "ALL", programming, null, 1).getTotal());
    }

    @Test
    void resultsAreSplitIntoPagesOfTen() {
        for (int i = 0; i < 16; i++) f.app.catalog.addBook(input(String.format("978-1-00000-%03d-0", i), "Extra " + i, 1));
        assertEquals(25, search("", "ALL").getTotal());
        assertEquals(10, f.app.catalog.search("", "ALL", 0, null, 1).getItems().size());
        assertEquals(10, f.app.catalog.search("", "ALL", 0, null, 2).getItems().size());
        PageResult<Book> last = f.app.catalog.search("", "ALL", 0, null, 3);
        assertEquals(5, last.getItems().size());
        assertEquals(3, last.getTotalPages());
    }

    // ------------------------------------------------------------ book details
    @Test
    void detailsShowAvailableCopiesAndShelf() {
        Book b = f.app.catalog.getBook(2);
        assertEquals(3, b.getTotalCopies());
        assertEquals(2, b.getAvailableCopies());
        assertEquals("Shelf A1", b.getShelfLocation());
        assertEquals("Robert C. Martin", b.authorNames());
        assertEquals("Prentice Hall", b.getPublisherName());
    }

    @Test
    void bookWithoutAvailableCopyShowsZero() {
        assertEquals(0, f.app.catalog.getBook(6).getAvailableCopies());
    }

    @Test
    void unknownBookIsReported() {
        assertEquals(404, fails(() -> f.app.catalog.getBook(999)).getStatus());
    }

    // ------------------------------------------------------------ manage books
    @Test
    void addBookCreatesTheRequestedNumberOfCopies() {
        Book b = f.app.catalog.addBook(input("978-0-13-468599-1", "Effective Java", 3));
        assertEquals(3, b.getTotalCopies());
        assertEquals(3, b.getAvailableCopies());
        List<BookCopy> copies = f.store.copiesOfBook(b.getBookId());
        assertEquals(3, copies.stream().map(BookCopy::getBarcode).distinct().count());
        assertEquals("Joshua Bloch", b.authorNames());
    }

    @Test
    void duplicateIsbnIsRefused() {
        AppException e = fails(() -> f.app.catalog.addBook(input("978-0-13-235088-4", "Other", 1)));
        assertEquals("ISBN already exists", e.getMessage());
        assertEquals("isbn", e.getField());
    }

    @Test
    void requiredFieldsAreChecked() {
        assertEquals("title", fails(() -> f.app.catalog.addBook(input("978-0-13-468599-1", "", 1))).getField());
        assertEquals("isbn", fails(() -> f.app.catalog.addBook(input("abc", "T", 1))).getField());
        assertEquals("publishYear", fails(() -> f.app.catalog.addBook(new BookInput("978-0-13-468599-1", "T", List.of("A"), "C", "P", 99, "", 1, ""))).getField());
        assertEquals("authors", fails(() -> f.app.catalog.addBook(new BookInput("978-0-13-468599-1", "T", List.of(), "C", "P", 2018, "", 1, ""))).getField());
    }

    @Test
    void updateBookChangesTitleAndAuthors() {
        Book b = f.app.catalog.updateBook(2, new BookInput("978-0-13-235088-4", "Clean Code, 2nd Ed", List.of("Robert C. Martin", "Someone Else"), "Programming", "Prentice Hall", 2008, "new", null, null));
        assertEquals("Clean Code, 2nd Ed", b.getTitle());
        assertEquals(2, b.getAuthors().size());
        assertEquals(3, b.getTotalCopies());
    }

    @Test
    void updateCannotUseTheIsbnOfAnotherBook() {
        assertEquals("ISBN already exists", fails(() -> f.app.catalog.updateBook(2, new BookInput("978-0-465-05065-9", "X", List.of("A"), "C", "P", 2018, "", null, null))).getMessage());
    }

    @Test
    void bookWithActiveLoanCannotBeDeleted() {
        assertEquals(409, fails(() -> f.app.catalog.deleteBook(2)).getStatus());
    }

    @Test
    void bookWithActiveReservationCannotBeDeleted() {
        f.app.reservations.reserve(f.member("member01").getUserId(), 6);
        assertEquals(409, fails(() -> f.app.catalog.deleteBook(6)).getStatus());
    }

    @Test
    void deletedBookDisappearsAndItsIsbnCanBeUsedAgain() {
        f.app.catalog.deleteBook(8);
        assertEquals(0, search("discrete", "TITLE").getTotal());
        assertEquals(404, fails(() -> f.app.catalog.getBook(8)).getStatus());
        assertNotNull(f.app.catalog.addBook(input("978-1-259-67651-2", "Discrete Mathematics 2", 1)));
    }

    // ------------------------------------------------------------------ copies
    @Test
    void addCopyIncreasesAvailability() {
        BookCopy c = f.app.catalog.addCopy(2, "Shelf A9");
        assertEquals(CopyStatus.AVAILABLE, c.getStatus());
        assertEquals(3, f.app.catalog.getBook(2).getAvailableCopies());
    }

    @Test
    void damagedCopyIsNotAvailable() {
        BookCopy c = f.copy("BC-000005");
        f.app.catalog.updateCopy(c.getCopyId(), "DAMAGED", c.getShelfLocation());
        assertEquals(1, f.app.catalog.getBook(2).getAvailableCopies());
        f.app.catalog.updateCopy(c.getCopyId(), "AVAILABLE", c.getShelfLocation());
        assertEquals(2, f.app.catalog.getBook(2).getAvailableCopies());
    }

    @Test
    void borrowedCopyStatusCannotBeChangedByHand() {
        BookCopy c = f.copy("BC-000004");
        assertEquals(409, fails(() -> f.app.catalog.updateCopy(c.getCopyId(), "AVAILABLE", "x")).getStatus());
    }

    @Test
    void copyWithLoanHistoryCannotBeDeleted() {
        assertEquals(409, fails(() -> f.app.catalog.deleteCopy(f.copy("BC-000001").getCopyId())).getStatus());
        BookCopy fresh = f.app.catalog.addCopy(2, "Z");
        f.app.catalog.deleteCopy(fresh.getCopyId());
        assertNull(f.store.findCopy(fresh.getCopyId()));
    }

    // -------------------------------------------------- categories, authors, ...
    @Test
    void categoryInUseCannotBeDeleted() {
        int id = f.store.categories().findByName("Programming").getCategoryId();
        assertEquals(409, fails(() -> f.app.catalog.deleteCategory(id)).getStatus());
    }

    @Test
    void unusedCategoryCanBeAddedRenamedAndDeleted() {
        Category c = f.app.catalog.addCategory("History");
        assertEquals("Category already exists", fails(() -> f.app.catalog.addCategory("history")).getMessage());
        f.app.catalog.updateCategory(c.getCategoryId(), "World history");
        assertEquals("World history", f.store.categories().find(c.getCategoryId()).getName());
        f.app.catalog.deleteCategory(c.getCategoryId());
        assertNull(f.store.categories().find(c.getCategoryId()));
    }

    @Test
    void publisherKeepsItsAddress() {
        Publisher p = f.app.catalog.addPublisher("Kim Dong", "55 Quang Trung");
        assertEquals("55 Quang Trung", f.store.publishers().find(p.getPublisherId()).getAddress());
    }
}
