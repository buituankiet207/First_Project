package vn.uth.lms.web;

import java.util.List;
import java.util.Map;
import vn.uth.lms.model.User;
import vn.uth.lms.service.Session;
import vn.uth.lms.util.AppException;

/** One request: query string, JSON body, path variables and the logged-in user. */
public class Ctx {
    public final String method;
    public final String path;
    public final Map<String, String> query;
    public final Map<String, Object> body;
    public final List<String> pathVars;
    public final String token;
    public Session session;
    public User user;

    public Ctx(String method, String path, Map<String, String> query, Map<String, Object> body, List<String> pathVars, String token) {
        this.method = method;
        this.path = path;
        this.query = query;
        this.body = body;
        this.pathVars = pathVars;
        this.token = token;
    }

    /** Value from the JSON body, falling back to the query string. */
    public String str(String name) {
        Object v = body.get(name);
        if (v == null) return query.get(name);
        return v.toString();
    }

    public int intOf(String name, int def) {
        String s = str(name);
        if (s == null || s.isBlank()) return def;
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { throw AppException.badRequest("Invalid number for '" + name + "'").field(name); }
    }

    public Integer integer(String name) {
        String s = str(name);
        if (s == null || s.isBlank()) return null;
        try { return (int) Double.parseDouble(s.trim()); }
        catch (NumberFormatException e) { throw AppException.badRequest("Invalid number for '" + name + "'").field(name); }
    }

    public Double decimal(String name) {
        String s = str(name);
        if (s == null || s.isBlank()) return null;
        try { return Double.parseDouble(s.trim()); }
        catch (NumberFormatException e) { throw AppException.badRequest("Invalid number for '" + name + "'").field(name); }
    }

    public List<String> strings(String name) {
        Object v = body.get(name);
        if (v instanceof List<?> l) return l.stream().filter(x -> x != null).map(Object::toString).toList();
        if (v instanceof String s && !s.isBlank()) return List.of(s);
        return List.of();
    }

    public int pathInt(int index) {
        try { return Integer.parseInt(pathVars.get(index)); }
        catch (NumberFormatException e) { throw AppException.badRequest("Invalid id in the URL"); }
    }
}
