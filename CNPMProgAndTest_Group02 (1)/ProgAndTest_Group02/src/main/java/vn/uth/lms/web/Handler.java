package vn.uth.lms.web;

@FunctionalInterface
public interface Handler {
    /** Returns a Map / List (sent as JSON), a {@link Raw}, or null (sent as {"ok":true}). */
    Object handle(Ctx ctx) throws Exception;
}
