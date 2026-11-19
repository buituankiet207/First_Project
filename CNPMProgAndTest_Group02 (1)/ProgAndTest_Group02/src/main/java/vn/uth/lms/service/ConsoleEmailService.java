package vn.uth.lms.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Demo e-mail service: prints the message to the console and keeps the last messages in an outbox. */
public class ConsoleEmailService implements EmailService {
    private final List<Map<String, String>> outbox = new ArrayList<>();
    private volatile boolean failing;

    @Override
    public synchronized void send(String to, String subject, String body) {
        if (failing) throw new IllegalStateException("Mail server unavailable");
        Map<String, String> m = new LinkedHashMap<>();
        m.put("to", to);
        m.put("subject", subject);
        m.put("body", body);
        outbox.add(m);
        if (outbox.size() > 50) outbox.remove(0);
        System.out.println("[MAIL] to=" + to + " subject=" + subject + "\n" + body);
    }

    public synchronized List<Map<String, String>> getOutbox() { return new ArrayList<>(outbox); }

    public void setFailing(boolean failing) { this.failing = failing; }
}
