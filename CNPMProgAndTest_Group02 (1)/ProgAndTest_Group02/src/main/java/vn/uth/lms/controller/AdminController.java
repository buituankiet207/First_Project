package vn.uth.lms.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import vn.uth.lms.app.AppContext;
import vn.uth.lms.model.Member;
import vn.uth.lms.service.ProfileUpdate;
import vn.uth.lms.service.Report;
import vn.uth.lms.service.StaffService;
import vn.uth.lms.util.AppException;
import vn.uth.lms.web.Access;
import vn.uth.lms.web.Ctx;
import vn.uth.lms.web.Raw;
import vn.uth.lms.web.Router;

/** Controller for: Manage members, Manage staff, View reports, configuration. */
public class AdminController {

    private static StaffService.StaffInput staffInput(Ctx c) {
        return new StaffService.StaffInput(c.str("fullName"), c.str("email"), c.str("phone"), c.str("username"),
                c.str("password"), c.str("role"), c.str("staffCode"), c.str("hireDate"));
    }

    public void register(Router r, AppContext app) {
        // ----------------------------------------------------------- members
        r.get("/api/members", Access.STAFF, c -> app.members.search(c.str("q")).stream().map(Dto::user).toList());
        r.get("/api/members/{id}", Access.STAFF, c -> Dto.user(app.members.get(c.pathInt(0))));
        r.put("/api/members/{id}", Access.STAFF, c -> Dto.user(app.members.update(c.pathInt(0), new ProfileUpdate(c.str("fullName"),
                c.str("phone"), c.str("address"), c.str("dateOfBirth"), c.str("email"), null, null))));
        r.post("/api/members/{id}/activate", Access.STAFF, c -> {
            Member m = app.members.activate(c.pathInt(0), c.str("cardNumber"));
            Map<String, Object> out = new LinkedHashMap<>(Dto.user(m));
            out.put("message", "Library card issued. Valid until " + m.getCardExpiry());
            return out;
        });
        r.post("/api/members/{id}/lock", Access.STAFF, c -> Dto.user(app.members.lock(c.pathInt(0))));
        r.post("/api/members/{id}/unlock", Access.STAFF, c -> Dto.user(app.members.unlock(c.pathInt(0))));
        r.delete("/api/members/{id}", Access.STAFF, c -> { app.members.delete(c.pathInt(0)); return null; });

        // ------------------------------------------------------------- staff
        r.get("/api/staff", Access.ADMIN, c -> app.staff.list().stream().map(Dto::user).toList());
        r.post("/api/staff", Access.ADMIN, c -> Dto.user(app.staff.create(staffInput(c))));
        r.put("/api/staff/{id}", Access.ADMIN, c -> Dto.user(app.staff.update(c.pathInt(0), staffInput(c))));
        r.post("/api/staff/{id}/lock", Access.ADMIN, c -> Dto.user(app.staff.lock(c.user.getUserId(), c.pathInt(0))));
        r.post("/api/staff/{id}/unlock", Access.ADMIN, c -> Dto.user(app.staff.unlock(c.pathInt(0))));
        r.delete("/api/staff/{id}", Access.ADMIN, c -> { app.staff.delete(c.user.getUserId(), c.pathInt(0)); return null; });

        // ----------------------------------------------------------- reports
        r.get("/api/reports/{type}", Access.ADMIN, c -> {
            Report rep = app.reports.generate(c.pathVars.get(0), c.str("from"), c.str("to"));
            if ("csv".equalsIgnoreCase(c.str("format")))
                return new Raw("text/csv; charset=utf-8", rep.toCsv(), c.pathVars.get(0) + "-" + c.str("from") + "-" + c.str("to") + ".csv");
            return rep.toMap();
        });

        // ------------------------------------------------------------ config
        r.get("/api/config", Access.ADMIN, c -> Map.of("finePerDay", app.fines.ratePerDay()));
        r.put("/api/config", Access.ADMIN, c -> {
            Double rate = c.decimal("finePerDay");
            if (rate == null) throw AppException.badRequest("Fine per day is required").field("finePerDay");
            app.fines.setRatePerDay(rate.longValue());
            return Map.of("finePerDay", app.fines.ratePerDay(), "message", "Configuration saved");
        });

        // -------------------------------------------------- demo / development
        r.get("/api/dev/outbox", Access.PUBLIC, c -> {
            if (!app.devMode) throw AppException.notFound("Unknown API endpoint");
            return app.email.getOutbox();
        });
        r.post("/api/dev/mail-failure", Access.PUBLIC, c -> {
            if (!app.devMode) throw AppException.notFound("Unknown API endpoint");
            boolean failing = "true".equalsIgnoreCase(c.str("failing"));
            app.email.setFailing(failing);
            return Map.of("failing", failing);
        });
    }
}
