package com.contextswitcher.queue;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~message-queue-send~5]
class QueueSendCommandsTest {

    @Test
    void pastePastesBracketedThenSubmitsWithASeparateDelayedEnter() {
        assertThat(QueueSendCommands.pasteCommand("@17")).containsExactly(
                "tmux", "paste-buffer", "-d", "-p", "-b", "cs-queue", "-t", "'@17'", "\\;",
                "run-shell", "'sleep 1'", "\\;",
                "send-keys", "-t", "'@17'", "Enter");
    }

    @Test
    void confirmingPasteSendsASecondDelayedEnter() {
        assertThat(QueueSendCommands.pasteCommand("@17", true)).containsExactly(
                "tmux", "paste-buffer", "-d", "-p", "-b", "cs-queue", "-t", "'@17'", "\\;",
                "run-shell", "'sleep 1'", "\\;",
                "send-keys", "-t", "'@17'", "Enter", "\\;",
                "run-shell", "'sleep 1'", "\\;",
                "send-keys", "-t", "'@17'", "Enter");
    }

    @Test
    void loadBufferReadsStdin() {
        assertThat(QueueSendCommands.loadBufferCommand())
                .containsExactly("tmux", "load-buffer", "-b", "cs-queue", "-");
    }

    @Test
    void uploadDecodesBase64StdinIntoTheRemoteDropDir() {
        assertThat(QueueSendCommands.uploadCommand("img-1.png")).containsExactly(
                "mkdir", "-p", ".contextswitcher/attachments",
                "&&", "base64", "-d", ">", "'.contextswitcher/attachments/img-1.png'");
    }

    @Test
    void uploadTargetWithASpaceStaysOneRedirectTarget() {
        // Unquoted, the remote shell would word-split the redirect target
        // and `base64` would fail with "extra operand".
        assertThat(QueueSendCommands.uploadCommand("a b.yml"))
                .contains("'.contextswitcher/attachments/a b.yml'");
    }

    @Test
    void noCommandPartEverCarriesADoubleQuote() {
        assertThat(QueueSendCommands.pasteCommand("@17")).noneMatch(part -> part.contains("\""));
        assertThat(QueueSendCommands.uploadCommand("img.png")).noneMatch(part -> part.contains("\""));
        assertThat(QueueSendCommands.loadBufferCommand()).noneMatch(part -> part.contains("\""));
    }

    @Test
    void uploadPayloadCarriesNoCarriageReturnsAndRoundTrips() {
        byte[] data = new byte[200];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        byte[] payload = QueueSendCommands.uploadPayload(data);
        // GNU `base64 -d` rejects CR bytes with "invalid input".
        assertThat(new String(payload, java.nio.charset.StandardCharsets.US_ASCII))
                .doesNotContain("\r");
        assertThat(java.util.Base64.getMimeDecoder().decode(payload)).isEqualTo(data);
    }

    @Test
    void remotePathIsAbsoluteUnderTheRemoteHome() {
        assertThat(QueueSendCommands.remotePath("/home/koppor", "img-1.png"))
                .isEqualTo("/home/koppor/.contextswitcher/attachments/img-1.png");
    }
}
