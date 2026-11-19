package vn.uth.lms.app;

import java.time.Clock;
import vn.uth.lms.persistence.LibraryStore;
import vn.uth.lms.service.*;

/** Wires the layers together: data access -> business services (used by the controllers). */
public class AppContext {
    public final LibraryStore store;
    public final Clock clock;
    public final boolean devMode;
    public final ConsoleEmailService email = new ConsoleEmailService();
    public final SessionManager sessions;
    public final CaptchaService captcha;
    public final AuthService auth;
    public final ReservationService reservations;
    public final FineService fines;
    public final LoanService loans;
    public final CatalogService catalog;
    public final MemberService members;
    public final StaffService staff;
    public final ReportService reports;

    public AppContext(LibraryStore store, Clock clock, boolean devMode) {
        this.store = store;
        this.clock = clock;
        this.devMode = devMode;
        this.sessions = new SessionManager(clock);
        this.captcha = new CaptchaService(clock);
        this.auth = new AuthService(store, clock, email, sessions);
        this.reservations = new ReservationService(store, clock);
        this.fines = new FineService(store, clock);
        this.loans = new LoanService(store, clock, reservations, fines);
        this.catalog = new CatalogService(store, clock, reservations);
        this.members = new MemberService(store, clock, auth, reservations, fines, sessions);
        this.staff = new StaffService(store, clock, sessions);
        this.reports = new ReportService(store, clock);
    }
}
