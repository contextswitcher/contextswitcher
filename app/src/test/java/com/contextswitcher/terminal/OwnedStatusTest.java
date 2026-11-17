package com.contextswitcher.terminal;

import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~terminal-owned-session~5]
class OwnedStatusTest {

    @Test
    void hooksWriteTheStatusFileTheReadPicksUpUnderTheTasksKey(@TempDir Path dir) throws Exception {
        Path settings = OwnedStatus.prepare(dir, "jabref/fix npe");

        JsonNode hooks = new ObjectMapper().readTree(Files.readString(settings)).get("hooks");
        String stop = hooks.get("Stop").get(0).get("hooks").get(0).get("command").asText();
        assertThat(stop).startsWith("echo waiting>\"").endsWith(".status\"");
        assertThat(hooks.get("UserPromptSubmit").get(0).get("hooks").get(0).get("command").asText())
                .startsWith("echo working>");

        // What the hook writes — cmd's line break included — is what the poll reads.
        Path status = Path.of(stop.substring(stop.indexOf('"') + 1, stop.length() - 1));
        Files.writeString(status, "waiting\r\n");
        assertThat(OwnedStatus.read(dir)).containsEntry(OwnedStatus.key("jabref/fix npe"), "waiting");

        // A new start drops the old run's status.
        OwnedStatus.prepare(dir, "jabref/fix npe");
        assertThat(OwnedStatus.read(dir)).isEmpty();
    }

    @Test
    void anOwnedChatCanBeArmed() {
        assertThat(new ChatRoute.Owned("t").canDelay()).isTrue();
        assertThat(new ChatRoute.None().canDelay()).isFalse();
    }
}
