package vn.uth.lms;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import vn.uth.lms.model.*;

/** Behaviour of the entity classes of the class diagram. */
class DomainTest {

    private Loan loan(LocalDate due) {
        Loan l = new Loan();
        l.setLoanDate(due.minusDays(14));
        l.setDueDate(due);
        l.setStatus(LoanStatus.BORROWED);
        return l;
    }

    @Test
    void renewExtendsDueDateByFourteenDaysAndCounts() {
        Loan l = loan(LocalDate.of(2026, 11, 15));
        l.renew();
        assertEquals(LocalDate.of(2026, 11, 29), l.getDueDate());
        assertEquals(1, l.getRenewCount());
    }

    @Test
    void canRenewIsFalseAfterTwoRenewalsOrWhenOverdue() {
        Loan l = loan(LocalDate.of(2026, 11, 20));
        LocalDate today = LocalDate.of(2026, 11, 18);
        assertTrue(l.canRenew(today));
        l.renew();
        l.renew();
        assertFalse(l.canRenew(today));
        assertFalse(loan(LocalDate.of(2026, 11, 10)).canRenew(today));
    }

    @Test
    void overdueStartsTheDayAfterTheDueDate() {
        Loan l = loan(LocalDate.of(2026, 11, 18));
        assertFalse(l.isOverdue(LocalDate.of(2026, 11, 18)));
        assertTrue(l.isOverdue(LocalDate.of(2026, 11, 19)));
        assertEquals(0, l.overdueDays(LocalDate.of(2026, 11, 18)));
        assertEquals(5, l.overdueDays(LocalDate.of(2026, 11, 23)));
    }

    @Test
    void returnedLoanIsNeverOverdue() {
        Loan l = loan(LocalDate.of(2026, 11, 1));
        l.setStatus(LoanStatus.RETURNED);
        assertFalse(l.isOverdue(LocalDate.of(2026, 12, 1)));
    }

    @Test
    void fineIsRatePerDayTimesOverdueDays() {
        Loan l = loan(LocalDate.of(2026, 11, 10));
        assertEquals(5000.0, l.calculateFine(LocalDate.of(2026, 11, 15), 1000));
        Fine f = new Fine();
        assertEquals(6000.0, f.calculateAmount(3, 2000));
        assertEquals(6000.0, f.getAmount());
    }

    @Test
    void markPaidSetsStatusAndTime() {
        Fine f = new Fine();
        f.setStatus(FineStatus.UNPAID);
        LocalDateTime t = LocalDateTime.of(2026, 11, 18, 10, 0);
        f.markPaid(t);
        assertEquals(FineStatus.PAID, f.getStatus());
        assertEquals(t, f.getPaidAt());
    }

    @Test
    void readyReservationExpiresAfterItsHoldingPeriod() {
        Reservation r = new Reservation();
        r.create(LocalDateTime.of(2026, 11, 18, 9, 0));
        assertEquals(ReservationStatus.WAITING, r.getStatus());
        assertFalse(r.isExpired(LocalDateTime.of(2030, 1, 1, 0, 0)));
        r.setStatus(ReservationStatus.READY);
        r.setExpiresAt(LocalDateTime.of(2026, 11, 21, 9, 0));
        assertFalse(r.isExpired(LocalDateTime.of(2026, 11, 21, 9, 0)));
        assertTrue(r.isExpired(LocalDateTime.of(2026, 11, 21, 9, 1)));
        assertTrue(r.isActive());
        r.cancel();
        assertFalse(r.isActive());
    }

    @Test
    void fiveFailedLoginsLockTheAccountForFifteenMinutes() {
        Member m = new Member();
        LocalDateTime now = LocalDateTime.of(2026, 11, 18, 9, 0);
        for (int i = 0; i < 4; i++) m.registerFailedLogin(now);
        assertFalse(m.isTemporarilyLocked(now));
        m.registerFailedLogin(now);
        assertTrue(m.isTemporarilyLocked(now.plusMinutes(14)));
        assertFalse(m.isTemporarilyLocked(now.plusMinutes(15)));
        m.resetFailedLogins();
        assertEquals(0, m.getFailedAttempts());
    }

    @Test
    void roleFollowsTheClassHierarchy() {
        assertEquals(Role.MEMBER, new Member().getRole());
        assertEquals(Role.LIBRARIAN, new Librarian().getRole());
        assertEquals(Role.ADMIN, new Admin().getRole());
        assertTrue(new Admin() instanceof Librarian);
    }

    @Test
    void copyAvailabilityAndCardValidity() {
        BookCopy c = new BookCopy();
        c.setStatus(CopyStatus.AVAILABLE);
        assertTrue(c.isAvailable());
        c.updateStatus(CopyStatus.BORROWED);
        assertFalse(c.isAvailable());
        Member m = new Member();
        assertFalse(m.isCardValid(LocalDate.of(2026, 11, 18)));
        m.setCardNumber("LIB-1");
        m.setCardExpiry(LocalDate.of(2026, 11, 18));
        assertTrue(m.isCardValid(LocalDate.of(2026, 11, 18)));
        assertFalse(m.isCardValid(LocalDate.of(2026, 11, 19)));
    }
}
