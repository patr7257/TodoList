package dk.dtu.ui;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.W32APIOptions;
import javafx.stage.Stage;

/**
 * Native window chrome tweaks.
 *
 * The only thing here today: make the Windows title bar follow the app's
 * light/dark theme via the DWM "immersive dark mode" attribute, so a dark app
 * does not sit under a bright white OS title bar.
 *
 * Everything is a best-effort, silent no-op on failure and on non-Windows
 * platforms: title-bar coloring is pure cosmetics and must never crash the app.
 */
public final class WindowChrome {

    // DWMWA_USE_IMMERSIVE_DARK_MODE. 20 on Windows 10 build 18985+ and Windows 11.
    // 19 was the (undocumented) value on earlier Windows 10 20H1 builds; we try it
    // as a fallback when 20 fails.
    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE_OLD = 19;

    // SetWindowPos flags used only to nudge a non-client (title bar) redraw.
    private static final int SWP_NOSIZE = 0x0001;
    private static final int SWP_NOMOVE = 0x0002;
    private static final int SWP_NOZORDER = 0x0004;
    private static final int SWP_NOACTIVATE = 0x0010;
    private static final int SWP_FRAMECHANGED = 0x0020;

    private WindowChrome() {
    }

    /** Minimal JNA binding for the one dwmapi.dll call we need. */
    private interface DwmApi extends com.sun.jna.Library {
        DwmApi INSTANCE = isWindows()
                ? Native.load("dwmapi", DwmApi.class, W32APIOptions.DEFAULT_OPTIONS)
                : null;

        // HRESULT DwmSetWindowAttribute(HWND, DWORD attr, LPCVOID pvAttribute, DWORD cbAttribute)
        int DwmSetWindowAttribute(HWND hwnd, int dwAttribute, IntByReference pvAttribute, int cbAttribute);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /**
     * Apply (or remove) the dark native title bar for the given stage.
     *
     * Safe to call repeatedly, including after the stage title changes: the HWND
     * is (re)resolved from the current stage title each time.
     *
     * @param stage the primary stage whose native window should be recolored
     * @param dark  true to darken the title bar, false to restore the light one
     */
    public static void applyDarkTitleBar(Stage stage, boolean dark) {
        if (stage == null || !isWindows() || DwmApi.INSTANCE == null) {
            return; // non-Windows or dwmapi unavailable: cosmetics only, no-op.
        }
        try {
            HWND hwnd = findStageWindow(stage);
            if (hwnd == null) {
                return;
            }

            IntByReference value = new IntByReference(dark ? 1 : 0);

            // Try the modern attribute first, fall back to the older Win10 value.
            int hr = DwmApi.INSTANCE.DwmSetWindowAttribute(
                    hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, value, 4);
            if (hr != 0) {
                DwmApi.INSTANCE.DwmSetWindowAttribute(
                        hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_OLD, value, 4);
            }

            // Nudge a non-client redraw so the new title-bar color takes effect
            // immediately. SWP with FRAMECHANGED but no move/size/z-order change
            // is flicker-free (the window does not actually move or resize).
            User32.INSTANCE.SetWindowPos(hwnd, null, 0, 0, 0, 0,
                    SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE | SWP_FRAMECHANGED);
        } catch (Throwable t) {
            // Never let a cosmetic title-bar tweak take down the app.
            System.err.println("[WindowChrome] Could not apply dark title bar: " + t.getMessage());
        }
    }

    /**
     * Resolve the native window handle for the stage. Primary path is FindWindow
     * by the stage's current title (SceneNavigator keeps this in sync). If that
     * misses, fall back to the current foreground window.
     */
    private static HWND findStageWindow(Stage stage) {
        String title = stage.getTitle();
        HWND hwnd = null;
        if (title != null && !title.isBlank()) {
            hwnd = User32.INSTANCE.FindWindow(null, title);
        }
        if (hwnd == null) {
            hwnd = User32.INSTANCE.GetForegroundWindow();
        }
        return hwnd;
    }
}
