package com.contextswitcher.ui;

/// Maps a count (of queued messages) to a single Unicode negative-circled
/// number glyph: `➊`…`➓` for 1–10 (sans-serif negative circled,
/// U+278A–U+2793), `⓫`…`⓴` for 11–20 (U+24EB–U+24F4). The count is capped at
/// 20 — a longer queue is rare and reading the exact number past 20 is not
/// worth it, so 20 reads as "at least twenty". A non-positive count yields the
/// empty string, so a task with nothing queued shows no badge at all.
// [impl->dsn~message-queue-count-badge~1]
public final class CircledCount {

    private CircledCount() {
    }

    public static String glyph(int count) {
        if (count <= 0) {
            return "";
        }
        int n = Math.min(count, 20);
        // 1–10 live in the Dingbats block (0x278A = ➊), 11–20 in Enclosed
        // Alphanumerics (0x24EB = ⓫); both single BMP chars.
        int codePoint = n <= 10 ? 0x2789 + n : 0x24E0 + n;
        return String.valueOf((char) codePoint);
    }
}
