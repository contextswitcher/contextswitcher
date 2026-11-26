package com.contextswitcher.config;

import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/// The global hotkey that switches to the task of the window in front
/// (`switchByWindowHotkey:` in `settings.yaml`, `dsn~switch-by-window-hotkey~1`).
///
/// A combination is written as `+`-separated tokens, modifiers first, in any
/// case: `Win`, `Ctrl`, `Alt`, `Shift`, then one key — a letter, a digit or
/// `F1`…`F24` — e.g. `Win+Shift+F`. At least one modifier is required, since a
/// bare key registered system-wide would swallow that key in every app.
///
/// The default is `Win+Shift+F`: `Win+F` (what one would pick first) is taken
/// on Windows 11 — it opens Feedback Hub, and `RegisterHotKey` answers
/// `ERROR_HOTKEY_ALREADY_REGISTERED` (1409), as do `Win+Alt+F` and `Ctrl+Win+F`
/// (probe on build 26300, 2026-11-26). `Ctrl+Alt` is avoided on purpose: on a
/// German keyboard it is AltGr, which types `@`, `€`, `{` ….
// [impl->dsn~switch-by-window-hotkey~1]
public final class WindowHotkey {

    /// The `settings.yaml` key.
    public static final String SETTING = "switchByWindowHotkey";

    /// The combination used when the key is absent.
    public static final String DEFAULT = "Win+Shift+F";

    /// `RegisterHotKey`'s modifier flags.
    static final int MOD_ALT = 0x1;
    static final int MOD_CONTROL = 0x2;
    static final int MOD_SHIFT = 0x4;
    static final int MOD_WIN = 0x8;

    private WindowHotkey() {
    }

    /// A parsed combination: `RegisterHotKey`'s `modifiers` and virtual-key
    /// code, and its canonical `label` (`Win+Shift+F`) for messages.
    public record Hotkey(int modifiers, int virtualKey, String label) {
    }

    /// What `settings.yaml` asks for: the hotkey, off (`hotkey` null,
    /// `error` null), or a value that does not parse (`error` says why).
    public record Setting(@Nullable Hotkey hotkey, @Nullable String error) {
    }

    /// Reads `switchByWindowHotkey` from the settings text: absent → the
    /// [#DEFAULT]; `off`, `false` or empty → off.
    public static Setting fromSettings(String settingsYaml) {
        Map<?, ?> root;
        try {
            root = new Yaml(new SafeConstructor(new LoaderOptions())).load(settingsYaml) instanceof Map<?, ?> map
                    ? map : Map.of();
        } catch (RuntimeException e) {
            root = Map.of();
        }
        if (!root.containsKey(SETTING)) {
            return new Setting(parse(DEFAULT), null);
        }
        Object value = root.get(SETTING);
        if (value == null || Boolean.FALSE.equals(value) || value.toString().isBlank()
                || value.toString().strip().equalsIgnoreCase("off")) {
            return new Setting(null, null);
        }
        Hotkey hotkey = parse(value.toString());
        return hotkey != null ? new Setting(hotkey, null)
                : new Setting(null, "%s: \"%s\" is no key combination (e.g. %s)".formatted(SETTING, value, DEFAULT));
    }

    /// Parses `Win+Shift+F`-style text, or null when it names no modifier,
    /// no key, more than one key, or a token it does not know.
    public static @Nullable Hotkey parse(String text) {
        int modifiers = 0;
        int key = 0;
        String keyLabel = null;
        for (String raw : text.split("\\+")) {
            String token = raw.strip().toUpperCase(Locale.ROOT);
            int modifier = switch (token) {
                case "WIN", "WINDOWS", "SUPER", "META" -> MOD_WIN;
                case "CTRL", "CONTROL", "STRG" -> MOD_CONTROL;
                case "ALT" -> MOD_ALT;
                case "SHIFT" -> MOD_SHIFT;
                default -> 0;
            };
            if (modifier != 0) {
                modifiers |= modifier;
                continue;
            }
            int vk = virtualKey(token);
            if (vk == 0 || key != 0) {
                return null;
            }
            key = vk;
            keyLabel = token;
        }
        if (modifiers == 0 || key == 0) {
            return null;
        }
        StringBuilder label = new StringBuilder();
        if ((modifiers & MOD_WIN) != 0) {
            label.append("Win+");
        }
        if ((modifiers & MOD_CONTROL) != 0) {
            label.append("Ctrl+");
        }
        if ((modifiers & MOD_ALT) != 0) {
            label.append("Alt+");
        }
        if ((modifiers & MOD_SHIFT) != 0) {
            label.append("Shift+");
        }
        return new Hotkey(modifiers, key, label.append(keyLabel).toString());
    }

    /// The Windows virtual-key code of a letter, digit or `F1`…`F24`; 0 for
    /// anything else.
    static int virtualKey(String token) {
        if (token.length() == 1) {
            char c = token.charAt(0);
            return (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') ? c : 0;
        }
        if (token.matches("F([1-9]|1[0-9]|2[0-4])")) {
            return 0x70 + Integer.parseInt(token.substring(1)) - 1;
        }
        return 0;
    }

    /// The status-bar text for a failed registration: `RegisterHotKey`'s
    /// error code, spelled out for the one that matters; a negative code is
    /// a listener that did not start at all.
    public static String failure(String label, int errorCode) {
        String why = errorCode == 1409
                ? "already taken by Windows or another app"
                : errorCode < 0 ? "its listener did not start (see the log)"
                : "Windows error " + errorCode;
        return "Hotkey %s not registered: %s — set another one as %s in settings.yaml"
                .formatted(label, why, SETTING);
    }
}
