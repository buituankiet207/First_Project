package vn.uth.lms;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.uth.lms.model.*;
import vn.uth.lms.util.AppException;

/** Use case: Reserve book (queue, limit of 3, holding period of 3 days). */
class ReservationServiceTest {
    Fixture f;
    int m1, m4;

    @BeforeEach
    void setUp() {
        f = new Fixture();
        m1 = f.member("member01").getUserId();
        m4 = f.member("member04").getUserId();
    }

    private AppException fails(Runnable r) { return assertThrows(AppException.class, r::run); }

    @Test
    void reserveCreatesAWaitingReservationAtPositionOne() {
        Reservation r = f.app.reservations.reserve(m1, 6);
        assertEquals(ReservationStatus.WAITING, r.getStatus());
        assertEquals(1, f.app.reservations.queuePosition(r));
    }

    @Test
    void bookWithAnAvailableCopyCannotBeReserved() {
        AppException e = fails(() -> f.app.reservations.reserve(m1, 2));
        assertTrue(e.getMessage().contains("available"));
    }

    @Test
    void sameBookCannotBeReservedTwice() {
        f.app.reservations.reserve(m1, 6);
        assertEquals(409, fails(() -> f.app.reservations.reserve(m1, 6)).getStatus());
    }

    @Test
    void queueIsFirstComeFirstServed() {
        Reservation a = f.app.reservations.reserve(m1, 6);
        f.clock.advance(Duration.ofSeconds(5));
        Reservation b = f.app.reservations.reserve(m4, 6);
        assertEquals(1, f.app.reservations.queuePosition(a));
        assertEquals(2, f.app.reservations.queuePosition(b));
    }

    @Test
    void thirdReservationIsAllowedButTheFourthIsRefused() {
        f.exhaust(7);
        f.exhaust(8);
        f.exhaust(9);
        f.app.reservations.reserve(m4, 6);
        f.app.reservations.reserve(m4, 7);
        f.app.reservations.reserve(m4, 8);
        AppException e = fails(() -> f.app.reservations.reserve(m4, 9));
        assertTrue(e.getMessage().contains("3 active reservations"));
    }

    @Test
    void onlyActiveMembersCanReserve() {
        AppException e = fails(() -> f.app.reservations.reserve(f.member("member03").getUserId(), 6));
        assertEquals(403, e.getStatus());
        assertTrue(e.getMessage().contains("ACTIVE"));
    }

    @Test
    void returnedCopyIsHeldForTheFirstMemberInTheQueue() {
        f.app.reservations.reserve(m4, 6);
        f.app.loans.returnBook(f.librarian(), "BC-000018", "OK", null);
        BookCopy copy = f.copy("BC-000018");
        assertEquals(CopyStatus.RESERVED, copy.getStatus());
        Reservation r = f.store.activeReservationsOfMember(m4).get(0);
        assertEquals(ReservationStatus.READY, r.getStatus());
        assertEquals(f.clock.instant(), r.getExpiresAt().minusDays(3).atZone(f.clock.getZone()).toInstant());
        assertEquals(0, f.app.catalog.getBook(6).getAvailableCopies());
    }

    @Test
    void heldCopyCanOnlyBeLentToTheMemberWhoReservedIt() {
        f.app.reservations.reserve(m4, 6);
        f.app.loans.returnBook(f.librarian(), "BC-000018", "OK", null);
        AppException e = fails(() -> f.app.loans.lend(f.librarian(), "LIB-000123", List.of("BC-000018")));
        assertTrue(e.getMessage().contains("reserved for another member"));
        List<Loan> loans = f.app.loans.lend(f.librarian(), "LIB-000124", List.of("BC-000018"));
        assertEquals(1, loans.size());
        assertEquals(ReservationStatus.FULFILLED, f.store.reservationsOfMember(m4).get(0).getStatus());
    }

    @Test
    void reservationExpiresAfterThreeDaysAndPassesToTheNextMember() {
        f.app.reservations.reserve(m4, 6);
        f.clock.advance(Duration.ofSeconds(5));
        f.app.reservations.reserve(m1, 6);
        f.app.loans.returnBook(f.librarian(), "BC-000018", "OK", null);
        f.advanceDays(3);
        f.advanceMinutes(1);
        f.app.reservations.refresh();
        assertEquals(ReservationStatus.EXPIRED, f.store.reservationsOfMember(m4).get(0).getStatus());
        assertEquals(ReservationStatus.READY, f.store.reservationsOfMember(m1).get(0).getStatus());
        assertEquals(CopyStatus.RESERVED, f.copy("BC-000018").getStatus());
    }

    @Test
    void expiredReservationWithNoOneWaitingFreesTheCopy() {
        f.app.reservations.reserve(m4, 6);
        f.app.loans.returnBook(f.librarian(), "BC-000018", "OK", null);
        f.advanceDays(4);
        f.app.reservations.refresh();
        assertEquals(CopyStatus.AVAILABLE, f.copy("BC-000018").getStatus());
        assertEquals(1, f.app.catalog.getBook(6).getAvailableCopies());
    }

    @Test
    void cancellingAReadyReservationReleasesTheCopy() {
        Reservation r = f.app.reservations.reserve(m4, 6);
        f.app.loans.returnBook(f.librarian(), "BC-000018", "OK", null);
        f.app.reservations.cancel(m4, r.getReservationId());
        assertEquals(CopyStatus.AVAILABLE, f.copy("BC-000018").getStatus());
        assertEquals(409, fails(() -> f.app.reservations.cancel(m4, r.getReservationId())).getStatus());
    }

    @Test
    void memberCannotCancelSomeoneElsesReservation() {
        Reservation r = f.app.reservations.reserve(m4, 6);
        assertEquals(404, fails(() -> f.app.reservations.cancel(m1, r.getReservationId())).getStatus());
    }

    @Test
    void newCopyOfAReservedBookGoesToTheQueue() {
        Reservation r = f.app.reservations.reserve(m4, 6);
        f.app.catalog.addCopy(6, "Shelf C1");
        assertEquals(ReservationStatus.READY, f.store.findReservation(r.getReservationId()).getStatus());
    }
}
