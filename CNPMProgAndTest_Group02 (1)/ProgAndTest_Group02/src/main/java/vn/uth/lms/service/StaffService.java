package vn.uth.lms.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import vn.uth.lms.model.*;
import vn.uth.lms.persistence.LibraryStore;
import vn.uth.lms.util.AppException;
import vn.uth.lms.util.PasswordHasher;
import vn.uth.lms.util.TextUtil;

/** Use case: Manage staff (admin only): add, update, lock / unlock and delete librarians and admins. */
public class StaffService {

    public record StaffInput(String fullName, String email, String phone, String username, String password,
                             String role, String staffCode, String hireDate) {}

    private final LibraryStore store;
    private final Clock clock;
    private final SessionManager sessions;

    public StaffService(LibraryStore store, Clock clock, SessionManager sessions) {
        this.store = store;
        this.clock = clock;
        this.sessions = sessions;
    }

    public List<Librarian> list() { return store.listStaff(); }

    private Librarian get(int id) {
        User u = store.findUser(id);
        if (!(u instanceof Librarian l) || l.isDeleted()) throw AppException.notFound("Staff account not found");
        return l;
    }

    public Librarian create(StaffInput in) {
        AuthService.required(in.fullName(), "fullName", "Full name");
        AuthService.required(in.email(), "email", "Email");
        AuthService.required(in.phone(), "phone", "Phone");
        AuthService.required(in.username(), "username", "Username");
        AuthService.required(in.password(), "password", "Password");
        AuthService.required(in.staffCode(), "staffCode", "Staff code");
        String role = TextUtil.isBlank(in.role()) ? "LIBRARIAN" : in.role().trim().toUpperCase();
        if (!role.equals("LIBRARIAN") && !role.equals("ADMIN")) throw AppException.badRequest("Role must be LIBRARIAN or ADMIN").field("role");
        if (!TextUtil.isValidEmail(in.email())) throw AppException.badRequest("Invalid email format").field("email");
        if (!TextUtil.isValidPhone(in.phone())) throw AppException.badRequest("Invalid phone number").field("phone");
        if (!in.username().trim().matches("[A-Za-z0-9._-]{3,50}"))
            throw AppException.badRequest("Username must have 3-50 characters (letters, digits, . _ -)").field("username");
        AuthService.checkPassword(in.password(), "password");
        LocalDate hire = TextUtil.isBlank(in.hireDate()) ? LocalDate.now(clock) : AuthService.parseDate(in.hireDate(), "hireDate", "Hire date");
        return store.tx(() -> {
            if (store.findUserByUsername(in.username().trim()) != null) throw AppException.conflict("Username already exists").field("username");
            if (store.findUserByEmail(in.email().trim()) != null) throw AppException.conflict("Email already exists").field("email");
            if (store.staffCodeExists(in.staffCode().trim())) throw AppException.conflict("Staff code already exists").field("staffCode");
            Librarian l = role.equals("ADMIN") ? new Admin() : new Librarian();
            if (l instanceof Admin a) a.setAdminLevel(1);
            l.setUsername(in.username().trim());
            l.setPasswordHash(PasswordHasher.hash(in.password()));
            l.setFullName(in.fullName().trim());
            l.setEmail(in.email().trim());
            l.setPhone(in.phone().trim());
            l.setStaffCode(in.staffCode().trim());
            l.setHireDate(hire);
            l.setStatus(AccountStatus.ACTIVE);
            l.setCreatedAt(java.time.LocalDateTime.now(clock));
            return (Librarian) store.saveUser(l);
        });
    }

    public Librarian update(int id, StaffInput in) {
        AuthService.required(in.fullName(), "fullName", "Full name");
        AuthService.required(in.email(), "email", "Email");
        AuthService.required(in.phone(), "phone", "Phone");
        AuthService.required(in.staffCode(), "staffCode", "Staff code");
        if (!TextUtil.isValidEmail(in.email())) throw AppException.badRequest("Invalid email format").field("email");
        if (!TextUtil.isValidPhone(in.phone())) throw AppException.badRequest("Invalid phone number").field("phone");
        return store.tx(() -> {
            Librarian l = get(id);
            User other = store.findUserByEmail(in.email().trim());
            if (other != null && other.getUserId() != id) throw AppException.conflict("Email already exists").field("email");
            if (!in.staffCode().trim().equalsIgnoreCase(l.getStaffCode()) && store.staffCodeExists(in.staffCode().trim()))
                throw AppException.conflict("Staff code already exists").field("staffCode");
            if (!TextUtil.isBlank(in.hireDate())) l.setHireDate(AuthService.parseDate(in.hireDate(), "hireDate", "Hire date"));
            l.setFullName(in.fullName().trim());
            l.setEmail(in.email().trim());
            l.setPhone(in.phone().trim());
            l.setStaffCode(in.staffCode().trim());
            return (Librarian) store.saveUser(l);
        });
    }

    public Librarian lock(int actingId, int id) {
        if (actingId == id) throw AppException.conflict("You cannot lock your own account");
        return store.tx(() -> {
            Librarian l = get(id);
            if (l.getStatus() == AccountStatus.LOCKED) throw AppException.conflict("The account is already locked");
            l.setStatus(AccountStatus.LOCKED);
            store.saveUser(l);
            sessions.invalidateAllOf(id);
            return l;
        });
    }

    public Librarian unlock(int id) {
        return store.tx(() -> {
            Librarian l = get(id);
            if (l.getStatus() != AccountStatus.LOCKED) throw AppException.conflict("The account is not locked");
            l.setStatus(AccountStatus.ACTIVE);
            l.resetFailedLogins();
            return (Librarian) store.saveUser(l);
        });
    }

    public void delete(int actingId, int id) {
        if (actingId == id) throw AppException.conflict("You cannot delete your own account");
        store.tx(() -> {
            Librarian l = get(id);
            if (l instanceof Admin && store.listStaff().stream().filter(s -> s instanceof Admin && s.getUserId() != id).count() == 0)
                throw AppException.conflict("The last admin account cannot be deleted");
            l.setDeleted(true);
            l.setUsername(l.getUsername() + "#" + id);
            l.setEmail("deleted-" + id + "@deleted.local");
            l.setStaffCode(l.getStaffCode() + "#" + id);
            l.setStatus(AccountStatus.LOCKED);
            store.saveUser(l);
            sessions.invalidateAllOf(id);
            return null;
        });
    }
}
