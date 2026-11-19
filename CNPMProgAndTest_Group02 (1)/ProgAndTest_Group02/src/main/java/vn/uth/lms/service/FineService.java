package vn.uth.lms.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import vn.uth.lms.model.*;
import vn.uth.lms.persistence.LibraryStore;
import vn.uth.lms.util.AppException;

/** Use case: Collect fine (and creation of the fines produced when a book is returned). */
public class FineService {
    public static final String RATE_KEY = "fine_per_day";

    private final LibraryStore store;
    private final Clock clock;

    public FineService(LibraryStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public long ratePerDay() {
        try { return Long.parseLong(store.getConfig(RATE_KEY, String.valueOf(Rules.DEFAULT_FINE_PER_DAY))); }
        catch (NumberFormatException e) { return Rules.DEFAULT_FINE_PER_DAY; }
    }

    public void setRatePerDay(long rate) {
        if (rate <= 0) throw AppException.badRequest("The fine rate must be greater than 0").field("finePerDay");
        store.setConfig(RATE_KEY, String.valueOf(rate));
    }

    /** Overdue fine = overdue days x rate per day. Returns null when the loan is not overdue. */
    Fine createOverdueFine(Loan loan, LocalDate returnDate) {
        long days = loan.overdueDays(returnDate);
        if (days <= 0) return null;
        Fine f = new Fine();
        f.setLoanId(loan.getLoanId());
        f.calculateAmount(days, ratePerDay());
        f.setReason(FineReason.OVERDUE);
        f.setStatus(FineStatus.UNPAID);
        f.setCreatedAt(LocalDateTime.now(clock));
        return store.saveFine(f);
    }

    /** Fine for a damaged or lost copy: the replacement price decided by the librarian. */
    Fine createReplacementFine(Loan loan, FineReason reason, double price) {
        Fine f = new Fine();
        f.setLoanId(loan.getLoanId());
        f.setAmount(price);
        f.setReason(reason);
        f.setStatus(FineStatus.UNPAID);
        f.setCreatedAt(LocalDateTime.now(clock));
        return store.saveFine(f);
    }

    public Fine collectFine(Librarian librarian, int fineId) {
        return store.tx(() -> {
            Fine f = store.findFine(fineId);
            if (f == null) throw AppException.notFound("Fine not found");
            if (f.getStatus() == FineStatus.PAID) throw AppException.conflict("This fine has already been paid");
            f.markPaid(LocalDateTime.now(clock));
            return store.saveFine(f);
        });
    }

    public List<Fine> unpaidFinesOfMember(int memberId) {
        return store.finesOfMember(memberId).stream().filter(f -> f.getStatus() == FineStatus.UNPAID).collect(Collectors.toList());
    }

    public double unpaidTotal(int memberId) {
        return unpaidFinesOfMember(memberId).stream().mapToDouble(Fine::getAmount).sum();
    }
}
