package vn.uth.lms.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import vn.uth.lms.app.AppContext;
import vn.uth.lms.model.User;
import vn.uth.lms.service.*;
import vn.uth.lms.util.AppException;
import vn.uth.lms.web.Access;
import vn.uth.lms.web.Router;

/** Controller for: Register account, Log in, Reset password, Update profile. */
public class AuthController {
    public void register(Router r, AppContext app) {
        r.get("/api/captcha", Access.PUBLIC, c -> {
            CaptchaService.Challenge ch = app.captcha.create();
            return Map.of("id", ch.id(), "question", ch.question());
        });

        r.post("/api/register", Access.PUBLIC, c -> {
            RegisterRequest req = new RegisterRequest(c.str("fullName"), c.str("dateOfBirth"), c.str("address"), c.str("phone"),
                    c.str("email"), c.str("username"), c.str("password"), c.str("confirmPassword"));
            // validate the form first, so the CAPTCHA is only consumed by a complete submission
            if (!app.captcha.verify(c.str("captchaId"), c.str("captchaAnswer")))
                throw AppException.badRequest("The CAPTCHA answer is incorrect").field("captchaAnswer");
            app.auth.register(req);
            return Map.of("message", "Registration successful. Please wait for the librarian to activate your library card.");
        });

        r.post("/api/login", Access.PUBLIC, c -> {
            Session s = app.auth.login(c.str("username"), c.str("password"));
            User u = app.auth.getUser(s.getUserId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("token", s.getToken());
            m.put("user", Dto.user(u));
            return m;
        });

        r.post("/api/logout", Access.AUTHENTICATED, c -> {
            app.auth.logout(c.token);
            return null;
        });

        r.post("/api/password-reset", Access.PUBLIC, c -> {
            app.auth.resetPassword(c.str("email"));
            return Map.of("message", "A new password has been sent to your email");
        });

        r.get("/api/me", Access.AUTHENTICATED, c -> Dto.user(c.user));

        r.put("/api/profile", Access.MEMBER, c -> {
            User u = app.auth.updateProfile(c.user.getUserId(), new ProfileUpdate(c.str("fullName"), c.str("phone"), c.str("address"),
                    c.str("dateOfBirth"), c.str("email"), c.str("currentPassword"), c.str("newPassword")));
            Map<String, Object> m = new LinkedHashMap<>(Dto.user(u));
            m.put("message", "Your profile has been updated");
            return m;
        });
    }
}
