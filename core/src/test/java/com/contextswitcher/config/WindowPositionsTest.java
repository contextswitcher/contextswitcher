package com.contextswitcher.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Verifies the per-desktop window geometry store: entries round-trip through
/// the properties file, a malformed or absent entry reads as "nothing
/// remembered", and a removed entry (off-screen after a monitor change) stays
/// gone across a reload.
// [utest->dsn~window-position-per-desktop~2]
class WindowPositionsTest {

    @Test
    void roundTripsThroughTheFile(@TempDir Path dir) {
        new WindowPositions(dir).put("JabRef",
                new WindowPositions.Geometry(10.4, -20.6, 800, 600));
        // A fresh instance reads what the first one wrote, rounded to pixels.
        assertThat(new WindowPositions(dir).get("JabRef"))
                .isEqualTo(new WindowPositions.Geometry(10, -21, 800, 600));
    }

    @Test
    void unknownDesktopHasNoPosition(@TempDir Path dir) {
        assertThat(new WindowPositions(dir).get("Mail")).isNull();
    }

    @Test
    void malformedEntryReadsAsAbsent(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("window-positions.properties"),
                "Mail=10,20\nBroken=a,b,c,d\n");
        WindowPositions positions = new WindowPositions(dir);
        assertThat(positions.get("Mail")).isNull();
        assertThat(positions.get("Broken")).isNull();
    }

    @Test
    void removedEntryStaysGoneAcrossReload(@TempDir Path dir) {
        WindowPositions positions = new WindowPositions(dir);
        positions.put("JabRef", new WindowPositions.Geometry(1, 2, 3, 4));
        positions.remove("JabRef");
        assertThat(positions.get("JabRef")).isNull();
        assertThat(new WindowPositions(dir).get("JabRef")).isNull();
    }

    @Test
    void desktopsAreIndependent(@TempDir Path dir) {
        WindowPositions positions = new WindowPositions(dir);
        positions.put("JabRef", new WindowPositions.Geometry(0, 0, 800, 600));
        positions.put("Mail", new WindowPositions.Geometry(1920, 0, 1000, 700));
        assertThat(positions.get("JabRef")).isEqualTo(new WindowPositions.Geometry(0, 0, 800, 600));
        assertThat(positions.get("Mail")).isEqualTo(new WindowPositions.Geometry(1920, 0, 1000, 700));
    }
}
