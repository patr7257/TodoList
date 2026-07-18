package dk.dtu.update;

/**
 * Reports the version of the running client. jpackage bakes the value in via
 * the JVM option "-Dtodolist.version=X.Y.Z" (see build-installers.ps1 and the
 * build-installers workflow). When run from an IDE or a plain jar the property
 * is absent, so we report "dev" and the updater stays quiet.
 */
public final class AppVersion {

    private AppVersion() {
    }

    /**
     * The current client version, e.g. "1.2.3", or "dev" when unpackaged.
     */
    public static String current() {
        String v = System.getProperty("todolist.version", "dev");
        if (v == null || v.isBlank()) {
            return "dev";
        }
        return v.trim();
    }
}
