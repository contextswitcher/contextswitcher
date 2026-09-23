package com.contextswitcher.ui;

import java.io.InputStream;
import java.util.Objects;

import javafx.scene.image.Image;

/// The app's target-rings mark — the same icon the browser extension shows on
/// its toolbar button.
///
/// Loaded, not drawn: JavaFX only accepts a stage icon whose pixels are
/// byte-based (`WindowStage.findBestImage` takes `BYTE_RGB`, `BYTE_BGRA_PRE`
/// or `BYTE_GRAY` and silently skips everything else), and a canvas snapshot
/// is `INT_ARGB_PRE` — so the drawn mark was dropped on the floor and the
/// window kept the toolkit's default icon (2026-09-09, 2026-09-12).
///
/// The file is the Chrome extension's `icon-128.png`, copied into the jar by
/// `processResources`, so there is still exactly one raster of the mark.
// [impl->dsn~app-icon~4]
public final class AppIcon {

    private AppIcon() {
    }

    /// The mark as a square image of `size` pixels, transparent outside the
    /// outer ring.
    public static Image image(int size) {
        InputStream png = Objects.requireNonNull(AppIcon.class.getResourceAsStream("icon.png"));
        return new Image(png, size, size, true, true);
    }
}
