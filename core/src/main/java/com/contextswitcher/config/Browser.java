package com.contextswitcher.config;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// The browser ContextSwitcher drives: the one its extension is installed in
/// and, on Windows, the one whose windows are raised, launched, and captured
/// for a complete-control desktop.
///
/// Only the *native* side needs to know which one it is — the extension itself
/// dials in over the loopback WebSocket and identifies itself, so both
/// extensions may be installed and either can connect. What is not
/// discoverable that way is the window class and the executable the
/// PowerShell helpers need, and that is the whole of this enum.
///
/// [#processName()] is checked alongside [#windowClass()] because a class name
/// alone is not the browser: `Chrome_WidgetWin_1` is every Chromium-based
/// window on the machine (VS Code, Slack, Electron apps), and closing one of
/// those on a complete-control suspend would throw away the user's work.
// [impl->dsn~browser-choice~2]
public enum Browser {

    FIREFOX("firefox", "Firefox", "MozillaWindowClass", "firefox", "-new-window"),
    CHROME("chrome", "Chrome", "Chrome_WidgetWin_1", "chrome", "--new-window");

    /// The `browser` value in `settings.yaml`.
    private final String key;
    private final String displayName;
    private final String windowClass;
    private final String processName;
    private final String newWindowFlag;

    Browser(String key, String displayName, String windowClass, String processName,
            String newWindowFlag) {
        this.key = key;
        this.displayName = displayName;
        this.windowClass = windowClass;
        this.processName = processName;
        this.newWindowFlag = newWindowFlag;
    }

    /// The valid `browser` values, for the settings form's drop-down.
    public static final List<String> KEYS = List.of(FIREFOX.key, CHROME.key);

    /// The default browser — the one ContextSwitcher shipped with first.
    public static final Browser DEFAULT = FIREFOX;

    /// The browser named by `value`, or [#DEFAULT] for anything unknown (an
    /// absent key, a hand-edited file, a value from a newer version).
    public static Browser of(@Nullable Object value) {
        if (value == null) {
            return DEFAULT;
        }
        String normalized = value.toString().strip().toLowerCase(Locale.ROOT);
        return KEYS.contains(normalized) ? valueOf(normalized.toUpperCase(Locale.ROOT)) : DEFAULT;
    }

    /// The browser `value` names exactly, or null when it names none — the
    /// tolerant [#of(Object)] falls back to Firefox, which is right for a
    /// hand-edited settings file and wrong for "which browser is on the other
    /// end of this socket", where not knowing must stay not knowing.
    // [impl->dsn~drive-the-connected-browser~1]
    public static @Nullable Browser named(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        return KEYS.contains(normalized) ? valueOf(normalized.toUpperCase(Locale.ROOT)) : null;
    }

    public String key() {
        return key;
    }

    /// The name to put in a user-facing message ("Chrome: focused existing tab").
    public String displayName() {
        return displayName;
    }

    /// The Win32 top-level window class the browser's windows carry.
    public String windowClass() {
        return windowClass;
    }

    /// The process image name (no extension), as `Get-Process` reports it —
    /// the second half of identifying a window as this browser's.
    public String processName() {
        return processName;
    }

    /// The command-line flag that makes the browser open a **new window**
    /// rather than a tab in the window it last used.
    public String newWindowFlag() {
        return newWindowFlag;
    }
}
