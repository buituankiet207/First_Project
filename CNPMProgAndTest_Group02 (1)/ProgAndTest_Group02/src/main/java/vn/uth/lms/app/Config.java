package vn.uth.lms.app;

/** Runtime configuration read from environment variables. */
public final class Config {
    public final int port = Integer.parseInt(env("PORT", "8080"));
    /** "memory" (demo, no database) or "mysql". */
    public final String storage = env("STORAGE", "mysql").toLowerCase();
    public final String dbUrl = env("DB_URL", "jdbc:mysql://localhost:3306/library?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh");
    public final String dbUser = env("DB_USER", "library");
    public final String dbPassword = env("DB_PASSWORD", "library");
    public final boolean seed = Boolean.parseBoolean(env("SEED", "true"));
    public final boolean devMode = Boolean.parseBoolean(env("DEV_MODE", "false"));

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }
}
