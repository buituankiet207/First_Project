package vn.uth.lms.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import vn.uth.lms.model.Rules;
import vn.uth.lms.model.User;
import vn.uth.lms.util.AppException;

/** Server-side sessions that expire after 30 minutes of inactivity (NFR-SE4). */
public class SessionManager {
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Clock clock;

    public SessionManager(Clock clock) { this.clock = clock; }

    public Session create(User user) {
        Session s = new Session(UUID.randomUUID().toString(), user.getUserId(), user.getRole(), LocalDateTime.now(clock));
        sessions.put(s.getToken(), s);
        return s;
    }

    /** Returns the session and refreshes its inactivity timer; throws 401 when missing or expired. */
    public Session get(String token) {
        Session s = token == null ? null : sessions.get(token);
        if (s == null) throw AppException.unauthorized("Please log in first");
        LocalDateTime now = LocalDateTime.now(clock);
        if (Duration.between(s.getLastAccess(), now).toMinutes() >= Rules.SESSION_MINUTES) {
            sessions.remove(token);
            throw AppException.unauthorized("Session expired. Please log in again");
        }
        s.touch(now);
        return s;
    }

    public void invalidate(String token) { if (token != null) sessions.remove(token); }

    public void invalidateAllOf(int userId) { sessions.values().removeIf(s -> s.getUserId() == userId); }
}
