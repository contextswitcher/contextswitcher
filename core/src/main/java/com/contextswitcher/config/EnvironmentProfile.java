package com.contextswitcher.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/// One named entry of `settings.yaml`'s `profiles:` — how the machine itself
/// should look in a situation (lecture hall, screen share, desk work): the
/// `screens`' resolution and scale, the `displays` topology, whether the
/// `taskbar` auto-hides, and whether the browser's `bookmarksBar` shows.
/// Every key is optional; an absent one (null, an empty `screens` map) is
/// "not this profile's business" and is filled from the layer below ([#over]).
///
/// `screens` maps a screen selector to a [Screen]: a monitor's EDID name
/// (`DELL U4025QW`) or product code (`DEL4308`), `primary`, `internal` (the
/// laptop panel) or `external` (any other screen — the lecture-hall
/// projector), case-insensitive. Display numbers are deliberately not a
/// selector: Windows renumbers them on docking and hot-plug.
// [impl->dsn~environment-profiles~1]
public record EnvironmentProfile(
        Map<String, Screen> screens,
        @Nullable Displays displays,
        @Nullable Taskbar taskbar,
        @Nullable BookmarksBar bookmarksBar) {

    public static final EnvironmentProfile EMPTY = new EnvironmentProfile(Map.of(), null, null, null);

    public EnvironmentProfile {
        screens = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(screens));
    }

    /// A screen mode, `<width>x<height>` in the YAML.
    public record Resolution(int width, int height) {

        private static final Pattern FORM = Pattern.compile("\\s*(\\d{3,5})\\s*[xX×]\\s*(\\d{3,5})\\s*");

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

    /// One `screens:` entry: `resolution: <w>x<h>` (null: the mode is left
    /// alone) and `scale: <percent>`, Windows' per-screen "Scale" setting —
    /// 100 when left out, so a rule always pins the zoom and a projector is
    /// not left at whatever it was last given.
    public record Screen(@Nullable Resolution resolution, int scale) {

        /// The percentages Windows' "Scale" setting offers, in order.
        public static final List<Integer> SCALES = List.of(100, 125, 150, 175, 200, 225, 250, 300, 350, 400, 450, 500);

        private static final Pattern PERCENT = Pattern.compile("\\s*(\\d{3})\\s*%?\\s*");

        public Screen(Resolution resolution) {
            this(resolution, 100);
        }

        /// The `screens:` value; an entry with a malformed `resolution` or
        /// `scale`, or with neither, is dropped whole — a typo must not turn
        /// into a scale-only or 100% rule.
        static Map<String, Screen> parseAll(@Nullable Object value) {
            Map<String, Screen> result = new LinkedHashMap<>();
            if (value instanceof Map<?, ?> map) {
                map.forEach((selector, entry) -> {
                    Screen screen = entry instanceof Map<?, ?> keys ? parse(keys) : null;
                    if (selector != null && !selector.toString().isBlank() && screen != null) {
                        result.put(selector.toString().strip(), screen);
                    }
                });
            }
            return result;
        }

        private static @Nullable Screen parse(Map<?, ?> keys) {
            Object mode = keys.get("resolution");
            Object percent = keys.get("scale");
            if (mode == null && percent == null) {
                return null;
            }
            Resolution resolution = Resolution.parse(mode);
            if (mode != null && resolution == null) {
                return null;
            }
            int scale = 100;
            if (percent != null) {
                Matcher m = PERCENT.matcher(percent.toString());
                if (!m.matches() || !SCALES.contains(Integer.parseInt(m.group(1)))) {
                    return null;
                }
                scale = Integer.parseInt(m.group(1));
            }
            return new Screen(resolution, scale);
        }

        @Override
        public String toString() {
            return (resolution == null ? "" : resolution + " ") + scale + "%";
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
                Screen.parseAll(map.get("screens")),
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
    /// hides the bookmarks bar keeps the category's screens. `screens`
    /// merges per selector: a lecture profile setting `external` keeps the
    /// default's `internal`.
    public EnvironmentProfile over(EnvironmentProfile base) {
        Map<String, Screen> merged = new LinkedHashMap<>(base.screens);
        screens.forEach((selector, screen) -> {
            merged.keySet().removeIf(key -> key.equalsIgnoreCase(selector));
            merged.put(selector, screen);
        });
        return new EnvironmentProfile(
                merged,
                displays != null ? displays : base.displays,
                taskbar != null ? taskbar : base.taskbar,
                bookmarksBar != null ? bookmarksBar : base.bookmarksBar);
    }

    public boolean isEmpty() {
        return screens.isEmpty() && displays == null && taskbar == null && bookmarksBar == null;
    }
}
