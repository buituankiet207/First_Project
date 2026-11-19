package vn.uth.lms.web;

import java.nio.charset.StandardCharsets;

/** A non-JSON response body (e.g. a CSV download). */
public class Raw {
    public final String contentType;
    public final byte[] body;
    public final String filename;

    public Raw(String contentType, String text, String filename) {
        this.contentType = contentType;
        this.body = text.getBytes(StandardCharsets.UTF_8);
        this.filename = filename;
    }
}
