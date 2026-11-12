package com.contextswitcher.discovery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~local-category-session-import~1]
class LocalClaudeSessionsTest {

    private static final String A = "d60a0a07-ed98-463d-93a1-f79ab3608f78";
    private static final String B = "ebe7befe-06c3-4896-930f-77c768f10619";
    private static final String STUB = "7aff15d2-cfac-488e-91e8-e8c2a78040bc";

    private static String prompt(String text) {
        return "{\"type\":\"user\",\"message\":{\"role\":\"user\",\"content\":\"" + text + "\"}}\n";
    }

    @Test
    void projectDirEncodesTheSpacesOfAOneDriveFolder() {
        assertThat(ClaudeSessionLookup.encodeProjectDir(
                "C:\\Users\\olive\\OneDrive - Technische Hochschule Ulm\\Vorlesungen\\SOFEN\\TH Ulm\\2026-WS-OK"))
                .isEqualTo("C--Users-olive-OneDrive---Technische-Hochschule-Ulm-Vorlesungen-SOFEN-TH-Ulm-2026-WS-OK");
    }

    @Test
    void listsSessionsNewestFirstTitledByTheirLatestTitle(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(A + ".jsonl"), prompt("What's on the plate for tomorrow?")
                + "{\"type\":\"ai-title\",\"aiTitle\":\"Old title\",\"sessionId\":\"" + A + "\"}\n"
                + "{\"type\":\"ai-title\",\"aiTitle\":\"Tomorrow's plate, session 08\"}\n");
        Files.setLastModifiedTime(dir.resolve(A + ".jsonl"), FileTime.fromMillis(2_000));
        Files.writeString(dir.resolve(B + ".jsonl"), prompt("Fix the slides\\nsecond line"));
        Files.setLastModifiedTime(dir.resolve(B + ".jsonl"), FileTime.fromMillis(1_000));

        List<LocalClaudeSessions.Session> sessions = LocalClaudeSessions.in(dir);

        assertThat(sessions).extracting(LocalClaudeSessions.Session::id).containsExactly(A, B);
        assertThat(sessions).extracting(LocalClaudeSessions.Session::title)
                .containsExactly("Tomorrow's plate, session 08", "Fix the slides");
    }

    @Test
    void aRenamedSessionKeepsItsCustomTitle(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(A + ".jsonl"), prompt("hi")
                + "{\"type\":\"custom-title\",\"customTitle\":\"Grading\"}\n"
                + "{\"type\":\"ai-title\",\"aiTitle\":\"Something else\"}\n");

        assertThat(LocalClaudeSessions.in(dir)).extracting(LocalClaudeSessions.Session::title)
                .containsExactly("Grading");
    }

    @Test
    void skipsTitleOnlyStubsAndSideFiles(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(STUB + ".jsonl"),
                "{\"type\":\"ai-title\",\"aiTitle\":\"SWEN - 02.pdf image metadata\"}\n");
        Files.writeString(dir.resolve(A + ".orphaned-1792092385336-d55e9618.jsonl"), prompt("x"));
        Files.writeString(dir.resolve("notes.txt"), prompt("x"));

        assertThat(LocalClaudeSessions.in(dir)).isEmpty();
    }

    @Test
    void aMissingProjectDirHasNoSessions(@TempDir Path dir) {
        assertThat(LocalClaudeSessions.in(dir.resolve("absent"))).isEmpty();
    }

    @Test
    void firstPromptSkipsToolResultsAndCommandWrappers() {
        String chunk = "{\"type\":\"user\",\"message\":{\"content\":\"<command-name>/clear</command-name>\"}}\n"
                + "{\"type\":\"user\",\"message\":{\"content\":[{\"type\":\"tool_result\",\"content\":\"ls\"}]}}\n"
                + "{\"type\":\"user\",\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"Real prompt\"}]}}\n";

        assertThat(LocalClaudeSessions.firstPrompt(chunk)).isEqualTo("Real prompt");
    }
}
