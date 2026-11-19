package vn.uth.lms;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.uth.lms.model.*;
import vn.uth.lms.service.*;
import vn.uth.lms.util.AppException;

/** Use cases: Register account, Log in, Reset password, Update profile. */
class AuthServiceTest {
    Fixture f;

    @BeforeEach
    void setUp() { f = new Fixture(); }

    private RegisterRequest req(String username, String email, String password, String confirm) {
        return new RegisterRequest("Nguyen Van Moi", "2005-05-15", "12 Nguyen Hue", "0901234567", email, username, password, confirm);
    }

    private AppException fails(Runnable r) { return assertThrows(AppException.class, r::run); }

    // ---------------------------------------------------------------- register
    @Test
    void registerCreatesPendingMemberWithHashedPassword() {
        Member m = f.app.auth.register(req("newuser", "new@mail.com", "123456", "123456"));
        assertEquals(AccountStatus.PENDING, m.getStatus());
        assertEquals(Role.MEMBER, m.getRole());
        assertNull(m.getCardNumber());
        assertFalse(m.getPasswordHash().contains("123456"));
    }

    @Test
    void registerRequiresEveryField() {
        AppException e = fails(() -> f.app.auth.register(new RegisterRequest("", "2005-05-15", "a", "0901234567", "a@b.com", "u1234", "123456", "123456")));
        assertEquals("Full name is required", e.getMessage());
        assertEquals("fullName", e.getField());
    }

    @Test
    void registerRejectsExistingUsernameAndEmail() {
        assertEquals("Username already exists", fails(() -> f.app.auth.register(req("member01", "x@mail.com", "123456", "123456"))).getMessage());
        assertEquals("Email already exists", fails(() -> f.app.auth.register(req("another", "vana@gmail.com", "123456", "123456"))).getMessage());
    }

    @Test
    void passwordMustHaveAtLeastSixCharacters() {
        AppException e = fails(() -> f.app.auth.register(req("short1", "s@mail.com", "12345", "12345")));
        assertEquals("password", e.getField());
        assertNotNull(f.app.auth.register(req("six123", "six@mail.com", "123456", "123456")));
    }

    @Test
    void confirmPasswordMustMatch() {
        assertEquals("confirmPassword", fails(() -> f.app.auth.register(req("mism1", "m@mail.com", "123456", "654321"))).getField());
    }

    @Test
    void registerRejectsInvalidEmailAndPhone() {
        assertEquals("email", fails(() -> f.app.auth.register(req("mail1", "vana.gmail.com", "123456", "123456"))).getField());
        RegisterRequest bad = new RegisterRequest("A", "2005-05-15", "x", "09ABC12345", "ok@mail.com", "phone1", "123456", "123456");
        assertEquals("phone", fails(() -> f.app.auth.register(bad)).getField());
    }

    @Test
    void registerRejectsBirthDateInTheFuture() {
        RegisterRequest bad = new RegisterRequest("A", "2030-01-01", "x", "0901234567", "ok@mail.com", "future1", "123456", "123456");
        assertEquals("dateOfBirth", fails(() -> f.app.auth.register(bad)).getField());
    }

    // ------------------------------------------------------------------- login
    @Test
    void loginSucceedsForEachRole() {
        assertEquals(Role.MEMBER, f.app.auth.login("member01", "123456").getRole());
        assertEquals(Role.LIBRARIAN, f.app.auth.login("staff01", "123456").getRole());
        assertEquals(Role.ADMIN, f.app.auth.login("admin01", "123456").getRole());
    }

    @Test
    void wrongPasswordAndUnknownUserGiveTheSameMessage() {
        assertEquals("Invalid username or password", fails(() -> f.app.auth.login("member01", "bad")).getMessage());
        assertEquals("Invalid username or password", fails(() -> f.app.auth.login("nobody", "123456")).getMessage());
    }

    @Test
    void loginRequiresUsernameAndPassword() {
        assertEquals(400, fails(() -> f.app.auth.login("", "")).getStatus());
    }

    @Test
    void lockedAccountCannotLogIn() {
        AppException e = fails(() -> f.app.auth.login("member02", "123456"));
        assertEquals(403, e.getStatus());
        assertTrue(e.getMessage().contains("locked"));
    }

    @Test
    void pendingAccountCanLogIn() {
        assertEquals(Role.MEMBER, f.app.auth.login("member03", "123456").getRole());
    }

    @Test
    void fiveFailedAttemptsLockTheAccountForFifteenMinutes() {
        for (int i = 0; i < 5; i++) assertEquals("Invalid username or password", fails(() -> f.app.auth.login("member01", "bad")).getMessage());
        AppException e = fails(() -> f.app.auth.login("member01", "123456"));
        assertEquals(403, e.getStatus());
        f.advanceMinutes(14);
        fails(() -> f.app.auth.login("member01", "123456"));
        f.advanceMinutes(2);
        assertNotNull(f.app.auth.login("member01", "123456"));
    }

    @Test
    void successfulLoginResetsTheFailureCounter() {
        for (int i = 0; i < 4; i++) fails(() -> f.app.auth.login("member01", "bad"));
        assertNotNull(f.app.auth.login("member01", "123456"));
        for (int i = 0; i < 4; i++) fails(() -> f.app.auth.login("member01", "bad"));
        assertNotNull(f.app.auth.login("member01", "123456"));
    }

    @Test
    void sessionExpiresAfterThirtyMinutesOfInactivity() {
        Session s = f.app.auth.login("member01", "123456");
        f.advanceMinutes(20);
        assertNotNull(f.app.sessions.get(s.getToken()));
        f.advanceMinutes(20);
        assertNotNull(f.app.sessions.get(s.getToken()));
        f.advanceMinutes(30);
        assertEquals(401, fails(() -> f.app.sessions.get(s.getToken())).getStatus());
    }

    @Test
    void logoutEndsTheSession() {
        Session s = f.app.auth.login("member01", "123456");
        f.app.auth.logout(s.getToken());
        assertEquals(401, fails(() -> f.app.sessions.get(s.getToken())).getStatus());
    }

    // ---------------------------------------------------------- reset password
    private String tempPasswordFromMail() {
        List<Map<String, String>> box = f.app.email.getOutbox();
        String body = box.get(box.size() - 1).get("body");
        String marker = "temporary password is: ";
        int i = body.indexOf(marker) + marker.length();
        return body.substring(i, body.indexOf('\n', i));
    }

    @Test
    void resetPasswordMailsATemporaryPassword() {
        f.app.auth.resetPassword("vana@gmail.com");
        String temp = tempPasswordFromMail();
        assertTrue(temp.length() >= 8);
        assertEquals("Invalid username or password", fails(() -> f.app.auth.login("member01", "123456")).getMessage());
        assertNotNull(f.app.auth.login("member01", temp));
    }

    @Test
    void resetPasswordForUnknownEmailIsRefused() {
        AppException e = fails(() -> f.app.auth.resetPassword("unknown@mail.com"));
        assertEquals(404, e.getStatus());
        assertTrue(f.app.email.getOutbox().isEmpty());
    }

    @Test
    void passwordIsUnchangedWhenTheMailCannotBeSent() {
        f.app.email.setFailing(true);
        assertEquals(503, fails(() -> f.app.auth.resetPassword("vana@gmail.com")).getStatus());
        assertNotNull(f.app.auth.login("member01", "123456"));
    }

    // ---------------------------------------------------------- update profile
    private ProfileUpdate profile(String email, String current, String next) {
        return new ProfileUpdate("Nguyen Van B", "0912345678", "5 Le Loi", "2004-05-15", email, current, next);
    }

    @Test
    void updateProfileSavesTheChanges() {
        Member m = f.member("member01");
        f.app.auth.updateProfile(m.getUserId(), profile("vanb@gmail.com", null, null));
        Member saved = f.member("member01");
        assertEquals("Nguyen Van B", saved.getFullName());
        assertEquals("5 Le Loi", saved.getAddress());
        assertEquals("member01", saved.getUsername());
    }

    @Test
    void changePasswordNeedsTheCurrentPassword() {
        int id = f.member("member01").getUserId();
        assertEquals("currentPassword", fails(() -> f.app.auth.updateProfile(id, profile("vana@gmail.com", "wrong", "abc12345"))).getField());
        f.app.auth.updateProfile(id, profile("vana@gmail.com", "123456", "abc12345"));
        assertNotNull(f.app.auth.login("member01", "abc12345"));
        assertEquals("Invalid username or password", fails(() -> f.app.auth.login("member01", "123456")).getMessage());
    }

    @Test
    void emailUsedByAnotherAccountIsRefused() {
        int id = f.member("member01").getUserId();
        assertEquals("Email is used by another account", fails(() -> f.app.auth.updateProfile(id, profile("thid@gmail.com", null, null))).getMessage());
    }

    @Test
    void invalidProfileDataIsRefused() {
        int id = f.member("member01").getUserId();
        assertEquals("email", fails(() -> f.app.auth.updateProfile(id, profile("vanb.gmail.com", null, null))).getField());
    }
}
