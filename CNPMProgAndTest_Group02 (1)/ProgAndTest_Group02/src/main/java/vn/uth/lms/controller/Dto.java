package vn.uth.lms.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import vn.uth.lms.model.*;

/** Converts model objects to the JSON structures sent to the browser (never includes password hashes). */
final class Dto {
    private Dto() {}

    static Map<String, Object> user(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", u.getUserId());
        m.put("username", u.getUsername());
        m.put("fullName", u.getFullName());
        m.put("email", u.getEmail());
        m.put("phone", u.getPhone());
        m.put("status", u.getStatus());
        m.put("role", u.getRole());
        m.put("createdAt", u.getCreatedAt());
        if (u instanceof Member x) {
            m.put("address", x.getAddress());
            m.put("dateOfBirth", x.getDateOfBirth());
            m.put("cardNumber", x.getCardNumber());
            m.put("cardExpiry", x.getCardExpiry());
        }
        if (u instanceof Librarian l) {
            m.put("staffCode", l.getStaffCode());
            m.put("hireDate", l.getHireDate());
        }
        return m;
    }

    static Map<String, Object> book(Book b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bookId", b.getBookId());
        m.put("isbn", b.getIsbn());
        m.put("title", b.getTitle());
        List<String> names = new ArrayList<>();
        b.getAuthors().forEach(a -> names.add(a.getName()));
        m.put("authors", names);
        m.put("category", b.getCategoryName());
        m.put("categoryId", b.getCategoryId());
        m.put("publisher", b.getPublisherName());
        m.put("publishYear", b.getPublishYear());
        m.put("description", b.getDescription());
        m.put("availableCopies", b.getAvailableCopies());
        m.put("totalCopies", b.getTotalCopies());
        m.put("shelfLocation", b.getShelfLocation());
        return m;
    }

    static Map<String, Object> copy(BookCopy c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("copyId", c.getCopyId());
        m.put("bookId", c.getBookId());
        m.put("barcode", c.getBarcode());
        m.put("status", c.getStatus());
        m.put("shelfLocation", c.getShelfLocation());
        return m;
    }

    static Map<String, Object> named(NamedEntity n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("name", n.getName());
        if (n instanceof Publisher p) m.put("address", p.getAddress());
        return m;
    }

    static Map<String, Object> fine(Fine f, String title, String memberName) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fineId", f.getFineId());
        m.put("loanId", f.getLoanId());
        m.put("amount", f.getAmount());
        m.put("reason", f.getReason());
        m.put("status", f.getStatus());
        m.put("createdAt", f.getCreatedAt());
        m.put("paidAt", f.getPaidAt());
        m.put("title", title);
        m.put("member", memberName);
        return m;
    }

    static Map<String, Object> reservation(Reservation r, String title, int position) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reservationId", r.getReservationId());
        m.put("bookId", r.getBookId());
        m.put("title", title);
        m.put("reservedAt", r.getReservedAt());
        m.put("expiresAt", r.getExpiresAt());
        m.put("status", r.getStatus());
        m.put("position", position);
        return m;
    }
}
