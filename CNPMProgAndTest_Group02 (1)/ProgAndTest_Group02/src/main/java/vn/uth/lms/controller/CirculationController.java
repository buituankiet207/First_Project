package vn.uth.lms.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import vn.uth.lms.app.AppContext;
import vn.uth.lms.model.*;
import vn.uth.lms.service.LoanService;
import vn.uth.lms.util.AppException;
import vn.uth.lms.web.Access;
import vn.uth.lms.web.Router;

/** Controller for: Reserve book, Renew loan, View loan history, Lend book, Return book, Collect fine. */
public class CirculationController {

    public void register(Router r, AppContext app) {
        // ---------------------------------------------------- member: reservations
        r.post("/api/reservations", Access.MEMBER, c -> {
            Reservation res = ((Member) c.user).reserveBook(app.reservations, c.intOf("bookId", 0));
            Book b = app.store.findBook(res.getBookId());
            Map<String, Object> m = new LinkedHashMap<>(Dto.reservation(res, b.getTitle(), app.reservations.queuePosition(res)));
            m.put("message", "Reservation created. Your position in the queue: " + m.get("position"));
            return m;
        });
        r.get("/api/reservations", Access.MEMBER, c -> {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Reservation res : app.reservations.mine(c.user.getUserId())) {
                Book b = app.store.findBook(res.getBookId());
                rows.add(Dto.reservation(res, b == null ? "" : b.getTitle(), res.isActive() ? app.reservations.queuePosition(res) : 0));
            }
            return rows;
        });
        r.delete("/api/reservations/{id}", Access.MEMBER, c -> { app.reservations.cancel(c.user.getUserId(), c.pathInt(0)); return null; });

        // ------------------------------------------------- member: loans and renew
        r.get("/api/loans/mine", Access.MEMBER, c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("loans", ((Member) c.user).viewLoanHistory(app.loans, c.str("status")));
            m.put("unpaidFines", app.fines.unpaidTotal(c.user.getUserId()));
            return m;
        });
        r.post("/api/loans/{id}/renew", Access.MEMBER, c -> {
            Loan l = ((Member) c.user).renewLoan(app.loans, c.pathInt(0));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("loanId", l.getLoanId());
            m.put("dueDate", l.getDueDate());
            m.put("renewCount", l.getRenewCount());
            m.put("message", "Loan renewed. New due date: " + l.getDueDate());
            return m;
        });

        // ------------------------------------------------- librarian: lend / return
        r.get("/api/lending/member", Access.STAFF, c -> app.loans.checkMember(c.str("card")));

        r.post("/api/lending/lend", Access.STAFF, c -> {
            Librarian lib = (Librarian) c.user;
            List<Loan> loans = lib.lendBook(app.loans, c.str("card"), c.strings("barcodes"));
            Member m = app.store.findMemberByCard(c.str("card").trim());
            List<Map<String, Object>> items = new ArrayList<>();
            for (Loan l : loans) {
                BookCopy copy = app.store.findCopy(l.getCopyId());
                Book b = app.store.findBook(copy.getBookId());
                Map<String, Object> it = new LinkedHashMap<>();
                it.put("loanId", l.getLoanId());
                it.put("barcode", copy.getBarcode());
                it.put("title", b.getTitle());
                it.put("loanDate", l.getLoanDate());
                it.put("dueDate", l.getDueDate());
                items.add(it);
            }
            Map<String, Object> slip = new LinkedHashMap<>();
            slip.put("member", m.getFullName());
            slip.put("cardNumber", m.getCardNumber());
            slip.put("librarian", lib.getUsername());
            slip.put("items", items);
            return Map.of("message", "Loan confirmed: " + loans.size() + " book(s) lent", "slip", slip);
        });

        r.post("/api/lending/return", Access.STAFF, c -> {
            Librarian lib = (Librarian) c.user;
            LoanService.ReturnResult res = lib.returnBook(app.loans, c.str("barcode"), c.str("condition"), c.decimal("replacementPrice"));
            Book b = app.store.findBook(res.copy.getBookId());
            List<Map<String, Object>> fines = new ArrayList<>();
            for (Fine f : res.fines) fines.add(Dto.fine(f, b.getTitle(), res.member.getFullName()));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("message", res.fines.isEmpty() ? "Book returned. No fine." : "Book returned. A fine must be collected.");
            m.put("title", b.getTitle());
            m.put("barcode", res.copy.getBarcode());
            m.put("member", res.member.getFullName());
            m.put("returnDate", res.loan.getReturnDate());
            m.put("copyStatus", res.copy.getStatus());
            m.put("fines", fines);
            return m;
        });

        // ---------------------------------------------------- librarian: fines
        r.get("/api/fines", Access.STAFF, c -> {
            String card = c.str("card");
            if (card == null || card.isBlank()) throw AppException.badRequest("Please enter the member card number").field("card");
            Member m = app.store.findMemberByCard(card.trim());
            if (m == null) throw AppException.notFound("No member found with this card number").field("card");
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Fine f : app.store.finesOfMember(m.getUserId())) {
                Loan l = app.store.findLoan(f.getLoanId());
                BookCopy copy = app.store.findCopy(l.getCopyId());
                rows.add(Dto.fine(f, app.store.findBook(copy.getBookId()).getTitle(), m.getFullName()));
            }
            return Map.of("member", m.getFullName(), "cardNumber", m.getCardNumber(), "fines", rows);
        });

        r.post("/api/fines/{id}/pay", Access.STAFF, c -> {
            Fine f = ((Librarian) c.user).collectFine(app.fines, c.pathInt(0));
            Loan l = app.store.findLoan(f.getLoanId());
            BookCopy copy = app.store.findCopy(l.getCopyId());
            Member m = (Member) app.store.findUser(l.getMemberId());
            Map<String, Object> receipt = new LinkedHashMap<>(Dto.fine(f, app.store.findBook(copy.getBookId()).getTitle(), m.getFullName()));
            receipt.put("cardNumber", m.getCardNumber());
            receipt.put("librarian", c.user.getUsername());
            return Map.of("message", "Payment recorded", "receipt", receipt);
        });
    }
}
