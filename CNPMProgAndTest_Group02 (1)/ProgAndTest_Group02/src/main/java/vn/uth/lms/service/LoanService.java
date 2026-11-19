package vn.uth.lms.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import vn.uth.lms.model.*;
import vn.uth.lms.persistence.LibraryStore;
import vn.uth.lms.util.AppException;
import vn.uth.lms.util.TextUtil;

/** Use cases: Lend book, Return book, Renew loan, View loan history. */
public class LoanService {

    /** Result of "Return book": the closed loan and the fines (if any) that must be collected. */
    public static class ReturnResult {
        public final Loan loan;
        public final BookCopy copy;
        public final Member member;
        public final List<Fine> fines;

        ReturnResult(Loan loan, BookCopy copy, Member member, List<Fine> fines) {
            this.loan = loan;
            this.copy = copy;
            this.member = member;
            this.fines = fines;
        }
    }

    private final LibraryStore store;
    private final Clock clock;
    private final ReservationService reservations;
    private final FineService fines;

    public LoanService(LibraryStore store, Clock clock, ReservationService reservations, FineService fines) {
        this.store = store;
        this.clock = clock;
        this.reservations = reservations;
        this.fines = fines;
    }

    private LocalDate today() { return LocalDate.now(clock); }

    // ------------------------------------------------------- eligibility
    /** Returns the reason why the member cannot borrow {@code additional} more books, or null when allowed. */
    String eligibilityProblem(Member m, int additional) {
        if (m.getStatus() != AccountStatus.ACTIVE) return "The member account is not ACTIVE (status: " + m.getStatus() + ")";
        if (!m.isCardValid(today())) return "The library card has expired or has not been issued";
        List<Loan> active = store.activeLoansOfMember(m.getUserId());
        if (active.stream().anyMatch(l -> l.isOverdue(today()))) return "The member has an overdue loan";
        double unpaid = fines.unpaidTotal(m.getUserId());
        if (unpaid > 0) return "The member has an unpaid fine of " + String.format("%,.0f", unpaid) + " VND";
        if (active.size() + additional > Rules.MAX_ACTIVE_LOANS)
            return "A member can borrow at most " + Rules.MAX_ACTIVE_LOANS + " books at the same time (currently borrowing " + active.size() + ")";
        return null;
    }

    private Member memberByCard(String card) {
        if (TextUtil.isBlank(card)) throw AppException.badRequest("Please enter the member card number").field("card");
        Member m = store.findMemberByCard(card.trim());
        if (m == null || m.isDeleted()) throw AppException.notFound("No member found with this card number").field("card");
        return m;
    }

    /** The "Check" button of the lending screen. */
    public Map<String, Object> checkMember(String card) {
        Member m = memberByCard(card);
        Map<String, Object> r = new LinkedHashMap<>();
        String problem = eligibilityProblem(m, 1);
        r.put("memberId", m.getUserId());
        r.put("fullName", m.getFullName());
        r.put("cardNumber", m.getCardNumber());
        r.put("status", m.getStatus());
        r.put("cardExpiry", m.getCardExpiry());
        r.put("activeLoans", store.activeLoansOfMember(m.getUserId()).size());
        r.put("maxLoans", Rules.MAX_ACTIVE_LOANS);
        r.put("unpaidFines", fines.unpaidTotal(m.getUserId()));
        r.put("eligible", problem == null);
        r.put("reason", problem);
        return r;
    }

    // -------------------------------------------------------------- lend
    public List<Loan> lend(Librarian librarian, String card, List<String> barcodes) {
        if (barcodes == null || barcodes.isEmpty()) throw AppException.badRequest("Please add at least one book copy").field("barcode");
        return store.tx(() -> {
            reservations.refresh();
            Member m = memberByCard(card);
            String problem = eligibilityProblem(m, barcodes.size());
            if (problem != null) throw AppException.conflict(problem);

            // 1) validate everything first, so nothing is changed when one copy is not allowed
            Map<BookCopy, Reservation> plan = new LinkedHashMap<>();
            Set<Integer> usedReservations = new HashSet<>();
            Set<String> seen = new HashSet<>();
            for (String raw : barcodes) {
                String code = raw == null ? "" : raw.trim();
                if (!seen.add(code.toUpperCase())) throw AppException.badRequest("Barcode " + code + " is listed twice").field("barcode");
                BookCopy c = store.findCopyByBarcode(code);
                if (c == null) throw AppException.badRequest("Invalid barcode: " + code).field("barcode");
                Reservation ready = null;
                if (c.getStatus() == CopyStatus.RESERVED) {
                    ready = store.activeReservationsOfBook(c.getBookId()).stream()
                            .filter(r -> r.getStatus() == ReservationStatus.READY && r.getMemberId() == m.getUserId()
                                    && !usedReservations.contains(r.getReservationId())).findFirst().orElse(null);
                    if (ready == null) throw AppException.conflict("Copy " + c.getBarcode() + " is not available (it is reserved for another member)");
                    usedReservations.add(ready.getReservationId());
                } else if (c.getStatus() != CopyStatus.AVAILABLE) {
                    throw AppException.conflict("Copy " + c.getBarcode() + " is not available (status: " + c.getStatus() + ")");
                }
                plan.put(c, ready);
            }

            // 2) apply
            List<Loan> created = new ArrayList<>();
            for (Map.Entry<BookCopy, Reservation> e : plan.entrySet()) {
                BookCopy c = e.getKey();
                Reservation res = e.getValue();
                boolean copyWasReserved = res != null;
                Loan loan = new Loan();
                loan.setMemberId(m.getUserId());
                loan.setCopyId(c.getCopyId());
                loan.setLibrarianId(librarian.getUserId());
                loan.setLoanDate(today());
                loan.setDueDate(today().plusDays(Rules.LOAN_DAYS));
                loan.setRenewCount(0);
                loan.setStatus(LoanStatus.BORROWED);
                created.add(store.saveLoan(loan));
                c.updateStatus(CopyStatus.BORROWED);
                store.saveCopy(c);
                if (res == null) {
                    res = store.activeReservationsOfMember(m.getUserId()).stream()
                            .filter(r -> r.getBookId() == c.getBookId() && !usedReservations.contains(r.getReservationId())).findFirst().orElse(null);
                    if (res != null) usedReservations.add(res.getReservationId());
                }
                if (res != null) {
                    boolean wasReady = res.getStatus() == ReservationStatus.READY;
                    res.setStatus(ReservationStatus.FULFILLED);
                    store.saveReservation(res);
                    if (wasReady && !copyWasReserved) reservations.releaseOneReservedCopy(c.getBookId());
                }
            }
            return created;
        });
    }

    // ------------------------------------------------------------- renew
    public Loan renew(int memberId, int loanId) {
        return store.tx(() -> {
            Loan loan = store.findLoan(loanId);
            if (loan == null || loan.getMemberId() != memberId) throw AppException.notFound("Loan not found");
            if (loan.getStatus() != LoanStatus.BORROWED) throw AppException.conflict("Only borrowed books can be renewed");
            if (loan.isOverdue(today())) throw AppException.conflict("This loan is overdue and cannot be renewed");
            if (loan.getRenewCount() >= Rules.MAX_RENEWALS)
                throw AppException.conflict("This loan has already been renewed " + Rules.MAX_RENEWALS + " times (the maximum)");
            BookCopy c = store.findCopy(loan.getCopyId());
            boolean othersWaiting = c != null && store.activeReservationsOfBook(c.getBookId()).stream().anyMatch(r -> r.getMemberId() != memberId);
            if (othersWaiting) throw AppException.conflict("Another member has reserved this book, so the loan cannot be renewed");
            loan.renew();
            return store.saveLoan(loan);
        });
    }

    // ------------------------------------------------------------ return
    public ReturnResult returnBook(Librarian librarian, String barcode, String condition, Double price) {
        String cond = TextUtil.isBlank(condition) ? "OK" : condition.trim().toUpperCase();
        if (!List.of("OK", "DAMAGED", "LOST").contains(cond)) throw AppException.badRequest("Condition must be OK, DAMAGED or LOST");
        if (!cond.equals("OK") && (price == null || price <= 0))
            throw AppException.badRequest("The replacement price is required for a damaged or lost book").field("replacementPrice");
        if (TextUtil.isBlank(barcode)) throw AppException.badRequest("Please enter the barcode of the copy").field("barcode");
        return store.tx(() -> {
            BookCopy copy = store.findCopyByBarcode(barcode.trim());
            if (copy == null) throw AppException.badRequest("Invalid barcode: " + barcode.trim()).field("barcode");
            Loan loan = store.findActiveLoanByCopy(copy.getCopyId());
            if (loan == null) throw AppException.conflict("This copy has no active loan").field("barcode");
            loan.setReturnDate(today());
            loan.setStatus(cond.equals("LOST") ? LoanStatus.LOST : LoanStatus.RETURNED);
            store.saveLoan(loan);
            List<Fine> created = new ArrayList<>();
            switch (cond) {
                case "DAMAGED" -> {
                    copy.updateStatus(CopyStatus.DAMAGED);
                    store.saveCopy(copy);
                    Fine overdue = fines.createOverdueFine(loan, today());
                    if (overdue != null) created.add(overdue);
                    created.add(fines.createReplacementFine(loan, FineReason.DAMAGED, price));
                }
                case "LOST" -> {
                    copy.updateStatus(CopyStatus.LOST);
                    store.saveCopy(copy);
                    created.add(fines.createReplacementFine(loan, FineReason.LOST, price));
                }
                default -> {
                    reservations.onCopyFreed(copy);
                    Fine overdue = fines.createOverdueFine(loan, today());
                    if (overdue != null) created.add(overdue);
                }
            }
            return new ReturnResult(loan, copy, (Member) store.findUser(loan.getMemberId()), created);
        });
    }

    // ----------------------------------------------------------- history
    public static String displayStatus(Loan l, LocalDate today) {
        return l.isOverdue(today) ? "OVERDUE" : l.getStatus().name();
    }

    public List<Map<String, Object>> loansOfMember(int memberId, String statusFilter) {
        String filter = TextUtil.isBlank(statusFilter) ? "ALL" : statusFilter.trim().toUpperCase();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Loan l : store.loansOfMember(memberId)) {
            String status = displayStatus(l, today());
            if (!filter.equals("ALL") && !filter.equals(status)) continue;
            BookCopy c = store.findCopy(l.getCopyId());
            Book b = c == null ? null : store.findBook(c.getBookId());
            List<Fine> fs = store.finesOfLoan(l.getLoanId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("loanId", l.getLoanId());
            m.put("bookId", b == null ? null : b.getBookId());
            m.put("title", b == null ? "" : b.getTitle());
            m.put("barcode", c == null ? "" : c.getBarcode());
            m.put("loanDate", l.getLoanDate());
            m.put("dueDate", l.getDueDate());
            m.put("returnDate", l.getReturnDate());
            m.put("status", status);
            m.put("renewCount", l.getRenewCount());
            m.put("maxRenewals", Rules.MAX_RENEWALS);
            m.put("canRenew", l.canRenew(today()));
            m.put("fineAmount", fs.stream().mapToDouble(Fine::getAmount).sum());
            m.put("fineStatus", fs.isEmpty() ? null : (fs.stream().anyMatch(f -> f.getStatus() == FineStatus.UNPAID) ? "UNPAID" : "PAID"));
            rows.add(m);
        }
        return rows.stream().collect(Collectors.toList());
    }
}
