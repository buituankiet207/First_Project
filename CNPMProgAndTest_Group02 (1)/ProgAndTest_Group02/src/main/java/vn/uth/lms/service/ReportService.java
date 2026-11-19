package vn.uth.lms.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import vn.uth.lms.model.*;
import vn.uth.lms.persistence.LibraryStore;
import vn.uth.lms.util.AppException;
import vn.uth.lms.util.TextUtil;

/** Use case: View reports (admin only). */
public class ReportService {
    public static final List<String> TYPES = List.of("most-borrowed", "overdue", "fines", "members");

    private final LibraryStore store;
    private final Clock clock;

    public ReportService(LibraryStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public Report generate(String type, String fromText, String toText) {
        if (type == null || !TYPES.contains(type)) throw AppException.badRequest("Unknown report type");
        if (TextUtil.isBlank(fromText) || TextUtil.isBlank(toText)) throw AppException.badRequest("Please choose the start and end date");
        LocalDate from = AuthService.parseDate(fromText, "from", "Start date");
        LocalDate to = AuthService.parseDate(toText, "to", "End date");
        if (from.isAfter(to)) throw AppException.badRequest("Invalid period: the start date must not be after the end date").field("from");
        return switch (type) {
            case "most-borrowed" -> mostBorrowed(from, to);
            case "overdue" -> overdue(from, to);
            case "fines" -> fines(from, to);
            default -> members(from, to);
        };
    }

    private Report mostBorrowed(LocalDate from, LocalDate to) {
        Report r = new Report("Most borrowed books", List.of("#", "Title", "ISBN", "Loans"));
        int rank = 1;
        long total = 0;
        for (Object[] row : store.mostBorrowed(from, to, 20)) {
            r.row(rank++, row[1], row[2], row[3]);
            r.chart((String) row[1], (Number) row[3]);
            total += (Long) row[3];
        }
        return r.summary("Loans in the top list", total);
    }

    private Report overdue(LocalDate from, LocalDate to) {
        Report r = new Report("Overdue loans (due date in the period)",
                List.of("Member", "Card", "Title", "Barcode", "Due date", "Returned", "Days overdue"));
        LocalDate today = LocalDate.now(clock);
        for (Loan l : store.overdueLoans(from, to, today)) {
            User u = store.findUser(l.getMemberId());
            BookCopy c = store.findCopy(l.getCopyId());
            Book b = c == null ? null : store.findBook(c.getBookId());
            LocalDate until = l.getReturnDate() != null ? l.getReturnDate() : today;
            r.row(u == null ? "" : u.getFullName(), u instanceof Member m ? m.getCardNumber() : "", b == null ? "" : b.getTitle(),
                    c == null ? "" : c.getBarcode(), l.getDueDate(), l.getReturnDate() == null ? "not yet" : l.getReturnDate(), l.overdueDays(until));
        }
        return r.summary("Overdue loans", r.getRows().size());
    }

    private Report fines(LocalDate from, LocalDate to) {
        Report r = new Report("Fines collected", List.of("Paid at", "Member", "Reason", "Amount (VND)"));
        double total = 0, overdue = 0, damaged = 0, lost = 0;
        for (Fine f : store.paidFinesBetween(from.atStartOfDay(), LocalDateTime.of(to, LocalTime.MAX))) {
            Loan l = store.findLoan(f.getLoanId());
            User u = l == null ? null : store.findUser(l.getMemberId());
            r.row(f.getPaidAt().toString().replace('T', ' '), u == null ? "" : u.getFullName(), f.getReason(), f.getAmount());
            total += f.getAmount();
            switch (f.getReason()) {
                case OVERDUE -> overdue += f.getAmount();
                case DAMAGED -> damaged += f.getAmount();
                default -> lost += f.getAmount();
            }
        }
        if (!r.isEmpty()) r.chart("OVERDUE", overdue).chart("DAMAGED", damaged).chart("LOST", lost);
        return r.summary("Total collected (VND)", total);
    }

    private Report members(LocalDate from, LocalDate to) {
        Map<String, Long> s = store.memberStats(from, to);
        Report r = new Report("Member statistics", List.of("Metric", "Value"));
        r.row("Members (all)", s.get("totalMembers")).row("Members with ACTIVE status", s.get("activeMembers"))
                .row("New registrations in the period", s.get("newMembers")).row("Members who borrowed in the period", s.get("borrowers"))
                .row("Loans in the period", s.get("loans"));
        r.chart("New", s.get("newMembers")).chart("Borrowers", s.get("borrowers")).chart("Loans", s.get("loans"));
        return r;
    }
}
