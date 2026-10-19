package com.contextswitcher.config;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/// One named entry of `settings.yaml`'s `profiles:` — how the machine itself
/// should look in a situation (lecture hall, screen share, desk work): the
/// screen `resolution`, the `displays` topology, whether the `taskbar`
/// auto-hides, and whether the browser's `bookmarksBar` shows.
/// Every key is optional; an absent one (null) is "not this profile's
/// business" and is filled from the layer below ([#over]).
// [impl->dsn~environment-profiles~1]
public record EnvironmentProfile(
        @Nullable Resolution resolution,
        @Nullable Displays displays,
        @Nullable Taskbar taskbar,
        @Nullable BookmarksBar bookmarksBar) {

    public static final EnvironmentProfile EMPTY = new EnvironmentProfile(null, null, null, null);

    /// A primary-display mode, `<width>x<height>` in the YAML.
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
                Resolution.parse(map.get("resolution")),
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
    /// hides the bookmarks bar keeps the category's resolution.
    public EnvironmentProfile over(EnvironmentProfile base) {
        return new EnvironmentProfile(
                resolution != null ? resolution : base.resolution,
                displays != null ? displays : base.displays,
                taskbar != null ? taskbar : base.taskbar,
                bookmarksBar != null ? bookmarksBar : base.bookmarksBar);
    }

    public boolean isEmpty() {
        return resolution == null && displays == null && taskbar == null && bookmarksBar == null;
    }
}
