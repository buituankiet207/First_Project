package vn.uth.lms.model;

/** Business constants taken from the Requirement Specification (section 1). */
public final class Rules {
    private Rules() {}
    public static final int MAX_ACTIVE_LOANS = 5;
    public static final int LOAN_DAYS = 14;
    public static final int MAX_RENEWALS = 2;
    public static final int RENEW_DAYS = 14;
    public static final int MAX_ACTIVE_RESERVATIONS = 3;
    public static final int HOLD_DAYS = 3;
    public static final int CARD_VALID_YEARS = 1;
    public static final long DEFAULT_FINE_PER_DAY = 1000;
    public static final int MIN_PASSWORD_LENGTH = 6;
    public static final int TEMP_PASSWORD_LENGTH = 10;
    public static final int MAX_FAILED_LOGINS = 5;
    public static final int LOCK_MINUTES = 15;
    public static final int SESSION_MINUTES = 30;
    public static final int PAGE_SIZE = 10;
}
