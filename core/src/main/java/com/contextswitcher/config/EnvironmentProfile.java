package com.contextswitcher.config;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/// One named entry of `settings.yaml`'s `profiles:` — how the machine itself
/// should look in a situation (lecture hall, screen share, desk work): the
/// screens' `resolution`, the `displays` topology, whether the `taskbar`
/// auto-hides, and whether the browser's `bookmarksBar` shows.
/// Every key is optional; an absent one (null, an empty `resolution` map) is
/// "not this profile's business" and is filled from the layer below ([#over]).
///
/// `resolution` maps a screen selector to its mode: a monitor's EDID name
/// (`DELL U4025QW`) or product code (`DEL4308`), `primary`, `internal` (the
/// laptop panel) or `external` (any other screen — the lecture-hall
/// projector), case-insensitive. The scalar form `resolution: 1920x1080` is
/// short for `{primary: 1920x1080}`. Display numbers are deliberately not a
/// selector: Windows renumbers them on docking and hot-plug.
// [impl->dsn~environment-profiles~1]
public record EnvironmentProfile(
        Map<String, Resolution> resolution,
        @Nullable Displays displays,
        @Nullable Taskbar taskbar,
        @Nullable BookmarksBar bookmarksBar) {

    public static final EnvironmentProfile EMPTY = new EnvironmentProfile(Map.of(), null, null, null);

    /// The selector the scalar `resolution:` form stands for.
    public static final String PRIMARY = "primary";

    public EnvironmentProfile {
        resolution = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(resolution));
    }

    /// A screen mode, `<width>x<height>` in the YAML.
    public record Resolution(int width, int height) {

        private static final Pattern FORM = Pattern.compile("\\s*(\\d{3,5})\\s*[xX×]\\s*(\\d{3,5})\\s*");

        /// The `resolution:` value, either form; entries that do not parse
        /// are dropped.
        static Map<String, Resolution> parseAll(@Nullable Object value) {
            Map<String, Resolution> result = new LinkedHashMap<>();
            if (value instanceof Map<?, ?> map) {
                map.forEach((screen, mode) -> {
                    Resolution r = parse(mode);
                    if (screen != null && !screen.toString().isBlank() && r != null) {
                        result.put(screen.toString().strip(), r);
                    }
                });
            } else {
                Resolution r = parse(value);
                if (r != null) {
                    result.put(PRIMARY, r);
                }
            }
            return result;
        }

        static @Nullable Resolution parse(@Nullable Object value) {
            if (value == null) {
                return null;
            }
            Matcher m = FORM.matcher(value.toString());
            return m.matches()
                    ? new Resolution(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)))
                    : null;
        }

        @Override
        public String toString() {
            return width + "x" + height;
        }
    }

    /// The Windows projection modes — `DisplaySwitch.exe`'s four switches
    /// (Win+P): PC screen only, duplicate, extend, second screen only.
    public enum Displays {
        INTERNAL, CLONE, EXTEND, EXTERNAL;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Taskbar {
        VISIBLE, AUTOHIDE;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum BookmarksBar {
        VISIBLE, HIDDEN;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /// Reads one profile's mapping leniently: an unknown or malformed value
    /// reads as absent, like the rest of a hand-edited config — a typo in one
    /// key must not take the whole profile down.
    static EnvironmentProfile fromYaml(Map<?, ?> map) {
        return new EnvironmentProfile(
                Resolution.parseAll(map.get("resolution")),
                enumOf(Displays.class, map.get("displays")),
                enumOf(Taskbar.class, map.get("taskbar")),
                enumOf(BookmarksBar.class, map.get("bookmarksBar")));
    }

    private static <E extends Enum<E>> @Nullable E enumOf(Class<E> type, @Nullable Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.toString().strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /// This profile layered over `base`: each key this profile sets wins,
    /// every other key comes from `base` — per key, so an override that only
    /// hides the bookmarks bar keeps the category's resolution. `resolution`
    /// merges per screen selector: a lecture profile setting `external` keeps
    /// the default's `internal`.
    public EnvironmentProfile over(EnvironmentProfile base) {
        Map<String, Resolution> screens = new LinkedHashMap<>(base.resolution);
        resolution.forEach((screen, mode) -> {
            screens.keySet().removeIf(key -> key.equalsIgnoreCase(screen));
            screens.put(screen, mode);
        });
        return new EnvironmentProfile(
                screens,
                displays != null ? displays : base.displays,
                taskbar != null ? taskbar : base.taskbar,
                bookmarksBar != null ? bookmarksBar : base.bookmarksBar);
    }

    public boolean isEmpty() {
        return resolution.isEmpty() && displays == null && taskbar == null && bookmarksBar == null;
    }
}
