package vn.uth.lms.util;

/** Business or request error that is reported to the client with an HTTP status and a readable message. */
public class AppException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final int status;
    private String field;

    public AppException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() { return status; }

    /** Name of the input field the error belongs to (so the UI can highlight it). */
    public AppException field(String field) { this.field = field; return this; }

    public String getField() { return field; }

    public static AppException badRequest(String message) { return new AppException(400, message); }
    public static AppException unauthorized(String message) { return new AppException(401, message); }
    public static AppException forbidden(String message) { return new AppException(403, message); }
    public static AppException notFound(String message) { return new AppException(404, message); }
    public static AppException conflict(String message) { return new AppException(409, message); }
}
