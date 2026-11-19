package vn.uth.lms.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import vn.uth.lms.model.*;
import vn.uth.lms.persistence.LibraryStore;
import vn.uth.lms.util.AppException;
import vn.uth.lms.util.TextUtil;

/** Use case: Manage members (search, activate library card, update, lock / unlock, delete). */
public class MemberService {
    private final LibraryStore store;
    private final Clock clock;
    private final AuthService auth;
    private final ReservationService reservations;
    private final FineService fines;
    private final SessionManager sessions;

    public MemberService(LibraryStore store, Clock clock, AuthService auth, ReservationService reservations,
                         FineService fines, SessionManager sessions) {
        this.store = store;
        this.clock = clock;
        this.auth = auth;
        this.reservations = reservations;
        this.fines = fines;
        this.sessions = sessions;
    }

    public List<Member> search(String keyword) { return store.searchMembers(keyword); }

    public Member get(int id) {
        User u = store.findUser(id);
        if (!(u instanceof Member m) || m.isDeleted()) throw AppException.notFound("Member not found");
        return m;
    }

    /** Issues the library card (1 year) and sets the status to ACTIVE. */
    public Member activate(int memberId, String cardNumber) {
        if (TextUtil.isBlank(cardNumber)) throw AppException.badRequest("Card number is required").field("cardNumber");
        String card = cardNumber.trim().toUpperCase();
        if (!card.matches("[A-Z0-9-]{3,30}")) throw AppException.badRequest("Card number may only contain letters, digits and '-'").field("cardNumber");
        return store.tx(() -> {
            Member m = get(memberId);
            if (m.getStatus() == AccountStatus.LOCKED) throw AppException.conflict("The account is locked. Unlock it first");
            boolean renewing = m.getStatus() == AccountStatus.ACTIVE;
            if (renewing && m.isCardValid(LocalDate.now(clock))) throw AppException.conflict("This member already has a valid library card");
            Member other = store.findMemberByCard(card);
            if (other != null && other.getUserId() != memberId) throw AppException.conflict("Card number already exists").field("cardNumber");
            m.setCardNumber(card);
            m.setCardExpiry(LocalDate.now(clock).plusYears(Rules.CARD_VALID_YEARS));
            m.setStatus(AccountStatus.ACTIVE);
            return (Member) store.saveUser(m);
        });
    }

    public Member update(int memberId, ProfileUpdate data) {
        get(memberId);
        return (Member) auth.updateProfile(memberId, new ProfileUpdate(data.fullName(), data.phone(), data.address(), data.dateOfBirth(), data.email(), null, null));
    }

    public Member lock(int memberId) {
        return store.tx(() -> {
            Member m = get(memberId);
            if (m.getStatus() == AccountStatus.LOCKED) throw AppException.conflict("The account is already locked");
            m.setStatus(AccountStatus.LOCKED);
            store.saveUser(m);
            sessions.invalidateAllOf(memberId);
            return m;
        });
    }

    public Member unlock(int memberId) {
        return store.tx(() -> {
            Member m = get(memberId);
            if (m.getStatus() != AccountStatus.LOCKED) throw AppException.conflict("The account is not locked");
            m.setStatus(m.getCardNumber() != null ? AccountStatus.ACTIVE : AccountStatus.PENDING);
            m.resetFailedLogins();
            return (Member) store.saveUser(m);
        });
    }

    /** Refused while the member has active loans or unpaid fines. The record is hidden, its history is kept. */
    public void delete(int memberId) {
        store.tx(() -> {
            Member m = get(memberId);
            if (!store.activeLoansOfMember(memberId).isEmpty()) throw AppException.conflict("The member cannot be deleted because of active loans");
            if (fines.unpaidTotal(memberId) > 0) throw AppException.conflict("The member cannot be deleted because of unpaid fines");
            for (Reservation r : store.activeReservationsOfMember(memberId)) reservations.cancel(memberId, r.getReservationId());
            m.setDeleted(true);
            m.setUsername(m.getUsername() + "#" + m.getUserId());
            m.setEmail("deleted-" + m.getUserId() + "@deleted.local");
            m.setCardNumber(null);
            m.setStatus(AccountStatus.LOCKED);
            store.saveUser(m);
            sessions.invalidateAllOf(memberId);
            return null;
        });
    }
}
