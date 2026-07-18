package dk.dtu.update;

import javafx.application.Platform;

import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Path;

/**
 * UI-agnostic orchestration for the update actions shared by the launch banner
 * and the Settings dialog. Everything here is best-effort and never throws to
 * the caller; UI reactions are delivered on the JavaFX thread via Runnable
 * callbacks.
 */
public final class UpdateFlow {

    private UpdateFlow() {
    }

    /**
     * Runs the download-and-install flow for the given release on a background
     * thread. On success the app exits so the installer is not blocked. On any
     * failure the release page is opened as a fallback and {@code onFailed} runs
     * on the JavaFX thread so the UI can re-enable its controls.
     *
     * @param release  the release to install (must have an installable asset)
     * @param onFailed  UI callback (JavaFX thread) invoked when the flow could
     *                  not complete; may be null
     */
    public static void downloadAndInstall(ReleaseInfo release, Runnable onFailed) {
        if (release == null || !release.hasInstallableAsset()) {
            openReleasesPage(release == null ? null : release.releasePageUrl());
            runOnFx(onFailed);
            return;
        }

        Thread worker = new Thread(() -> {
            UpdateChecker checker = new UpdateChecker();
            Path file = checker.download(release.assetUrl());
            if (file == null || !checker.launchInstaller(file)) {
                openReleasesPage(release.releasePageUrl());
                runOnFx(onFailed);
                return;
            }
            // Installer started: quit so it is not blocked by a running client.
            Platform.exit();
            System.exit(0);
        }, "update-download");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Opens the given URL in the system browser. Guarded and silent on failure.
     */
    public static void openReleasesPage(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception ignored) {
            // Best-effort: nothing else we can do from here.
        }
    }

    private static void runOnFx(Runnable r) {
        if (r != null) {
            Platform.runLater(r);
        }
    }
}
