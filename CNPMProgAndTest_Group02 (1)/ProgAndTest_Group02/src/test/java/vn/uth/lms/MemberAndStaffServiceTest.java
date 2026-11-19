package vn.uth.lms;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.uth.lms.model.*;
import vn.uth.lms.service.*;
import vn.uth.lms.util.AppException;

/** Use cases: Manage members and Manage staff. */
class MemberAndStaffServiceTest {
    Fixture f;

    @BeforeEach
    void setUp() { f = new Fixture(); }

    private AppException fails(Runnable r) { return assertThrows(AppException.class, r::run); }

    private int id(String username) { return f.store.findUserByUsername(username).getUserId(); }

    // ---------------------------------------------------------------- members
    @Test
    void searchFindsMembersByNameCardOrUsername() {
        assertEquals(6, f.app.members.search("").size());
        assertEquals(1, f.app.members.search("LIB-000123").size());
        assertEquals(1, f.app.members.search("nguyen van a").size());
        assertEquals(1, f.app.members.search("member05").size());
    }

    @Test
    void activateIssuesACardValidForOneYear() {
        Member m = f.app.members.activate(id("member03"), "lib-000127");
        assertEquals(AccountStatus.ACTIVE, m.getStatus());
        assertEquals("LIB-000127", m.getCardNumber());
        assertEquals(f.today().plusYears(1), m.getCardExpiry());
    }

    @Test
    void cardNumberMustBeUnique() {
        AppException e = fails(() -> f.app.members.activate(id("member03"), "LIB-000123"));
        assertEquals("Card number already exists", e.getMessage());
        assertEquals("cardNumber", e.getField());
        assertEquals("cardNumber", fails(() -> f.app.members.activate(id("member03"), " ")).getField());
    }

    @Test
    void lockedOrAlreadyValidMembersCannotBeActivated() {
        assertTrue(fails(() -> f.app.members.activate(id("member02"), "LIB-000200")).getMessage().contains("locked"));
        assertTrue(fails(() -> f.app.members.activate(id("member04"), "LIB-000124")).getMessage().contains("valid library card"));
    }

    @Test
    void expiredCardCanBeRenewed() {
        Member m = f.member("member04");
        m.setCardExpiry(f.today().minusDays(1));
        f.store.saveUser(m);
        assertEquals(f.today().plusYears(1), f.app.members.activate(m.getUserId(), "LIB-000124").getCardExpiry());
    }

    @Test
    void lockedMemberCannotLogInUntilUnlocked() {
        f.app.members.lock(id("member01"));
        assertEquals(403, fails(() -> f.app.auth.login("member01", "123456")).getStatus());
        f.app.members.unlock(id("member01"));
        assertNotNull(f.app.auth.login("member01", "123456"));
        assertEquals(AccountStatus.ACTIVE, f.member("member01").getStatus());
    }

    @Test
    void lockingEndsTheSessionsOfTheMember() {
        Session s = f.app.auth.login("member01", "123456");
        f.app.members.lock(id("member01"));
        assertEquals(401, fails(() -> f.app.sessions.get(s.getToken())).getStatus());
    }

    @Test
    void unlockingAMemberWithoutCardGoesBackToPending() {
        f.app.members.lock(id("member03"));
        assertEquals(AccountStatus.PENDING, f.app.members.unlock(id("member03")).getStatus());
        assertEquals(409, fails(() -> f.app.members.unlock(id("member03"))).getStatus());
        assertEquals(409, fails(() -> f.app.members.lock(id("member02"))).getStatus());
    }

    @Test
    void memberWithActiveLoansOrUnpaidFinesCannotBeDeleted() {
        assertTrue(fails(() -> f.app.members.delete(id("member01"))).getMessage().contains("active loans"));
        assertTrue(fails(() -> f.app.members.delete(id("member06"))).getMessage().contains("active loans"));
        f.app.loans.returnBook(f.librarian(), "BC-000018", "OK", null);
        assertTrue(fails(() -> f.app.members.delete(id("member06"))).getMessage().contains("unpaid fines"));
    }

    @Test
    void deletedMemberCannotLogInAndTheUsernameCanBeReused() {
        int deletedId = id("member04");
        f.app.members.delete(deletedId);
        assertEquals(404, fails(() -> f.app.members.get(deletedId)).getStatus());
        assertEquals(5, f.app.members.search("").size());
        assertEquals(401, fails(() -> f.app.auth.login("member04", "123456")).getStatus());
        assertNotNull(f.app.auth.register(new RegisterRequest("New Person", "2005-05-15", "x", "0901234567", "new04@mail.com", "member04", "123456", "123456")));
    }

    @Test
    void deletingAMemberCancelsTheReservations() {
        Reservation r = f.app.reservations.reserve(id("member04"), 6);
        f.app.members.delete(id("member04"));
        assertEquals(ReservationStatus.CANCELLED, f.store.findReservation(r.getReservationId()).getStatus());
    }

    @Test
    void updateMemberChecksTheData() {
        Member m = f.app.members.update(id("member01"), new ProfileUpdate("Nguyen Van A2", "0911999999", "New address", "2004-05-15", "vana@gmail.com", null, null));
        assertEquals("Nguyen Van A2", m.getFullName());
        assertEquals("email", fails(() -> f.app.members.update(id("member01"), new ProfileUpdate("A", "0911999999", "x", "2004-05-15", "bad", null, null))).getField());
    }

    // ------------------------------------------------------------------ staff
    private StaffService.StaffInput staff(String username, String email, String code, String role) {
        return new StaffService.StaffInput("Tran Thi B", email, "0903333333", username, "123456", role, code, "2026-11-01");
    }

    @Test
    void adminCreatesALibrarianWhoCanLogIn() {
        Librarian l = f.app.staff.create(staff("staff02", "staff02@lib.vn", "ST003", "LIBRARIAN"));
        assertEquals(Role.LIBRARIAN, l.getRole());
        assertEquals(Role.LIBRARIAN, f.app.auth.login("staff02", "123456").getRole());
        assertEquals(3, f.app.staff.list().size());
    }

    @Test
    void adminCanCreateAnotherAdmin() {
        Librarian a = f.app.staff.create(staff("admin02", "admin02@lib.vn", "ST004", "ADMIN"));
        assertTrue(a instanceof Admin);
        assertEquals(Role.ADMIN, f.app.auth.login("admin02", "123456").getRole());
    }

    @Test
    void usernameEmailAndStaffCodeMustBeUnique() {
        assertEquals("username", fails(() -> f.app.staff.create(staff("staff01", "x@lib.vn", "ST010", "LIBRARIAN"))).getField());
        assertEquals("email", fails(() -> f.app.staff.create(staff("staff09", "staff01@library.vn", "ST010", "LIBRARIAN"))).getField());
        assertEquals("staffCode", fails(() -> f.app.staff.create(staff("staff09", "x@lib.vn", "ST002", "LIBRARIAN"))).getField());
    }

    @Test
    void staffDataIsValidated() {
        assertEquals("role", fails(() -> f.app.staff.create(staff("staff09", "x@lib.vn", "ST010", "BOSS"))).getField());
        StaffService.StaffInput shortPw = new StaffService.StaffInput("A", "x@lib.vn", "0903333333", "staff09", "123", "LIBRARIAN", "ST010", null);
        assertEquals("password", fails(() -> f.app.staff.create(shortPw)).getField());
    }

    @Test
    void updateStaffChangesThePhone() {
        Librarian l = f.app.staff.update(id("staff01"), new StaffService.StaffInput("Le Thi Thu Thu", "staff01@library.vn", "0904444444", null, null, null, "ST002", null));
        assertEquals("0904444444", l.getPhone());
        assertEquals("staffCode", fails(() -> f.app.staff.update(id("staff01"), new StaffService.StaffInput("A", "staff01@library.vn", "0904444444", null, null, null, "ST001", null))).getField());
    }

    @Test
    void lockedLibrarianCannotLogIn() {
        f.app.staff.lock(id("admin01"), id("staff01"));
        assertEquals(403, fails(() -> f.app.auth.login("staff01", "123456")).getStatus());
        f.app.staff.unlock(id("staff01"));
        assertNotNull(f.app.auth.login("staff01", "123456"));
    }

    @Test
    void adminCannotLockOrDeleteHisOwnAccount() {
        assertEquals(409, fails(() -> f.app.staff.lock(id("admin01"), id("admin01"))).getStatus());
        assertEquals(409, fails(() -> f.app.staff.delete(id("admin01"), id("admin01"))).getStatus());
    }

    @Test
    void deletedStaffCannotLogInAndTheLastAdminIsProtected() {
        assertTrue(fails(() -> f.app.staff.delete(id("staff01"), id("admin01"))).getMessage().contains("last admin"));
        f.app.staff.delete(id("admin01"), id("staff01"));
        assertEquals(401, fails(() -> f.app.auth.login("staff01", "123456")).getStatus());
        assertEquals(1, f.app.staff.list().size());
        assertNotNull(f.app.staff.create(staff("staff01", "staff01@library.vn", "ST002", "LIBRARIAN")));
    }
}
