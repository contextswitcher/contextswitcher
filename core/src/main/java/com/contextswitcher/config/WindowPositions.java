package com.contextswitcher.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// Remembers the main window's geometry **per virtual desktop**, keyed by the
/// desktop's name, in `<configDir>/window-positions.properties` — the same idea
/// as git gui's geometry entry in its config, one entry per desktop
/// (MADR 0021). Each desktop is one property `name = x,y,width,height`
/// (rounded to whole pixels).
///
/// Best-effort by design: an unreadable or unwritable file only logs — losing
/// a remembered position must never take the window (or the desktop poll that
/// drives it) down. A malformed entry reads as "no position remembered".
/// The caller decides whether a stored geometry is still visible on the
/// current monitors and calls [#remove(String)] when it is not — the
/// visibility test needs `javafx.stage.Screen`, which this store deliberately
/// does not touch, so it stays a plain testable file-backed map.
// [impl->dsn~window-position-per-desktop~2]
public class WindowPositions {

    /// One remembered window geometry, in screen coordinates.
    public record Geometry(double x, double y, double width, double height) {
    }

    /// Reserved key for the geometry at last exit, restored at the next
    /// startup on any OS, desktop known or not — the per-desktop entries only
    /// take over once the (Windows-only) desktop poll reports a name.
    // ponytail: a virtual desktop literally named `!startup` would collide
    //   with this key; nobody names a desktop that, a prefix scheme when
    //   someone does.
    public static final String STARTUP = "!startup";

    private static final String FILE_NAME = "window-positions.properties";

    private final Path file;
    private final Properties entries = new Properties();

    public WindowPositions(Path configDir) {
        this.file = configDir.resolve(FILE_NAME);
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                entries.load(reader);
            } catch (IOException e) {
                Logger.warn("Cannot read {}: {}", file, e.getMessage());
            }
        }
    }

    /// The geometry remembered for `desktop`, or null when none is stored or
    /// the entry is malformed (a hand-edited file).
    public @Nullable Geometry get(String desktop) {
        String value = entries.getProperty(desktop);
        if (value == null) {
            return null;
        }
        String[] parts = value.split(",");
        if (parts.length != 4) {
            return null;
        }
        try {
            return new Geometry(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim()), Double.parseDouble(parts[3].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /// Remembers `geometry` for `desktop` and persists the file.
    public void put(String desktop, Geometry geometry) {
        entries.setProperty(desktop, "%d,%d,%d,%d".formatted(
                Math.round(geometry.x()), Math.round(geometry.y()),
                Math.round(geometry.width()), Math.round(geometry.height())));
        store();
    }

    /// Forgets `desktop`'s entry (its stored geometry is off every current
    /// screen — the monitor configuration changed) and persists the file.
    public void remove(String desktop) {
        if (entries.remove(desktop) != null) {
            store();
        }
    }

    private void store() {
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                entries.store(writer, "ContextSwitcher window geometry per virtual desktop");
            }
        } catch (IOException e) {
            Logger.warn("Cannot write {}: {}", file, e.getMessage());
        }
    }
}
