package vn.uth.lms;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.uth.lms.util.Json;
import vn.uth.lms.util.PasswordHasher;
import vn.uth.lms.util.TextUtil;

class UtilTest {
    static { System.setProperty("lms.pbkdf2.iterations", "1000"); }

    @Test
    void passwordIsHashedWithRandomSaltAndVerified() {
        String a = PasswordHasher.hash("123456");
        String b = PasswordHasher.hash("123456");
        assertFalse(a.contains("123456"));
        assertFalse(a.equals(b));
        assertTrue(PasswordHasher.verify("123456", a));
        assertFalse(PasswordHasher.verify("1234567", a));
        assertFalse(PasswordHasher.verify(null, a));
    }

    @Test
    void randomPasswordHasRequestedLength() {
        assertEquals(10, PasswordHasher.randomPassword(10).length());
        assertFalse(PasswordHasher.randomPassword(10).equals(PasswordHasher.randomPassword(10)));
    }

    @Test
    void normalizeRemovesAccentsAndCase() {
        assertEquals("lap trinh java", TextUtil.normalize("Lập Trình JAVA"));
        assertEquals("duong dinh", TextUtil.normalize("Đường Đình"));
        assertEquals("", TextUtil.normalize(null));
    }

    @Test
    void isbnKeyIgnoresHyphens() {
        assertEquals("9780132350884", TextUtil.isbnKey("978-0-13-235088-4"));
    }

    @Test
    void emailAndPhoneValidation() {
        assertTrue(TextUtil.isValidEmail("vana@gmail.com"));
        assertFalse(TextUtil.isValidEmail("vana.gmail.com"));
        assertTrue(TextUtil.isValidPhone("0901234567"));
        assertTrue(TextUtil.isValidPhone("+84901234567"));
        assertFalse(TextUtil.isValidPhone("09ABC12345"));
        assertFalse(TextUtil.isValidPhone("123"));
    }

    @Test
    void jsonRoundTrip() {
        Map<String, Object> m = Json.parseObject("{\"a\":1,\"b\":[true,null,\"x\\ny\"],\"c\":{\"d\":2.5},\"u\":\"\\u00e9\"}");
        assertEquals(1L, m.get("a"));
        assertEquals(3, ((List<?>) m.get("b")).size());
        assertEquals("é", m.get("u"));
        String out = Json.write(m);
        assertEquals(m, Json.parseObject(out));
    }

    @Test
    void jsonEscapesSpecialCharacters() {
        assertEquals("\"a\\\"b\\\\c\\n\"", Json.write("a\"b\\c\n"));
        assertEquals("[1,2.5,\"x\"]", Json.write(List.of(1, 2.5, "x")));
        Map<String, Object> nullValue = new java.util.LinkedHashMap<>();
        nullValue.put("k", null);
        assertEquals("{\"k\":null}", Json.write(nullValue));
    }

    @Test
    void jsonRejectsBrokenInput() {
        assertThrows(IllegalArgumentException.class, () -> Json.parseObject("{\"a\":"));
        assertThrows(IllegalArgumentException.class, () -> Json.parseObject("[1,2]"));
    }
}
