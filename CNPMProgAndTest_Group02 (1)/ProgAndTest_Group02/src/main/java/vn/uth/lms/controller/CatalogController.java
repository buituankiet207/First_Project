package vn.uth.lms.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import vn.uth.lms.app.AppContext;
import vn.uth.lms.model.Book;
import vn.uth.lms.model.BookCopy;
import vn.uth.lms.persistence.PageResult;
import vn.uth.lms.service.BookInput;
import vn.uth.lms.util.AppException;
import vn.uth.lms.web.Access;
import vn.uth.lms.web.Ctx;
import vn.uth.lms.web.Router;

/** Controller for: Search book, View book details, Manage books (books, copies, authors, categories, publishers). */
public class CatalogController {

    private static BookInput bookInput(Ctx c) {
        return new BookInput(c.str("isbn"), c.str("title"), c.strings("authors"), c.str("category"), c.str("publisher"),
                c.integer("publishYear"), c.str("description"), c.integer("copies"), c.str("shelfLocation"));
    }

    public void register(Router r, AppContext app) {
        r.get("/api/books", Access.PUBLIC, c -> {
            PageResult<Book> p = app.catalog.search(c.str("q"), c.str("type"), c.intOf("category", 0), c.str("sort"), c.intOf("page", 1));
            List<Map<String, Object>> items = new ArrayList<>();
            p.getItems().forEach(b -> items.add(Dto.book(b)));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("items", items);
            m.put("page", p.getPage());
            m.put("totalPages", p.getTotalPages());
            m.put("total", p.getTotal());
            return m;
        });
        r.get("/api/books/{id}", Access.PUBLIC, c -> Dto.book(app.catalog.getBook(c.pathInt(0))));
        r.post("/api/books", Access.STAFF, c -> Dto.book(app.catalog.addBook(bookInput(c))));
        r.put("/api/books/{id}", Access.STAFF, c -> Dto.book(app.catalog.updateBook(c.pathInt(0), bookInput(c))));
        r.delete("/api/books/{id}", Access.STAFF, c -> { app.catalog.deleteBook(c.pathInt(0)); return null; });

        // copies
        r.get("/api/books/{id}/copies", Access.STAFF, c -> app.catalog.copies(c.pathInt(0)).stream().map(Dto::copy).toList());
        r.post("/api/books/{id}/copies", Access.STAFF, c -> Dto.copy(app.catalog.addCopy(c.pathInt(0), c.str("shelfLocation"))));
        r.put("/api/copies/{id}", Access.STAFF, c -> Dto.copy(app.catalog.updateCopy(c.pathInt(0), c.str("status"), c.str("shelfLocation"))));
        r.delete("/api/copies/{id}", Access.STAFF, c -> { app.catalog.deleteCopy(c.pathInt(0)); return null; });

        r.get("/api/copies/lookup", Access.STAFF, c -> {
            BookCopy copy = app.store.findCopyByBarcode(c.str("barcode"));
            if (copy == null) throw AppException.badRequest("Invalid barcode: " + (c.str("barcode") == null ? "" : c.str("barcode").trim())).field("barcode");
            Book b = app.store.findBook(copy.getBookId());
            Map<String, Object> m = new LinkedHashMap<>(Dto.copy(copy));
            m.put("title", b.getTitle());
            return m;
        });

        // categories
        r.get("/api/categories", Access.PUBLIC, c -> app.catalog.listCategories().stream().map(Dto::named).toList());
        r.post("/api/categories", Access.STAFF, c -> Dto.named(app.catalog.addCategory(c.str("name"))));
        r.put("/api/categories/{id}", Access.STAFF, c -> Dto.named(app.catalog.updateCategory(c.pathInt(0), c.str("name"))));
        r.delete("/api/categories/{id}", Access.STAFF, c -> { app.catalog.deleteCategory(c.pathInt(0)); return null; });
        // authors
        r.get("/api/authors", Access.STAFF, c -> app.catalog.listAuthors().stream().map(Dto::named).toList());
        r.post("/api/authors", Access.STAFF, c -> Dto.named(app.catalog.addAuthor(c.str("name"))));
        r.put("/api/authors/{id}", Access.STAFF, c -> Dto.named(app.catalog.updateAuthor(c.pathInt(0), c.str("name"))));
        r.delete("/api/authors/{id}", Access.STAFF, c -> { app.catalog.deleteAuthor(c.pathInt(0)); return null; });
        // publishers
        r.get("/api/publishers", Access.STAFF, c -> app.catalog.listPublishers().stream().map(Dto::named).toList());
        r.post("/api/publishers", Access.STAFF, c -> Dto.named(app.catalog.addPublisher(c.str("name"), c.str("address"))));
        r.put("/api/publishers/{id}", Access.STAFF, c -> Dto.named(app.catalog.updatePublisher(c.pathInt(0), c.str("name"), c.str("address"))));
        r.delete("/api/publishers/{id}", Access.STAFF, c -> { app.catalog.deletePublisher(c.pathInt(0)); return null; });
    }
}
