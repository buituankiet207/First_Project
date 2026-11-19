package vn.uth.lms;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.uth.lms.model.*;
import vn.uth.lms.service.LoanService;
import vn.uth.lms.util.AppException;

/** Use cases: Lend book, Return book, Renew loan, View loan history, Collect fine. */
class LoanServiceTest {
    Fixture f;
    Librarian lib;

    @BeforeEach
    void setUp() {
        f = new Fixture();
        lib = f.librarian();
    }

    private AppException fails(Runnable r) { return assertThrows(AppException.class, r::run); }

    private Loan activeLoan(String barcode) { return f.store.findActiveLoanByCopy(f.copy(barcode).getCopyId()); }

    // -------------------------------------------------------------------- lend
    @Test
    void lendCreatesALoanDueInFourteenDays() {
        List<Loan> loans = f.app.loans.lend(lib, "LIB-000124", List.of("BC-000005"));
        Loan l = loans.get(0);
        assertEquals(f.today(), l.getLoanDate());
        assertEquals(f.today().plusDays(14), l.getDueDate());
        assertEquals(LoanStatus.BORROWED, l.getStatus());
        assertEquals(lib.getUserId(), l.getLibrarianId());
        assertEquals(CopyStatus.BORROWED, f.copy("BC-000005").getStatus());
    }

    @Test
    void checkMemberReportsLoansAndEligibility() {
        Map<String, Object> m = f.app.loans.checkMember("LIB-000123");
        assertEquals(2, m.get("activeLoans"));
        assertEquals(5, m.get("maxLoans"));
        assertEquals(true, m.get("eligible"));
        assertEquals(false, f.app.loans.checkMember("LIB-000125").get("eligible"));
    }

    @Test
    void memberWithFourLoansMayBorrowTheFifthButNotASixth() {
        f.app.loans.lend(lib, "LIB-000123", List.of("BC-000005", "BC-000006"));
        assertEquals(4, f.store.activeLoansOfMember(f.member("member01").getUserId()).size());
        f.app.loans.lend(lib, "LIB-000123", List.of("BC-000002"));
        AppException e = fails(() -> f.app.loans.lend(lib, "LIB-000123", List.of("BC-000003")));
        assertTrue(e.getMessage().contains("at most 5"));
    }

    @Test
    void requestingMoreThanTheLimitInOneGoIsRefused() {
        assertEquals(409, fails(() -> f.app.loans.lend(lib, "LIB-000123", List.of("BC-000005", "BC-000006", "BC-000002", "BC-000003"))).getStatus());
    }

    @Test
    void lockedMemberCannotBorrow() {
        assertTrue(fails(() -> f.app.loans.lend(lib, "LIB-000122", List.of("BC-000005"))).getMessage().contains("not ACTIVE"));
    }

    @Test
    void expiredCardCannotBorrow() {
        Member m = f.member("member04");
        m.setCardExpiry(f.today().minusDays(1));
        f.store.saveUser(m);
        assertTrue(fails(() -> f.app.loans.lend(lib, "LIB-000124", List.of("BC-000005"))).getMessage().contains("expired"));
    }

    @Test
    void cardExpiringTodayIsStillValid() {
        Member m = f.member("member04");
        m.setCardExpiry(f.today());
        f.store.saveUser(m);
        assertEquals(1, f.app.loans.lend(lib, "LIB-000124", List.of("BC-000005")).size());
    }

    @Test
    void memberWithAnOverdueLoanCannotBorrow() {
        assertTrue(fails(() -> f.app.loans.lend(lib, "LIB-000125", List.of("BC-000005"))).getMessage().contains("overdue"));
    }

    @Test
    void memberWithAnUnpaidFineCannotBorrow() {
        assertTrue(fails(() -> f.app.loans.lend(lib, "LIB-000126", List.of("BC-000005"))).getMessage().contains("unpaid fine"));
    }

    @Test
    void paymentOfTheFineAllowsBorrowingAgain() {
        Fine fine = f.store.finesOfMember(f.member("member06").getUserId()).get(0);
        f.app.fines.collectFine(lib, fine.getFineId());
        assertEquals(1, f.app.loans.lend(lib, "LIB-000126", List.of("BC-000005")).size());
    }

    @Test
    void borrowedOrUnknownCopiesAreRefused() {
        assertTrue(fails(() -> f.app.loans.lend(lib, "LIB-000124", List.of("BC-000004"))).getMessage().contains("not available"));
        assertTrue(fails(() -> f.app.loans.lend(lib, "LIB-000124", List.of("XX-000000"))).getMessage().contains("Invalid barcode"));
        assertEquals(404, fails(() -> f.app.loans.lend(lib, "LIB-999999", List.of("BC-000005"))).getStatus());
        assertEquals(400, fails(() -> f.app.loans.lend(lib, "LIB-000124", List.of())).getStatus());
    }

    @Test
    void sameBarcodeTwiceInTheListIsRefused() {
        assertEquals(400, fails(() -> f.app.loans.lend(lib, "LIB-000124", List.of("BC-000005", "bc-000005"))).getStatus());
    }

    @Test
    void nothingIsChangedWhenOneCopyOfTheListIsNotAllowed() {
        fails(() -> f.app.loans.lend(lib, "LIB-000124", List.of("BC-000005", "XX-000000")));
        assertEquals(CopyStatus.AVAILABLE, f.copy("BC-000005").getStatus());
        assertEquals(0, f.store.activeLoansOfMember(f.member("member04").getUserId()).size());
    }

    // ------------------------------------------------------------------- renew
    @Test
    void renewMovesTheDueDateByFourteenDays() {
        Loan l = activeLoan("BC-000004");
        var due = l.getDueDate();
        f.app.loans.renew(l.getMemberId(), l.getLoanId());
        assertEquals(due.plusDays(14), f.store.findLoan(l.getLoanId()).getDueDate());
        assertEquals(1, f.store.findLoan(l.getLoanId()).getRenewCount());
    }

    @Test
    void aLoanCanBeRenewedTwiceButNotThreeTimes() {
        Loan l = activeLoan("BC-000004");
        f.app.loans.renew(l.getMemberId(), l.getLoanId());
        f.app.loans.renew(l.getMemberId(), l.getLoanId());
        AppException e = fails(() -> f.app.loans.renew(l.getMemberId(), l.getLoanId()));
        assertTrue(e.getMessage().contains("2 times"));
    }

    @Test
    void overdueLoanCannotBeRenewed() {
        Loan l = activeLoan("BC-000014");
        assertTrue(fails(() -> f.app.loans.renew(l.getMemberId(), l.getLoanId())).getMessage().contains("overdue"));
    }

    @Test
    void loanCannotBeRenewedWhenAnotherMemberIsWaiting() {
        f.app.reservations.reserve(f.member("member04").getUserId(), 6);
        Loan l = activeLoan("BC-000018");
        assertTrue(fails(() -> f.app.loans.renew(l.getMemberId(), l.getLoanId())).getMessage().contains("reserved"));
    }

    @Test
    void memberCannotRenewSomeoneElsesLoan() {
        Loan l = activeLoan("BC-000004");
        assertEquals(404, fails(() -> f.app.loans.renew(f.member("member04").getUserId(), l.getLoanId())).getStatus());
    }

    @Test
    void returnedLoanCannotBeRenewed() {
        Loan l = activeLoan("BC-000004");
        f.app.loans.returnBook(lib, "BC-000004", "OK", null);
        assertEquals(409, fails(() -> f.app.loans.renew(l.getMemberId(), l.getLoanId())).getStatus());
    }

    // ------------------------------------------------------------------ return
    @Test
    void returnOnTimeClosesTheLoanWithoutFine() {
        Loan l = activeLoan("BC-000004");
        LoanService.ReturnResult r = f.app.loans.returnBook(lib, "BC-000004", "OK", null);
        assertEquals(LoanStatus.RETURNED, f.store.findLoan(l.getLoanId()).getStatus());
        assertEquals(f.today(), r.loan.getReturnDate());
        assertEquals(CopyStatus.AVAILABLE, f.copy("BC-000004").getStatus());
        assertTrue(r.fines.isEmpty());
    }

    @Test
    void returnOnTheDueDateIsNotOverdue() {
        f.advanceDays(10);
        assertTrue(f.app.loans.returnBook(lib, "BC-000004", "OK", null).fines.isEmpty());
    }

    @Test
    void overdueReturnCreatesAnUnpaidFineOfOneThousandPerDay() {
        LoanService.ReturnResult r = f.app.loans.returnBook(lib, "BC-000014", "OK", null);
        assertEquals(1, r.fines.size());
        Fine fine = r.fines.get(0);
        assertEquals(6000.0, fine.getAmount());
        assertEquals(FineStatus.UNPAID, fine.getStatus());
        assertEquals(FineReason.OVERDUE, fine.getReason());
    }

    @Test
    void oneDayOverdueIsOneThousand() {
        f.advanceDays(11);
        assertEquals(1000.0, f.app.loans.returnBook(lib, "BC-000004", "OK", null).fines.get(0).getAmount());
    }

    @Test
    void damagedBookGetsTheReplacementPriceAsFine() {
        LoanService.ReturnResult r = f.app.loans.returnBook(lib, "BC-000004", "DAMAGED", 80000.0);
        assertEquals(CopyStatus.DAMAGED, f.copy("BC-000004").getStatus());
        assertEquals(1, r.fines.size());
        assertEquals(80000.0, r.fines.get(0).getAmount());
        assertEquals(FineReason.DAMAGED, r.fines.get(0).getReason());
    }

    @Test
    void damagedAndOverdueGivesTwoFines() {
        assertEquals(2, f.app.loans.returnBook(lib, "BC-000014", "DAMAGED", 50000.0).fines.size());
    }

    @Test
    void lostBookClosesTheLoanAsLost() {
        LoanService.ReturnResult r = f.app.loans.returnBook(lib, "BC-000012", "LOST", 250000.0);
        assertEquals(LoanStatus.LOST, r.loan.getStatus());
        assertEquals(CopyStatus.LOST, f.copy("BC-000012").getStatus());
        assertEquals(250000.0, r.fines.get(0).getAmount());
    }

    @Test
    void replacementPriceIsRequiredForDamagedOrLostBooks() {
        assertEquals("replacementPrice", fails(() -> f.app.loans.returnBook(lib, "BC-000004", "DAMAGED", null)).getField());
        assertEquals("replacementPrice", fails(() -> f.app.loans.returnBook(lib, "BC-000004", "LOST", 0.0)).getField());
        assertEquals(CopyStatus.BORROWED, f.copy("BC-000004").getStatus());
    }

    @Test
    void copyWithoutActiveLoanOrWithBadBarcodeIsRefused() {
        assertEquals(409, fails(() -> f.app.loans.returnBook(lib, "BC-000005", "OK", null)).getStatus());
        assertEquals(400, fails(() -> f.app.loans.returnBook(lib, "XX-000000", "OK", null)).getStatus());
    }

    // ------------------------------------------------------------- collect fine
    @Test
    void collectingAFineMarksItPaid() {
        Fine fine = f.app.loans.returnBook(lib, "BC-000014", "OK", null).fines.get(0);
        Fine paid = f.app.fines.collectFine(lib, fine.getFineId());
        assertEquals(FineStatus.PAID, paid.getStatus());
        assertNotNull(paid.getPaidAt());
        assertEquals(409, fails(() -> f.app.fines.collectFine(lib, fine.getFineId())).getStatus());
    }

    @Test
    void fineRateIsReadFromTheConfiguration() {
        f.app.fines.setRatePerDay(2000);
        assertEquals(12000.0, f.app.loans.returnBook(lib, "BC-000014", "OK", null).fines.get(0).getAmount());
        assertEquals("finePerDay", fails(() -> f.app.fines.setRatePerDay(0)).getField());
    }

    @Test
    void unpaidTotalSumsTheOpenFines() {
        assertEquals(14000.0, f.app.fines.unpaidTotal(f.member("member06").getUserId()));
        f.app.loans.returnBook(lib, "BC-000014", "OK", null);
        assertEquals(6000.0, f.app.fines.unpaidTotal(f.member("member05").getUserId()));
    }

    // ------------------------------------------------------------ loan history
    @Test
    void historyShowsEveryLoanNewestFirstWithItsStatus() {
        List<Map<String, Object>> rows = f.app.loans.loansOfMember(f.member("member01").getUserId(), null);
        assertEquals(4, rows.size());
        assertEquals("Introduction to Algorithms", rows.get(0).get("title"));
        assertEquals(2, f.app.loans.loansOfMember(f.member("member01").getUserId(), "RETURNED").size());
        assertEquals(2, f.app.loans.loansOfMember(f.member("member01").getUserId(), "borrowed").size());
    }

    @Test
    void overdueLoansAreMarkedAndCanBeFiltered() {
        List<Map<String, Object>> rows = f.app.loans.loansOfMember(f.member("member05").getUserId(), "OVERDUE");
        assertEquals(1, rows.size());
        assertEquals("OVERDUE", rows.get(0).get("status"));
        assertEquals(false, rows.get(0).get("canRenew"));
    }

    @Test
    void historyShowsTheFineOfALoan() {
        List<Map<String, Object>> rows = f.app.loans.loansOfMember(f.member("member06").getUserId(), "RETURNED");
        assertEquals(14000.0, rows.get(0).get("fineAmount"));
        assertEquals("UNPAID", rows.get(0).get("fineStatus"));
    }

    @Test
    void memberWithoutLoansHasAnEmptyHistory() {
        assertTrue(f.app.loans.loansOfMember(f.member("member04").getUserId(), null).isEmpty());
    }
}
