package com.contextswitcher.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// [utest->dsn~generated-file-download~2]
class RemoteFilesTest {

    // [utest->dsn~terminal-owned-session~3]
    @Test
    void listsLocalScratchpadFilesNewestFirst(@TempDir Path claudeTemp) throws IOException {
        Path scratchpad = Files.createDirectories(
                claudeTemp.resolve("C--work").resolve("uuid").resolve("scratchpad"));
        Path older = Files.writeString(scratchpad.resolve("older.txt"), "a");
        Path newer = Files.writeString(
                Files.createDirectories(scratchpad.resolve("sub")).resolve("newer.md"), "bb");
        // Below the remote find's -maxdepth 2, and outside any scratchpad: both
        // are left out, although they are the newest files of all.
        Files.writeString(Files.createDirectories(scratchpad.resolve("sub").resolve("deeper"))
                .resolve("too-deep.txt"), "c");
        Files.writeString(Files.createDirectories(claudeTemp.resolve("C--work").resolve("uuid")
                .resolve("other")).resolve("not-scratch.txt"), "d");
        Files.setLastModifiedTime(older, java.nio.file.attribute.FileTime.fromMillis(1_000_000));
        Files.setLastModifiedTime(newer, java.nio.file.attribute.FileTime.fromMillis(2_000_000));

        List<RemoteFiles.RemoteFile> files = RemoteFiles.listLocal(claudeTemp);

        assertEquals(List.of(newer.toAbsolutePath().toString(), older.toAbsolutePath().toString()),
                files.stream().map(RemoteFiles.RemoteFile::path).toList());
        assertEquals(2, files.get(0).size());
        assertEquals(2000, files.get(0).modified());
    }

    // [utest->dsn~terminal-owned-session~3]
    @Test
    void listsNothingWithoutALocalClaudeTempDirectory(@TempDir Path dir) {
        assertTrue(RemoteFiles.listLocal(dir.resolve("missing")).isEmpty());
    }

    @Test
    void listCommandCarriesNoDoubleQuote() {
        // Windows ssh.exe swallows double quotes (dsn~ssh-command-runner~6).
        assertTrue(RemoteFiles.listCommand().stream().noneMatch(part -> part.contains("\"")));
    }

    @Test
    void listCommandGlobsEveryScratchpadOfTheRemoteUser() {
        assertTrue(RemoteFiles.listCommand().contains("/tmp/claude-$(id -u)/*/*/scratchpad"));
    }

    @Test
    void listCommandLeavesTheEscapesForFindToExpand() {
        // Single-quoted through the transport, so find — not the shell — turns
        // the \t and \n into separators.
        assertTrue(RemoteFiles.listCommand().contains("'%T@\\t%s\\t%p\\n'"));
    }

    @Test
    void readCommandSingleQuotesThePath() {
        assertEquals(List.of("base64", "'/tmp/claude-1001/x/scratchpad/a file.txt'"),
                RemoteFiles.readCommand("/tmp/claude-1001/x/scratchpad/a file.txt"));
    }

    @Test
    void statCommandNamesOnlyThatPathAndCarriesNoDoubleQuote() {
        List<String> command = RemoteFiles.statCommand("/tmp/claude-1001/x/a file.txt");
        // -maxdepth 0 = the path itself, never a descent into a directory.
        assertEquals(List.of("find", "'/tmp/claude-1001/x/a file.txt'", "-maxdepth", "0",
                "-type", "f", "-printf", RemoteFiles.LIST_FORMAT, "2>/dev/null"), command);
        assertTrue(command.stream().noneMatch(part -> part.contains("\"")));
    }

    @Test
    void cleanPathStripsTheTildeTheQuotingWouldNotExpand() {
        // Single-quoted through the transport, so the remote shell never
        // expands a tilde — and the exec channel already starts in the home.
        assertEquals("notes/report.md", RemoteFiles.cleanPath("  ~/notes/report.md "));
        assertEquals("/tmp/x.txt", RemoteFiles.cleanPath("/tmp/x.txt"));
        assertEquals("relative.txt", RemoteFiles.cleanPath("relative.txt"));
    }

    @Test
    void cleanPathRefusesWhatTheQuotingCannotCarry() {
        assertNull(RemoteFiles.cleanPath("/tmp/it's.txt"));
        assertNull(RemoteFiles.cleanPath("   "));
        assertNull(RemoteFiles.cleanPath("~"));
    }

    @Test
    void parsesFindOutputNewestFirst() {
        List<RemoteFiles.RemoteFile> files = RemoteFiles.parseList("""
                1754563200.1234567890\t2150\t/tmp/claude-1001/-p/uuid/scratchpad/email.txt
                1754562000.0000000000\t8600\t/tmp/claude-1001/-p/uuid/scratchpad/sub/report.md
                """);
        assertEquals(2, files.size());
        assertEquals("/tmp/claude-1001/-p/uuid/scratchpad/email.txt", files.get(0).path());
        assertEquals(2150, files.get(0).size());
        assertEquals(1754563200L, files.get(0).modified());
        assertEquals("report.md", files.get(1).fileName());
    }

    @Test
    void skipsUnparsableAndQuotedLines() {
        List<RemoteFiles.RemoteFile> files = RemoteFiles.parseList("""
                find: no such file
                1754563200.0\tnot-a-number\t/tmp/a
                1754563200.0\t10\t/tmp/it's-quoted.txt
                1754563200.0\t10\t/tmp/fine.txt
                """);
        assertEquals(List.of("/tmp/fine.txt"), files.stream().map(RemoteFiles.RemoteFile::path)
                .toList());
    }

    @Test
    void fileNameIsReducedToPortableCharacters() {
        assertEquals("my_report_1.md",
                new RemoteFiles.RemoteFile("/tmp/x/my report:1.md", 1, 0).fileName());
        assertEquals("download", new RemoteFiles.RemoteFile("/tmp/x/..", 1, 0).fileName());
    }

    @Test
    void uniquePathNeverOverwritesAnEarlierDownload(@TempDir Path dir) throws IOException {
        assertEquals(dir.resolve("report.md"), RemoteFiles.uniquePath(dir, "report.md"));
        Files.createFile(dir.resolve("report.md"));
        assertEquals(dir.resolve("report-2.md"), RemoteFiles.uniquePath(dir, "report.md"));
        Files.createFile(dir.resolve("report-2.md"));
        assertEquals(dir.resolve("report-3.md"), RemoteFiles.uniquePath(dir, "report.md"));
        // A name without an extension keeps the suffix at the end.
        Files.createFile(dir.resolve("notes"));
        assertEquals(dir.resolve("notes-2"), RemoteFiles.uniquePath(dir, "notes"));
    }

    @Test
    void formatsSizeAndAgeForTheMenu() {
        assertEquals("512B", RemoteFiles.humanSize(512));
        assertEquals("2.1K", RemoteFiles.humanSize(2150));
        assertEquals("64K", RemoteFiles.humanSize(65_536));
        assertEquals("1.5M", RemoteFiles.humanSize(1_572_864));
        assertEquals("42s", RemoteFiles.age(1000, 1042));
        assertEquals("3m", RemoteFiles.age(1000, 1000 + 200));
        assertEquals("2h", RemoteFiles.age(0, 7300));
        assertEquals("4d", RemoteFiles.age(0, 4 * 86_400 + 5));
        // A clock skew must not print a negative age.
        assertEquals("0s", RemoteFiles.age(2000, 1000));
    }

    @Test
    void downloadRefusesAFileAboveTheLimit() {
        RemoteFiles files = new RemoteFiles(new ProcessSshRunner());
        RemoteFiles.RemoteFile huge = new RemoteFiles.RemoteFile(
                "/tmp/huge.log", RemoteFiles.MAX_DOWNLOAD_BYTES + 1, 0);
        IOException thrown = org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
                () -> files.download("nowhere.invalid", huge));
        assertTrue(thrown.getMessage().contains("download limit"), thrown.getMessage());
    }

    @Test
    void downloadsDirIsInsideTheHome() {
        assertFalse(RemoteFiles.downloadsDir().toString().isBlank());
        assertTrue(RemoteFiles.downloadsDir().startsWith(System.getProperty("user.home")));
    }
}
