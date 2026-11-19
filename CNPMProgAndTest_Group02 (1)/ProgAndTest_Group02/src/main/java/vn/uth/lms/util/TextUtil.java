package vn.uth.lms.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class TextUtil {
    private TextUtil() {}

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{9,12}$");

    public static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    public static String trim(String s) { return s == null ? null : s.trim(); }

    /** Lower-case, accent-free form used for searching ("Lập trình" -> "lap trinh"). */
    public static String normalize(String s) {
        if (s == null) return "";
        String t = Normalizer.normalize(s.trim(), Normalizer.Form.NFD);
        t = MARKS.matcher(t).replaceAll("").replace('đ', 'd').replace('Đ', 'D');
        return t.toLowerCase(Locale.ROOT);
    }

    /** ISBN comparison key: no hyphens or spaces, lower case. */
    public static String isbnKey(String s) {
        return s == null ? "" : s.replace("-", "").replace(" ", "").toLowerCase(Locale.ROOT);
    }

    public static boolean isValidEmail(String s) { return s != null && EMAIL.matcher(s.trim()).matches(); }

    public static boolean isValidPhone(String s) { return s != null && PHONE.matcher(s.trim()).matches(); }
}
