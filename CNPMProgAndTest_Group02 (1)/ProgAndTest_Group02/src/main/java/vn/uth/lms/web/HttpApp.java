package vn.uth.lms.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import vn.uth.lms.app.AppContext;
import vn.uth.lms.model.AccountStatus;
import vn.uth.lms.model.Role;
import vn.uth.lms.service.Session;
import vn.uth.lms.util.AppException;
import vn.uth.lms.util.Json;

/** Embedded HTTP server: serves the web UI (static files) and the JSON API; enforces login and roles. */
public class HttpApp {
    private static final int MAX_BODY = 1_000_000;

    private final AppContext ctx;
    private final Router router;
    private HttpServer server;

    public HttpApp(AppContext ctx, Router router) {
        this.ctx = ctx;
        this.router = router;
    }

    public int start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(16));
        server.start();
        return server.getAddress().getPort();
    }

    public void stop() { if (server != null) server.stop(0); }

    private void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if (path.startsWith("/api/")) api(ex, path);
            else staticFile(ex, path);
        } catch (AppException e) {
            sendJson(ex, e.getStatus(), error(e));
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(ex, 500, error(new AppException(500, "Internal server error")));
        } finally {
            ex.close();
        }
    }

    private void api(HttpExchange ex, String path) throws Exception {
        String method = ex.getRequestMethod();
        Router.Match match = router.find(method, path);
        if (match == null) {
            if (router.pathExists(path)) throw new AppException(405, "Method not allowed");
            throw AppException.notFound("Unknown API endpoint");
        }
        Map<String, String> query = parseQuery(ex.getRequestURI().getRawQuery());
        Map<String, Object> body = new LinkedHashMap<>();
        if (!method.equals("GET")) {
            byte[] raw = readLimited(ex.getRequestBody());
            if (raw.length > 0) {
                try { body = Json.parseObject(new String(raw, StandardCharsets.UTF_8)); }
                catch (RuntimeException e) { throw AppException.badRequest("Invalid JSON in the request body"); }
            }
        }
        String token = ex.getRequestHeaders().getFirst("X-Auth-Token");
        Ctx c = new Ctx(method, path, query, body, match.vars, token);
        authorize(c, match.access());
        Object result = match.handler().handle(c);
        if (result instanceof Raw r) {
            ex.getResponseHeaders().set("Content-Type", r.contentType);
            if (r.filename != null) ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + r.filename + "\"");
            security(ex);
            ex.sendResponseHeaders(200, r.body.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(r.body); }
        } else {
            sendJson(ex, 200, result == null ? Map.of("ok", true) : result);
        }
    }

    private void authorize(Ctx c, Access access) {
        if (access == Access.PUBLIC) return;
        Session s = ctx.sessions.get(c.token);
        c.session = s;
        c.user = ctx.auth.getUser(s.getUserId());
        if (c.user.getStatus() == AccountStatus.LOCKED) {
            ctx.sessions.invalidate(c.token);
            throw AppException.unauthorized("Your account is locked");
        }
        Role r = s.getRole();
        boolean ok = switch (access) {
            case AUTHENTICATED -> true;
            case MEMBER -> r == Role.MEMBER;
            case STAFF -> r == Role.LIBRARIAN || r == Role.ADMIN;
            case ADMIN -> r == Role.ADMIN;
            default -> true;
        };
        if (!ok) throw AppException.forbidden("Access denied: your role is not allowed to use this function");
    }

    // ------------------------------------------------------------ static files
    private void staticFile(HttpExchange ex, String path) throws IOException {
        if (!ex.getRequestMethod().equals("GET") && !ex.getRequestMethod().equals("HEAD")) throw new AppException(405, "Method not allowed");
        if (path.equals("/")) path = "/index.html";
        if (path.contains("..")) throw AppException.notFound("Not found");
        try (InputStream in = HttpApp.class.getResourceAsStream("/web" + path)) {
            if (in == null) throw AppException.notFound("Not found");
            byte[] data = in.readAllBytes();
            ex.getResponseHeaders().set("Content-Type", contentType(path));
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            security(ex);
            ex.sendResponseHeaders(200, data.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(data); }
        }
    }

    private static String contentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    // ----------------------------------------------------------------- helpers
    private static void security(HttpExchange ex) {
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.getResponseHeaders().set("X-Frame-Options", "DENY");
        ex.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:");
    }

    private static Map<String, Object> error(AppException e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", e.getMessage());
        if (e.getField() != null) m.put("field", e.getField());
        return m;
    }

    private static void sendJson(HttpExchange ex, int status, Object payload) throws IOException {
        byte[] data = Json.write(payload).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        security(ex);
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        byte[] data = in.readNBytes(MAX_BODY + 1);
        if (data.length > MAX_BODY) throw new AppException(413, "Request is too large");
        return data;
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> m = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return m;
        for (String part : raw.split("&")) {
            int i = part.indexOf('=');
            String k = URLDecoder.decode(i < 0 ? part : part.substring(0, i), StandardCharsets.UTF_8);
            String v = i < 0 ? "" : URLDecoder.decode(part.substring(i + 1), StandardCharsets.UTF_8);
            m.put(k, v);
        }
        return m;
    }
}
