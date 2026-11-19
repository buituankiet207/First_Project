package vn.uth.lms.web;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Maps "METHOD /path/{var}" to a handler and the access level it needs. */
public class Router {
    public static final class Route {
        final String method;
        final Pattern pattern;
        final Access access;
        final Handler handler;

        Route(String method, Pattern pattern, Access access, Handler handler) {
            this.method = method;
            this.pattern = pattern;
            this.access = access;
            this.handler = handler;
        }
    }

    public static final class Match {
        public final Route route;
        public final List<String> vars;
        Match(Route route, List<String> vars) { this.route = route; this.vars = vars; }
        public Access access() { return route.access; }
        public Handler handler() { return route.handler; }
    }

    private final List<Route> routes = new ArrayList<>();

    public void add(String method, String pattern, Access access, Handler handler) {
        String regex = pattern.replaceAll("\\{[a-zA-Z]+}", "([^/]+)");
        routes.add(new Route(method, Pattern.compile("^" + regex + "$"), access, handler));
    }

    public void get(String p, Access a, Handler h) { add("GET", p, a, h); }
    public void post(String p, Access a, Handler h) { add("POST", p, a, h); }
    public void put(String p, Access a, Handler h) { add("PUT", p, a, h); }
    public void delete(String p, Access a, Handler h) { add("DELETE", p, a, h); }

    /** Returns the match, or null when no route exists for that path; methodAllowed is false when only the method is wrong. */
    public Match find(String method, String path) {
        for (Route r : routes) {
            Matcher m = r.pattern.matcher(path);
            if (m.matches() && r.method.equals(method)) {
                List<String> vars = new ArrayList<>();
                for (int i = 1; i <= m.groupCount(); i++) vars.add(m.group(i));
                return new Match(r, vars);
            }
        }
        return null;
    }

    public boolean pathExists(String path) {
        return routes.stream().anyMatch(r -> r.pattern.matcher(path).matches());
    }
}
