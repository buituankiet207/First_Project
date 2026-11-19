package vn.uth.lms.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import vn.uth.lms.model.*;
import vn.uth.lms.persistence.LibraryStore;
import vn.uth.lms.util.AppException;
import vn.uth.lms.util.PasswordHasher;
import vn.uth.lms.util.TextUtil;

/** Use cases: Register account, Log in, Reset password, Update profile. */
public class AuthService {
    public static final String INVALID_CREDENTIALS = "Invalid username or password";

    private final LibraryStore store;
    private final Clock clock;
    private final EmailService email;
    private final SessionManager sessions;

    public AuthService(LibraryStore store, Clock clock, EmailService email, SessionManager sessions) {
        this.store = store;
        this.clock = clock;
        this.email = email;
        this.sessions = sessions;
    }

    // ------------------------------------------------------------ register
    public Member register(RegisterRequest r) {
        required(r.fullName(), "fullName", "Full name");
        required(r.dateOfBirth(), "dateOfBirth", "Date of birth");
        required(r.address(), "address", "Address");
        required(r.phone(), "phone", "Phone");
        required(r.email(), "email", "Email");
        required(r.username(), "username", "Username");
        required(r.password(), "password", "Password");
        required(r.confirmPassword(), "confirmPassword", "Confirm password");
        LocalDate dob = parseDate(r.dateOfBirth(), "dateOfBirth", "Date of birth");
        if (!dob.isBefore(LocalDate.now(clock))) throw AppException.badRequest("Date of birth must be in the past").field("dateOfBirth");
        if (!TextUtil.isValidPhone(r.phone())) throw AppException.badRequest("Invalid phone number").field("phone");
        if (!TextUtil.isValidEmail(r.email())) throw AppException.badRequest("Invalid email format").field("email");
        if (!r.username().trim().matches("[A-Za-z0-9._-]{3,50}"))
            throw AppException.badRequest("Username must have 3-50 characters (letters, digits, . _ -)").field("username");
        checkPassword(r.password(), "password");
        if (!r.password().equals(r.confirmPassword())) throw AppException.badRequest("Confirm password does not match").field("confirmPassword");
        return store.tx(() -> {
            if (store.findUserByUsername(r.username().trim()) != null) throw AppException.conflict("Username already exists").field("username");
            if (store.findUserByEmail(r.email().trim()) != null) throw AppException.conflict("Email already exists").field("email");
            Member m = new Member();
            m.setUsername(r.username().trim());
            m.setPasswordHash(PasswordHasher.hash(r.password()));
            m.setFullName(r.fullName().trim());
            m.setEmail(r.email().trim());
            m.setPhone(r.phone().trim());
            m.setAddress(r.address().trim());
            m.setDateOfBirth(dob);
            m.setStatus(AccountStatus.PENDING);
            m.setCreatedAt(LocalDateTime.now(clock));
            return (Member) store.saveUser(m);
        });
    }

    // --------------------------------------------------------------- login
    public Session login(String username, String password) {
        if (TextUtil.isBlank(username) || TextUtil.isBlank(password))
            throw AppException.badRequest("Please enter your username and password");
        return store.tx(() -> {
            LocalDateTime now = LocalDateTime.now(clock);
            User u = store.findUserByUsername(username.trim());
            if (u == null || u.isDeleted()) throw AppException.unauthorized(INVALID_CREDENTIALS);
            if (u.isTemporarilyLocked(now))
                throw AppException.forbidden("Too many failed attempts. The account is locked until "
                        + u.getLockedUntil().format(DateTimeFormatter.ofPattern("HH:mm")));
            if (!PasswordHasher.verify(password, u.getPasswordHash())) {
                u.registerFailedLogin(now);
                store.saveUser(u);
                throw AppException.unauthorized(INVALID_CREDENTIALS);
            }
            if (u.getStatus() == AccountStatus.LOCKED) throw AppException.forbidden("Your account is locked. Please contact the library");
            if (u.getFailedAttempts() != 0 || u.getLockedUntil() != null) {
                u.resetFailedLogins();
                store.saveUser(u);
            }
            return sessions.create(u);
        });
    }

    public void logout(String token) { sessions.invalidate(token); }

    // ------------------------------------------------------ reset password
    public void resetPassword(String emailAddress) {
        required(emailAddress, "email", "Email");
        store.tx(() -> {
            User u = store.findUserByEmail(emailAddress.trim());
            if (u == null || u.isDeleted()) throw AppException.notFound("No account is registered with this email").field("email");
            String temp = PasswordHasher.randomPassword(Rules.TEMP_PASSWORD_LENGTH);
            try {
                email.send(u.getEmail(), "Library - your new password",
                        "Hello " + u.getFullName() + ",\n\nYour temporary password is: " + temp
                                + "\nPlease log in and change it in your profile.");
            } catch (RuntimeException ex) {
                throw new AppException(503, "Could not send the email. Your password was not changed").field("email");
            }
            u.setPasswordHash(PasswordHasher.hash(temp));
            store.saveUser(u);
            return null;
        });
    }

    // ------------------------------------------------------ update profile
    public User updateProfile(int userId, ProfileUpdate p) {
        required(p.fullName(), "fullName", "Full name");
        required(p.email(), "email", "Email");
        required(p.phone(), "phone", "Phone");
        if (!TextUtil.isValidEmail(p.email())) throw AppException.badRequest("Invalid email format").field("email");
        if (!TextUtil.isValidPhone(p.phone())) throw AppException.badRequest("Invalid phone number").field("phone");
        return store.tx(() -> {
            User u = store.findUser(userId);
            if (u == null) throw AppException.notFound("User not found");
            User other = store.findUserByEmail(p.email().trim());
            if (other != null && other.getUserId() != userId) throw AppException.conflict("Email is used by another account").field("email");
            if (u instanceof Member m) {
                required(p.address(), "address", "Address");
                required(p.dateOfBirth(), "dateOfBirth", "Date of birth");
                LocalDate dob = parseDate(p.dateOfBirth(), "dateOfBirth", "Date of birth");
                if (!dob.isBefore(LocalDate.now(clock))) throw AppException.badRequest("Date of birth must be in the past").field("dateOfBirth");
                m.setAddress(p.address().trim());
                m.setDateOfBirth(dob);
            }
            if (!TextUtil.isBlank(p.newPassword())) {
                if (!PasswordHasher.verify(p.currentPassword() == null ? "" : p.currentPassword(), u.getPasswordHash()))
                    throw AppException.badRequest("Current password is incorrect").field("currentPassword");
                checkPassword(p.newPassword(), "newPassword");
                u.setPasswordHash(PasswordHasher.hash(p.newPassword()));
            }
            u.setFullName(p.fullName().trim());
            u.setEmail(p.email().trim());
            u.setPhone(p.phone().trim());
            return store.saveUser(u);
        });
    }

    public User getUser(int userId) {
        User u = store.findUser(userId);
        if (u == null || u.isDeleted()) throw AppException.unauthorized("Account no longer exists");
        return u;
    }

    // -------------------------------------------------------------- helpers
    static void required(String value, String field, String label) {
        if (TextUtil.isBlank(value)) throw AppException.badRequest(label + " is required").field(field);
    }

    static void checkPassword(String password, String field) {
        if (password.length() < Rules.MIN_PASSWORD_LENGTH)
            throw AppException.badRequest("Password must have at least " + Rules.MIN_PASSWORD_LENGTH + " characters").field(field);
    }

    static LocalDate parseDate(String s, String field, String label) {
        try { return LocalDate.parse(s.trim()); }
        catch (DateTimeParseException e) { throw AppException.badRequest(label + " is not a valid date (yyyy-MM-dd)").field(field); }
    }
}
