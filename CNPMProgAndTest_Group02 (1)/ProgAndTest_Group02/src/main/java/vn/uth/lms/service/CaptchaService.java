package vn.uth.lms.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Simple arithmetic CAPTCHA used by the registration form (NFR-SE3). */
public class CaptchaService {
    public record Challenge(String id, String question) {}
    private record Entry(int answer, LocalDateTime expires) {}

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public CaptchaService(Clock clock) { this.clock = clock; }

    public Challenge create() {
        LocalDateTime now = LocalDateTime.now(clock);
        entries.values().removeIf(e -> e.expires().isBefore(now));
        int a = 1 + random.nextInt(9), b = 1 + random.nextInt(9);
        String id = UUID.randomUUID().toString();
        entries.put(id, new Entry(a + b, now.plusMinutes(10)));
        return new Challenge(id, a + " + " + b + " = ?");
    }

    /** One-time check: the challenge is consumed whether the answer is right or wrong. */
    public boolean verify(String id, String answer) {
        Entry e = id == null ? null : entries.remove(id);
        if (e == null || e.expires().isBefore(LocalDateTime.now(clock))) return false;
        try { return Integer.parseInt(answer.trim()) == e.answer(); } catch (RuntimeException ex) { return false; }
    }
}
