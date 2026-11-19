package vn.uth.lms.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import vn.uth.lms.model.*;
import vn.uth.lms.persistence.LibraryStore;
import vn.uth.lms.util.AppException;

/**
 * Use case: Reserve book. A reservation starts as WAITING; when a copy is free for the first member in the
 * queue it becomes READY and the copy is kept (RESERVED) for HOLD_DAYS days, then it EXPIRES.
 */
public class ReservationService {
    private final LibraryStore store;
    private final Clock clock;

    public ReservationService(LibraryStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public Reservation reserve(int memberId, int bookId) {
        return store.tx(() -> {
            refresh();
            User u = store.findUser(memberId);
            if (!(u instanceof Member m)) throw AppException.forbidden("Only members can reserve books");
            if (m.getStatus() != AccountStatus.ACTIVE)
                throw AppException.forbidden("Only ACTIVE members can reserve books (your account is " + m.getStatus()
                        + "). Please ask the librarian to activate your library card");
            Book b = store.findBook(bookId);
            if (b == null || b.isDeleted()) throw AppException.notFound("Book not found");
            if (b.getAvailableCopies() > 0)
                throw AppException.conflict("A copy of this book is available. Please borrow it at the library desk");
            List<Reservation> mine = store.activeReservationsOfMember(memberId);
            if (mine.stream().anyMatch(r -> r.getBookId() == bookId))
                throw AppException.conflict("You have already reserved this book");
            if (mine.size() >= Rules.MAX_ACTIVE_RESERVATIONS)
                throw AppException.conflict("You already have " + Rules.MAX_ACTIVE_RESERVATIONS + " active reservations (the maximum)");
            Reservation r = new Reservation();
            r.setMemberId(memberId);
            r.setBookId(bookId);
            r.create(LocalDateTime.now(clock));
            return store.saveReservation(r);
        });
    }

    public void cancel(int memberId, int reservationId) {
        store.tx(() -> {
            Reservation r = store.findReservation(reservationId);
            if (r == null || r.getMemberId() != memberId) throw AppException.notFound("Reservation not found");
            if (!r.isActive()) throw AppException.conflict("This reservation is no longer active");
            boolean wasReady = r.getStatus() == ReservationStatus.READY;
            r.cancel();
            store.saveReservation(r);
            if (wasReady) releaseOneReservedCopy(r.getBookId());
            return null;
        });
    }

    /** Expires READY reservations whose holding period has passed and passes their copies on. */
    public void refresh() {
        LocalDateTime now = LocalDateTime.now(clock);
        for (Reservation r : store.expiredReady(now)) {
            r.setStatus(ReservationStatus.EXPIRED);
            store.saveReservation(r);
            releaseOneReservedCopy(r.getBookId());
        }
    }

    /** A copy became free: give it to the first WAITING member (READY + hold) or make it AVAILABLE. */
    public void onCopyFreed(BookCopy copy) {
        Reservation next = store.activeReservationsOfBook(copy.getBookId()).stream()
                .filter(r -> r.getStatus() == ReservationStatus.WAITING).findFirst().orElse(null);
        if (next != null) {
            next.setStatus(ReservationStatus.READY);
            next.setExpiresAt(LocalDateTime.now(clock).plusDays(Rules.HOLD_DAYS));
            store.saveReservation(next);
            copy.updateStatus(CopyStatus.RESERVED);
        } else {
            copy.updateStatus(CopyStatus.AVAILABLE);
        }
        store.saveCopy(copy);
    }

    void releaseOneReservedCopy(int bookId) {
        store.copiesOfBook(bookId).stream().filter(c -> c.getStatus() == CopyStatus.RESERVED).findFirst().ifPresent(this::onCopyFreed);
    }

    /** 1-based position of an active reservation in the queue of its book. */
    public int queuePosition(Reservation r) {
        List<Reservation> q = store.activeReservationsOfBook(r.getBookId());
        for (int i = 0; i < q.size(); i++) if (q.get(i).getReservationId() == r.getReservationId()) return i + 1;
        return 0;
    }

    public List<Reservation> mine(int memberId) {
        refresh();
        return store.reservationsOfMember(memberId);
    }
}
