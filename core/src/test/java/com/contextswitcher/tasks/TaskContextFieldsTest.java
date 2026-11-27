package com.contextswitcher.tasks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~task-create-context-fields~1]
class TaskContextFieldsTest {

    private static final String WEB = "https://onedrive.live.com/view.aspx?resid=D7087D4BC5300FA4%21514904"
            + "&id=documents&wd=target%28Konkrete%20Veranstaltungen%2FNew%20Section%20Group%2FArbeiten"
            + "%20-%20laufend.one%7C154B32CE-2450-4C6E-ACF1-D0A5EEFC61F2%2FMA%20-%20SDev%20-%20Nikhil"
            + "%20Babu%20-%20Self-driven%20Software%7C666AC41B-1C96-448D-AF38-3DEF10279206%2F%29"
            + "&wdpartid={0860BAD7-224D-05CB-0130-426914792FD4}{1}"
            + "&wdsectionfileid=D7087D4BC5300FA4!seddea185e0c24bacb9f141e5f6b352cc&end";
    private static final String DESKTOP = "onenote:https://d.docs.live.net/D7087D4BC5300FA4/Dokumente/Lehre/"
            + "Konkrete%20Veranstaltungen/New%20Section%20Group/Arbeiten%20-%20laufend.one"
            + "#MA%20-%20SDev%20-%20Nikhil%20Babu%20-%20Self-driven%20Software"
            + "&section-id={154B32CE-2450-4C6E-ACF1-D0A5EEFC61F2}"
            + "&page-id={666AC41B-1C96-448D-AF38-3DEF10279206}&end";

    @Test
    void foldersAreOnePerLineUnquotedAndDeduplicated() {
        assertThat(TaskContextFields.folders("""
                "C:\\git\\jabref"
                  /data/koppor/notes

                C:\\git\\jabref
                """)).containsExactly("C:\\git\\jabref", "/data/koppor/notes");
        assertThat(TaskContextFields.folders(null)).isEmpty();
        assertThat(TaskContextFields.folders("  \n ")).isEmpty();
    }

    @Test
    void fileUrlsBecomePaths() {
        assertThat(TaskContextFields.fromFileUrl("file:///C:/Users/olive/OneDrive/Projects/publications/SG-2026"))
                .isEqualTo("C:\\Users\\olive\\OneDrive\\Projects\\publications\\SG-2026");
        assertThat(TaskContextFields.fromFileUrl("file:///C:/My%20Docs/a+b")).isEqualTo("C:\\My Docs\\a+b");
        assertThat(TaskContextFields.fromFileUrl("file:///c:")).isEqualTo("c:\\");
        assertThat(TaskContextFields.fromFileUrl("file:///data/notes")).isEqualTo("/data/notes");
        assertThat(TaskContextFields.fromFileUrl("file://localhost/data/notes")).isEqualTo("/data/notes");
        assertThat(TaskContextFields.fromFileUrl("file://server/share/x")).isEqualTo("\\\\server\\share\\x");
        assertThat(TaskContextFields.fromFileUrl("C:\\git")).isEqualTo("C:\\git");
        assertThat(TaskContextFields.folders("file:///data/notes\n/data/notes")).containsExactly("/data/notes");
    }

    @Test
    void noteKeepsTheDesktopLinkOfThePastedPair() {
        assertThat(TaskContextFields.note(WEB + "\n" + DESKTOP + "\n")).isEqualTo(DESKTOP);
        // A single-line field drops the newline: the two links glued together.
        assertThat(TaskContextFields.note(WEB + DESKTOP)).isEqualTo(DESKTOP);
        assertThat(TaskContextFields.note(DESKTOP)).isEqualTo(DESKTOP);
        assertThat(TaskContextFields.isWebOnlyNote(WEB + "\n" + DESKTOP)).isFalse();
    }

    @Test
    void aWebLinkAloneIsKeptAsIsAndFlagged() {
        assertThat(TaskContextFields.note("  " + WEB + "  ")).isEqualTo(WEB);
        assertThat(TaskContextFields.isWebOnlyNote(WEB)).isTrue();
        assertThat(TaskContextFields.isWebOnlyNote("https://example.org/wiki")).isFalse();
        assertThat(TaskContextFields.note(" ")).isNull();
        assertThat(TaskContextFields.isWebOnlyNote(null)).isFalse();
    }

    @Test
    void chatIsTheFirstLineOrNull() {
        assertThat(TaskContextFields.chat(" https://matrix.to/#/!room:matrix.org \n")).isEqualTo(
                "https://matrix.to/#/!room:matrix.org");
        assertThat(TaskContextFields.chat("")).isNull();
    }

    @Test
    void clipboardFolderTakesOneExistingAbsoluteDirectory(@TempDir Path dir) throws Exception {
        String path = dir.toString();
        assertThat(TaskContextFields.clipboardFolder(path)).isEqualTo(path);
        assertThat(TaskContextFields.clipboardFolder("  \"" + path + "\"\n")).isEqualTo(path);
        assertThat(TaskContextFields.clipboardFolder(null)).isNull();
        assertThat(TaskContextFields.clipboardFolder("relative/folder")).isNull();
        assertThat(TaskContextFields.clipboardFolder(path + "\n" + path)).isNull();
        Path file = Files.writeString(dir.resolve("f.txt"), "x");
        assertThat(TaskContextFields.clipboardFolder(file.toString())).isNull();
    }

    @Test
    void clipboardNoteTakesOnlyALinkOnlyClipboard() {
        assertThat(TaskContextFields.clipboardNote(WEB + "\r\n" + DESKTOP + "\r\n")).isEqualTo(DESKTOP);
        assertThat(TaskContextFields.clipboardNote(DESKTOP)).isEqualTo(DESKTOP);
        assertThat(TaskContextFields.clipboardNote(WEB)).isEqualTo(WEB);
        assertThat(TaskContextFields.clipboardNote("https://github.com/org/repo/pull/1")).isNull();
        assertThat(TaskContextFields.clipboardNote("Review the notes at " + DESKTOP)).isNull();
        assertThat(TaskContextFields.clipboardNote(null)).isNull();
    }

    @Test
    void foldersGoUnderTheHostScopedKey() {
        assertThat(TaskContextFields.foldersKey("DESKTOP-OK")).isEqualTo("folders-desktop-ok");
        assertThat(TaskContextFields.foldersKey("")).isEqualTo("folders");
    }

    @Test
    void applyWritesTheFilledFieldsAndTheyParseBack() throws Exception {
        String content = TaskFileParser.newTaskContent("Thesis", null, null, true);
        String applied = TaskContextFields.apply(content,
                List.of("C:\\Users\\me\\OneDrive - Uni\\Thesis", "/data/a, b"), WEB + DESKTOP,
                "https://matrix.to/#/!room:matrix.org", "devbox", List.of(), null, null);

        assertThat(applied).contains("folders-devbox:\n  - ").contains("note: ").contains("chat: ");
        Map<String, Object> folded = TaskFileParser.applyMachineVariants(
                Frontmatter.parse(applied), List.of("windows", "devbox"));
        assertThat(folded.get("folders")).isEqualTo(List.of("C:\\Users\\me\\OneDrive - Uni\\Thesis", "/data/a, b"));
        Task task = new TaskFileParser().parse("thesis", applied);
        assertThat(task.note()).isEqualTo(DESKTOP);
        assertThat(task.chat()).isEqualTo("https://matrix.to/#/!room:matrix.org");
    }

    @Test
    void emptyOrInheritedFieldsWriteNoKey() {
        String content = TaskFileParser.newTaskContent("Plain", null, null, false);
        assertThat(TaskContextFields.apply(content, List.of(), null, null, "devbox",
                List.of(), null, null)).isEqualTo(content);
        assertThat(TaskContextFields.apply(content, List.of("/p"), DESKTOP, "https://chat",
                "devbox", List.of("/p"), DESKTOP, "https://chat")).isEqualTo(content);
    }

    @Test
    void withBlockListReplacesAnExistingList() {
        String content = "---\ntitle: T\nfolders-devbox:\n  - /old\n  - /older\nstatus: active\n---\n";
        assertThat(TaskFileParser.withBlockList(content, "folders-devbox", List.of("/new")))
                .isEqualTo("---\ntitle: T\nfolders-devbox:\n  - /new\nstatus: active\n---\n");
    }
}
