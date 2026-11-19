package vn.uth.lms.app;

import java.time.Clock;
import vn.uth.lms.controller.*;
import vn.uth.lms.persistence.JdbcStore;
import vn.uth.lms.persistence.LibraryStore;
import vn.uth.lms.persistence.MemoryStore;
import vn.uth.lms.web.HttpApp;
import vn.uth.lms.web.Router;

/** Entry point: builds the layers, registers the controllers and starts the web server. */
public class Main {

    public static HttpApp build(AppContext app) {
        Router router = new Router();
        new AuthController().register(router, app);
        new CatalogController().register(router, app);
        new CirculationController().register(router, app);
        new AdminController().register(router, app);
        return new HttpApp(app, router);
    }

    public static void main(String[] args) throws Exception {
        Config cfg = new Config();
        Clock clock = Clock.systemDefaultZone();
        LibraryStore store = cfg.storage.equals("memory") ? new MemoryStore() : new JdbcStore(cfg.dbUrl, cfg.dbUser, cfg.dbPassword);
        if (cfg.seed && SeedData.seed(store, clock)) System.out.println("Demo data created (password of every demo account: " + SeedData.PASSWORD + ")");
        AppContext app = new AppContext(store, clock, cfg.devMode);
        int port = build(app).start(cfg.port);
        System.out.println("Library Management System is running at http://localhost:" + port + "  [storage=" + cfg.storage + "]");
    }
}
