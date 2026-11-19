package vn.uth.lms;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.uth.lms.app.Main;
import vn.uth.lms.util.Json;
import vn.uth.lms.web.HttpApp;

/** The web API over real HTTP: login, roles, errors, the lending flow, CSV export. */
class ApiIntegrationTest {
    record Resp(int status, String body, String contentType) {
        Map<String, Object> json() { return Json.parseObject(body); }
    }

    Fixture f;
    HttpApp server;
    String base;
    final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws Exception {
        f = new Fixture();
        server = Main.build(f.app);
        base = "http://localhost:" + server.start(0);
    }

    @AfterEach
    void stop() { server.stop(); }

    Resp call(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (body != null) b.header("Content-Type", "application/json");
        if (token != null) b.header("X-Auth-Token", token);
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body(), r.headers().firstValue("Content-Type").orElse(""));
    }

    String login(String user) throws Exception {
        Resp r = call("POST", "/api/login", null, "{\"username\":\"" + user + "\",\"password\":\"123456\"}");
        assertEquals(200, r.status());
        return (String) r.json().get("token");
    }

    @Test
    void loginReturnsATokenAndNeverThePasswordHash() throws Exception {
        Resp r = call("POST", "/api/login", null, "{\"username\":\"member01\",\"password\":\"123456\"}");
        assertEquals(200, r.status());
        assertFalse(r.body().contains("passwordHash"));
        assertFalse(r.body().contains("pbkdf2"));
        assertEquals("MEMBER", ((Map<?, ?>) r.json().get("user")).get("role"));
        assertFalse(((String) r.json().get("token")).isEmpty());
    }

    @Test
    void wrongPasswordGives401WithAMessage() throws Exception {
        Resp r = call("POST", "/api/login", null, "{\"username\":\"member01\",\"password\":\"bad\"}");
        assertEquals(401, r.status());
        assertEquals("Invalid username or password", r.json().get("error"));
    }

    @Test
    void searchIsPublic() throws Exception {
        Resp r = call("GET", "/api/books?q=clean&type=TITLE", null, null);
        assertEquals(200, r.status());
        assertEquals(1, ((List<?>) r.json().get("items")).size());
    }

    @Test
    void searchTreatsInjectionAsPlainText() throws Exception {
        Resp r = call("GET", "/api/books?q=%27%20OR%201%3D1%20--", null, null);
        assertEquals(200, r.status());
        assertEquals(0L, r.json().get("total"));
    }

    @Test
    void protectedRoutesNeedALogin() throws Exception {
        assertEquals(401, call("GET", "/api/loans/mine", null, null).status());
        assertEquals(401, call("GET", "/api/loans/mine", "not-a-token", null).status());
    }

    @Test
    void memberCannotUseLibrarianOrAdminFunctions() throws Exception {
        String t = login("member01");
        assertEquals(403, call("POST", "/api/books", t, "{}").status());
        assertEquals(403, call("GET", "/api/members", t, null).status());
        assertEquals(403, call("GET", "/api/staff", t, null).status());
        assertEquals(403, call("GET", "/api/reports/members?from=2026-01-01&to=2026-12-31", t, null).status());
    }

    @Test
    void librarianCannotOpenAdminFunctions() throws Exception {
        String t = login("staff01");
        assertEquals(403, call("GET", "/api/staff", t, null).status());
        assertEquals(403, call("GET", "/api/reports/members?from=2026-01-01&to=2026-12-31", t, null).status());
        assertEquals(200, call("GET", "/api/members", t, null).status());
    }

    @Test
    void staffCannotUseMemberOnlyFunctions() throws Exception {
        assertEquals(403, call("POST", "/api/reservations", login("staff01"), "{\"bookId\":6}").status());
    }

    @Test
    void lendingFlowChangesTheAvailability() throws Exception {
        String t = login("staff01");
        Resp check = call("GET", "/api/lending/member?card=LIB-000124", t, null);
        assertEquals(true, check.json().get("eligible"));
        Resp lend = call("POST", "/api/lending/lend", t, "{\"card\":\"LIB-000124\",\"barcodes\":[\"BC-000005\"]}");
        assertEquals(200, lend.status());
        assertEquals(1, ((List<?>) ((Map<?, ?>) lend.json().get("slip")).get("items")).size());
        assertEquals(1L, call("GET", "/api/books/2", null, null).json().get("availableCopies"));
        Resp again = call("POST", "/api/lending/lend", t, "{\"card\":\"LIB-000124\",\"barcodes\":[\"BC-000005\"]}");
        assertEquals(409, again.status());
    }

    @Test
    void returnWithOverdueGivesAFineAndPaymentGivesAReceipt() throws Exception {
        String t = login("staff01");
        Resp ret = call("POST", "/api/lending/return", t, "{\"barcode\":\"BC-000014\",\"condition\":\"OK\"}");
        assertEquals(200, ret.status());
        List<?> fines = (List<?>) ret.json().get("fines");
        assertEquals(1, fines.size());
        long fineId = ((Number) ((Map<?, ?>) fines.get(0)).get("fineId")).longValue();
        Resp pay = call("POST", "/api/fines/" + fineId + "/pay", t, null);
        assertEquals(200, pay.status());
        assertEquals("PAID", ((Map<?, ?>) pay.json().get("receipt")).get("status"));
    }

    @Test
    void memberReservesAndSeesTheQueuePosition() throws Exception {
        String t = login("member01");
        Resp r = call("POST", "/api/reservations", t, "{\"bookId\":6}");
        assertEquals(200, r.status());
        assertEquals(1L, r.json().get("position"));
        assertEquals(409, call("POST", "/api/reservations", t, "{\"bookId\":6}").status());
        assertEquals(1, ((List<?>) Json.parse(call("GET", "/api/reservations", t, null).body())).size());
    }

    @Test
    void registrationNeedsTheCaptchaAndThenCreatesAPendingAccount() throws Exception {
        Map<String, Object> c = call("GET", "/api/captcha", null, null).json();
        String[] parts = ((String) c.get("question")).split(" ");
        int answer = Integer.parseInt(parts[0]) + Integer.parseInt(parts[2]);
        String form = "\"fullName\":\"Nguyen Moi\",\"dateOfBirth\":\"2005-05-15\",\"address\":\"x\",\"phone\":\"0901234567\","
                + "\"email\":\"moi@mail.com\",\"username\":\"moi01\",\"password\":\"123456\",\"confirmPassword\":\"123456\"";
        Resp wrong = call("POST", "/api/register", null, "{" + form + ",\"captchaId\":\"" + c.get("id") + "\",\"captchaAnswer\":\"" + (answer + 1) + "\"}");
        assertEquals(400, wrong.status());
        assertEquals("captchaAnswer", wrong.json().get("field"));
        Map<String, Object> c2 = call("GET", "/api/captcha", null, null).json();
        String[] p2 = ((String) c2.get("question")).split(" ");
        int a2 = Integer.parseInt(p2[0]) + Integer.parseInt(p2[2]);
        Resp ok = call("POST", "/api/register", null, "{" + form + ",\"captchaId\":\"" + c2.get("id") + "\",\"captchaAnswer\":\"" + a2 + "\"}");
        assertEquals(200, ok.status());
        assertTrue(((String) ok.json().get("message")).startsWith("Registration successful"));
        assertEquals("PENDING", f.store.findUserByUsername("moi01").getStatus().name());
    }

    @Test
    void validationErrorsNameTheFieldToHighlight() throws Exception {
        Resp r = call("PUT", "/api/profile", login("member01"), "{\"fullName\":\"A\",\"phone\":\"abc\",\"email\":\"a@b.com\",\"address\":\"x\",\"dateOfBirth\":\"2004-01-01\"}");
        assertEquals(400, r.status());
        assertEquals("phone", r.json().get("field"));
    }

    @Test
    void sessionEndsAfterThirtyMinutesOfInactivity() throws Exception {
        String t = login("member01");
        assertEquals(200, call("GET", "/api/me", t, null).status());
        f.advanceMinutes(31);
        Resp r = call("GET", "/api/me", t, null);
        assertEquals(401, r.status());
        assertTrue(((String) r.json().get("error")).contains("expired"));
    }

    @Test
    void lockingAMemberEndsHisSession() throws Exception {
        String member = login("member01");
        String staff = login("staff01");
        assertEquals(200, call("POST", "/api/members/" + f.member("member01").getUserId() + "/lock", staff, null).status());
        assertEquals(401, call("GET", "/api/me", member, null).status());
    }

    @Test
    void adminExportsAReportAsCsv() throws Exception {
        Resp r = call("GET", "/api/reports/members?from=2026-01-01&to=2026-12-31&format=csv", login("admin01"), null);
        assertEquals(200, r.status());
        assertTrue(r.contentType().startsWith("text/csv"));
        assertTrue(r.body().contains("Metric,Value"));
        Resp bad = call("GET", "/api/reports/members?from=2026-12-31&to=2026-01-01", login("admin01"), null);
        assertEquals(400, bad.status());
    }

    @Test
    void unknownEndpointsAndWrongMethods() throws Exception {
        assertEquals(404, call("GET", "/api/nothing", null, null).status());
        assertEquals(405, call("DELETE", "/api/login", null, null).status());
        assertEquals(400, call("POST", "/api/login", null, "{broken").status());
    }

    @Test
    void theWebPageIsServedWithSecurityHeaders() throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(base + "/")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("LIBRARY MANAGEMENT SYSTEM"));
        assertTrue(r.headers().firstValue("Content-Security-Policy").isPresent());
        assertEquals("nosniff", r.headers().firstValue("X-Content-Type-Options").orElse(""));
    }
}
