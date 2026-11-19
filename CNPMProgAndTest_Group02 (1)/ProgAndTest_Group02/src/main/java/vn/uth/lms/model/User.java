package vn.uth.lms.model;

import java.time.LocalDateTime;
import vn.uth.lms.service.AuthService;
import vn.uth.lms.service.ProfileUpdate;
import vn.uth.lms.service.Session;

public abstract class User {
    private int userId;
    private String username;
    private String passwordHash;
    private String fullName;
    private String email;
    private String phone;
    private AccountStatus status;
    private int failedAttempts;
    private LocalDateTime lockedUntil;
    private LocalDateTime createdAt;
    private boolean deleted;

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }
    public int getFailedAttempts() { return failedAttempts; }
    public void setFailedAttempts(int failedAttempts) { this.failedAttempts = failedAttempts; }
    public LocalDateTime getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(LocalDateTime lockedUntil) { this.lockedUntil = lockedUntil; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    public abstract Role getRole();

    public boolean isTemporarilyLocked(LocalDateTime now) { return lockedUntil != null && now.isBefore(lockedUntil); }

    /** Counts a failed login; after MAX_FAILED_LOGINS in a row the account is locked for LOCK_MINUTES. */
    public void registerFailedLogin(LocalDateTime now) {
        failedAttempts++;
        if (failedAttempts >= Rules.MAX_FAILED_LOGINS) {
            lockedUntil = now.plusMinutes(Rules.LOCK_MINUTES);
            failedAttempts = 0;
        }
    }

    public void resetFailedLogins() { failedAttempts = 0; lockedUntil = null; }

    // ---- operations of the class diagram (delegate to the business layer) ----
    public Session login(AuthService auth, String password) { return auth.login(username, password); }
    public void logout(AuthService auth, String token) { auth.logout(token); }
    public User updateProfile(AuthService auth, ProfileUpdate update) { return auth.updateProfile(userId, update); }
    public void resetPassword(AuthService auth) { auth.resetPassword(email); }
}
