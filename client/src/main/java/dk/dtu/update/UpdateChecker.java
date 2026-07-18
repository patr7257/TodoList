package dk.dtu.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Optional;

/**
 * Checks the public GitHub Releases API for a newer client build and, when the
 * user asks, downloads and launches the matching platform installer.
 *
 * Every method is best-effort: network and file failures are swallowed and
 * turned into an empty Optional or a null/false result so nothing ever throws
 * back into the JavaFX UI. Callers fall back to opening the releases page.
 *
 * All calls here block on IO, so they MUST run off the JavaFX application thread.
 */
public class UpdateChecker {

    /** Owner/repo slug. The leading hyphen in the repo name is intentional. */
    private static final String REPO = "patr7257/-todolist-management-system";

    private static final String LATEST_RELEASE_URL =
            "https://api.github.com/repos/" + REPO + "/releases/latest";

    private static final String WINDOWS_ASSET = "TodoList-Client-Windows.msi";
    private static final String MACOS_ASSET = "TodoList-Client-macOS.dmg";

    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(TIMEOUT)
            .build();

    /**
     * Returns the latest release only when it is strictly newer than the running
     * version. Returns empty when the check fails, when we are already current,
     * or when the running version is "dev"/unparseable (so we never nag in dev).
     */
    public Optional<ReleaseInfo> findNewerRelease() {
        int[] current = parseVersion(AppVersion.current());
        if (current == null) {
            // "dev" or an unparseable version: stay quiet.
            return Optional.empty();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(LATEST_RELEASE_URL))
                    .header("User-Agent", "TodoList-Client")
                    .header("Accept", "application/vnd.github+json")
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!root.has("tag_name") || root.get("tag_name").isJsonNull()) {
                return Optional.empty();
            }

            String tag = root.get("tag_name").getAsString();
            int[] latest = parseVersion(tag);
            if (latest == null || !isNewer(latest, current)) {
                return Optional.empty();
            }

            String releasePageUrl = root.has("html_url") && !root.get("html_url").isJsonNull()
                    ? root.get("html_url").getAsString()
                    : "https://github.com/" + REPO + "/releases/latest";

            String assetUrl = findPlatformAssetUrl(root);

            String version = stripLeadingV(tag);
            return Optional.of(new ReleaseInfo(version, releasePageUrl, assetUrl));
        } catch (Exception e) {
            // Best-effort: no update surfaced on any failure.
            return Optional.empty();
        }
    }

    /**
     * Streams the given asset URL to a temp file and returns its path, or null
     * on any failure.
     */
    public Path download(String assetUrl) {
        if (assetUrl == null || assetUrl.isBlank()) {
            return null;
        }
        try {
            String suffix = assetUrl.toLowerCase().endsWith(".dmg") ? ".dmg" : ".msi";
            Path target = Files.createTempFile("todolist-update-", suffix);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(assetUrl))
                    .header("User-Agent", "TodoList-Client")
                    .header("Accept", "application/octet-stream")
                    .timeout(Duration.ofMinutes(5))
                    .GET()
                    .build();

            HttpResponse<Path> response = http.send(
                    request,
                    HttpResponse.BodyHandlers.ofFile(
                            target, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING));

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            return response.body();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Launches the platform installer for the downloaded file without waiting.
     * Windows uses msiexec, macOS uses "open" (which mounts the DMG so the user
     * can drag the app into Applications). Returns true when the process started.
     */
    public boolean launchInstaller(Path file) {
        if (file == null) {
            return false;
        }
        try {
            ProcessBuilder pb = isWindows()
                    ? new ProcessBuilder("msiexec", "/i", file.toString())
                    : new ProcessBuilder("open", file.toString());
            pb.start();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Selects the asset whose name matches this platform, or null when absent.
    private String findPlatformAssetUrl(JsonObject release) {
        if (!release.has("assets") || !release.get("assets").isJsonArray()) {
            return null;
        }
        String wanted = isWindows() ? WINDOWS_ASSET : MACOS_ASSET;
        JsonArray assets = release.getAsJsonArray("assets");
        for (JsonElement el : assets) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject asset = el.getAsJsonObject();
            if (!asset.has("name") || asset.get("name").isJsonNull()) {
                continue;
            }
            if (wanted.equals(asset.get("name").getAsString())
                    && asset.has("browser_download_url")
                    && !asset.get("browser_download_url").isJsonNull()) {
                return asset.get("browser_download_url").getAsString();
            }
        }
        return null;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static String stripLeadingV(String tag) {
        String t = tag.trim();
        if (t.startsWith("v") || t.startsWith("V")) {
            return t.substring(1);
        }
        return t;
    }

    /**
     * Parses a version like "v1.2.3" or "1.2.3" into an int triple. Returns null
     * when it does not match the numeric X.Y.Z shape (e.g. "dev").
     */
    private static int[] parseVersion(String raw) {
        if (raw == null) {
            return null;
        }
        String v = stripLeadingV(raw);
        String[] parts = v.split("\\.");
        if (parts.length != 3) {
            return null;
        }
        try {
            int[] out = new int[3];
            for (int i = 0; i < 3; i++) {
                out[i] = Integer.parseInt(parts[i].trim());
                if (out[i] < 0) {
                    return null;
                }
            }
            return out;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isNewer(int[] candidate, int[] current) {
        for (int i = 0; i < 3; i++) {
            if (candidate[i] != current[i]) {
                return candidate[i] > current[i];
            }
        }
        return false;
    }
}
