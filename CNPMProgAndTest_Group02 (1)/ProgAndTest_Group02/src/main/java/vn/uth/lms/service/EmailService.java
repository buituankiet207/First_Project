package vn.uth.lms.service;

/** Outgoing e-mail gateway (used for the temporary password of "Reset password"). */
public interface EmailService {
    void send(String to, String subject, String body);
}
