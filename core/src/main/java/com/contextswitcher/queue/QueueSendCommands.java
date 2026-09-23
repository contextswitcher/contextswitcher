package com.contextswitcher.queue;

import com.contextswitcher.ssh.ProcessSshRunner;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/// Command lines delivering a queued message into a task's tmux window.
/// The message text never appears in an argv: it travels on **stdin** into
/// `tmux load-buffer`, so arbitrary quotes and newlines survive and the
/// remote command stays free of double quotes (which Windows ssh.exe
/// mangles, see `ProcessSshRunner.buildCommand`). Images travel the same
/// way, base64-encoded — pure ASCII through any ssh stdin.
// [impl->dsn~message-queue-send~5]
public final class QueueSendCommands {

    /// tmux buffer name; a fixed own name so the user's buffer stack (`prefix ]`)
    /// is not disturbed and a concurrent send simply overwrites it.
    static final String BUFFER = "cs-queue";

    /// Remote drop directory for uploaded images, relative to the remote
    /// home (`cat`'s working directory on a fresh ssh exec channel).
    static final String REMOTE_DIR = ".contextswitcher/attachments";

    private QueueSendCommands() {
    }

    /// Loads the message text (stdin) into the send buffer.
    public static List<String> loadBufferCommand() {
        return List.of("tmux", "load-buffer", "-b", BUFFER, "-");
    }

    /// Pastes the buffer into the target window and submits it: `-p`
    /// (bracketed paste) keeps a multi-line message in Claude's input box,
    /// and the **separate** `Enter` after a server-side pause submits it —
    /// an `Enter` in the same burst would be absorbed as a newline (same
    /// trick as `ClaudeWindowLauncher`). `-d` frees the buffer.
    public static List<String> pasteCommand(String target) {
        return pasteCommand(target, false);
    }

    /// As [#pasteCommand(String)], but with `confirm` a **second** delayed
    /// `Enter` follows. `/effort` answers a submitted level with a modal
    /// ("Change effort level?", `1. Yes` preselected) once the conversation
    /// is cached; unanswered, that modal swallows every later paste — the
    /// following command and the message itself vanish and the chat looks
    /// hung. The extra `Enter` takes the preselected `Yes`; where no modal
    /// appears (`/model`, a fresh session) it lands on an empty input box
    /// and does nothing.
    public static List<String> pasteCommand(String target, boolean confirm) {
        List<String> command = new ArrayList<>(List.of("tmux",
                "paste-buffer", "-d", "-p", "-b", BUFFER, "-t", "'" + target + "'", "\\;",
                "run-shell", "'sleep 1'", "\\;",
                "send-keys", "-t", "'" + target + "'", "Enter"));
        if (confirm) {
            command.addAll(List.of("\\;", "run-shell", "'sleep 1'", "\\;",
                    "send-keys", "-t", "'" + target + "'", "Enter"));
        }
        return List.copyOf(command);
    }

    /// Decodes one base64-encoded attachment (stdin) into the remote drop
    /// directory. Plain shell words — the remote shell handles `&&` and
    /// `>`; the target path is single-quoted (double quotes are off limits,
    /// see the class comment), so a file name carrying a space is one
    /// redirect target instead of extra `base64` operands.
    public static List<String> uploadCommand(String fileName) {
        return List.of("mkdir", "-p", REMOTE_DIR,
                "&&", "base64", "-d", ">", "'" + REMOTE_DIR + "/" + fileName + "'");
    }

    /// Encodes image bytes for [#uploadCommand]'s stdin: base64 in
    /// 76-column lines with **LF** separators — the MIME default CRLF makes
    /// GNU `base64 -d` fail with `base64: invalid input` (it rejects the
    /// CR bytes).
    public static byte[] uploadPayload(byte[] data) {
        return Base64.getMimeEncoder(76, new byte[] {'\n'}).encode(data);
    }

    /// The absolute remote path an uploaded image lands at — what the
    /// message text carries after the marker rewrite.
    public static String remotePath(String remoteHome, String fileName) {
        return remoteHome + "/" + REMOTE_DIR + "/" + fileName;
    }
}
