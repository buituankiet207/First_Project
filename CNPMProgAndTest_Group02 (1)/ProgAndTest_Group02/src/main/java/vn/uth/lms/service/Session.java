package vn.uth.lms.service;

import java.time.LocalDateTime;
import vn.uth.lms.model.Role;

public class Session {
    private final String token;
    private final int userId;
    private final Role role;
    private LocalDateTime lastAccess;

    public Session(String token, int userId, Role role, LocalDateTime lastAccess) {
        this.token = token;
        this.userId = userId;
        this.role = role;
        this.lastAccess = lastAccess;
    }

    public String getToken() { return token; }
    public int getUserId() { return userId; }
    public Role getRole() { return role; }
    public LocalDateTime getLastAccess() { return lastAccess; }
    void touch(LocalDateTime now) { this.lastAccess = now; }
}
