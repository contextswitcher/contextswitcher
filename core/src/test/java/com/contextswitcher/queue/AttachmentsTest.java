package com.contextswitcher.queue;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~message-queue-send~5]
class AttachmentsTest {

    private final Path attachments = Path.of("/home/user/.contextswitcher/attachments");

    // [utest->dsn~message-queue-ui~26]
    @Test
    void makesAScreenshotWithZeroAlphaEverywhereOpaque() {
        int[] pixels = {0x00123456, 0x00FFFFFF, 0x00000000};

        Attachments.opaqueIfFullyTransparent(pixels);

        assertThat(pixels).containsExactly(0xFF123456, 0xFFFFFFFF, 0xFF000000);
    }

    // [utest->dsn~message-queue-ui~26]
    @Test
    void leavesARealTranslucentImageAlone() {
        int[] pixels = {0x00123456, 0x80FFFFFF};

        Attachments.opaqueIfFullyTransparent(pixels);

        assertThat(pixels).containsExactly(0x00123456, 0x80FFFFFF);
    }

    @Test
    void findsOnlyMarkersPointingIntoTheAttachmentsDir() {
        Path ours = attachments.resolve("img-1.png");
        String text = "see " + Attachments.marker(ours)
                + " but not [image: /somewhere/else.png] nor plain text";

        assertThat(Attachments.localRefs(text, attachments)).containsExactly(ours);
    }

    @Test
    void duplicateMarkersUploadOnce() {
        Path image = attachments.resolve("img-1.png");
        String text = Attachments.marker(image) + " and again " + Attachments.marker(image);

        assertThat(Attachments.localRefs(text, attachments)).hasSize(1);
    }

    @Test
    void findsAndRewritesFileMarkers() {
        Path file = attachments.resolve("20260717-101530-000-report.pdf");
        String text = "read " + Attachments.fileMarker(file) + " please";

        assertThat(Attachments.localRefs(text, attachments)).containsExactly(file);
        assertThat(Attachments.rewrite(text, Map.of(file, "/remote/report.pdf")))
                .isEqualTo("read `/remote/report.pdf` please");
    }

    /// What the queue boxes' hover tooltip asks for: every marker's path, in
    /// text order, wherever the file lives — hand-placed paths preview too.
    // [utest->dsn~attachment-image-hover~2]
    @Test
    void pathsCollectsEveryMarkerDistinctAndInOrder() {
        Path image = attachments.resolve("img-1.png");
        // The last marker holds a NUL: unparseable as a path on every OS, so
        // it is skipped instead of throwing out of the collection.
        String text = Attachments.marker(image) + " see also [file: /x/y.pdf], "
                + Attachments.marker(image) + " again, and [image: \0] which is no path";

        assertThat(Attachments.paths(text)).containsExactly(image, Path.of("/x/y.pdf"));
        assertThat(Attachments.paths("no markers here")).isEmpty();
    }

    // [utest->dsn~attachment-image-hover~2]
    @Test
    void pathAtHitsOnlyOffsetsInsideTheMarker() {
        Path image = attachments.resolve("img-1.png");
        String line = "see " + Attachments.marker(image) + " and [file: /x/y.pdf] done";
        int markerStart = line.indexOf('[');
        int markerEnd = line.indexOf(']');

        assertThat(Attachments.pathAt(line, markerStart - 1)).isNull();
        assertThat(Attachments.pathAt(line, markerStart)).isEqualTo(image);
        assertThat(Attachments.pathAt(line, markerEnd)).isEqualTo(image);
        assertThat(Attachments.pathAt(line, markerEnd + 1)).isNull();
        // The second marker on the line is found as well, file markers included.
        assertThat(Attachments.pathAt(line, line.indexOf("[file:") + 2))
                .isEqualTo(Path.of("/x/y.pdf"));
        assertThat(Attachments.pathAt("no markers here", 3)).isNull();
    }

    // [utest->dsn~attachment-image-hover~2]
    // [utest->dsn~terminal-file-links~1]
    @Test
    void uploadedRemotePathLeadsBackToTheLocalCopy() {
        String remote = "/export/home/u/.contextswitcher/attachments/img-20260914-135707-134.png";
        String line = "look at `" + remote + "` please";
        Path local = attachments.resolve("img-20260914-135707-134.png");

        assertThat(Attachments.pathAt(line, line.indexOf(remote) + 3, attachments)).isEqualTo(local);
        assertThat(Attachments.pathAt(line, line.indexOf('`'), attachments)).isNull();
        assertThat(Attachments.pathAt(line, 2, attachments)).isNull();
        assertThat(Attachments.localCopy(remote, attachments)).isEqualTo(local);
        assertThat(Attachments.localCopy("~/.contextswitcher/attachments/a.pdf", attachments))
                .isEqualTo(attachments.resolve("a.pdf"));
        assertThat(Attachments.localCopy("/tmp/img-1.png", attachments)).isNull();
    }

    @Test
    void rewriteReplacesTheWholeMarkerWithTheBacktickedRemotePath() {
        Path image = attachments.resolve("img-1.png");
        String text = "screenshot: " + Attachments.marker(image) + " shows the bug";

        String rewritten = Attachments.rewrite(text,
                Map.of(image, "/home/koppor/.contextswitcher/attachments/img-1.png"));

        // Backticks: a Markdown code span keeps a path with spaces one
        // token for the reading chat.
        assertThat(rewritten).isEqualTo(
                "screenshot: `/home/koppor/.contextswitcher/attachments/img-1.png` shows the bug");
    }
}
