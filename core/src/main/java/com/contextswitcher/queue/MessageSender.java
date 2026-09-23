package com.contextswitcher.queue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import com.contextswitcher.ssh.SshCommandRunner;
import com.contextswitcher.terminal.TmuxHost;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// Delivers one queued message into a task's tmux window: uploads every
/// referenced local image, rewrites the markers to the images' remote
/// paths, then pastes the text into the window and submits it. Blocking —
/// callers run it on a background executor.
///
/// A **local** chat ([TmuxHost#LOCAL]) takes the same route through the same
/// commands — only its images are not uploaded: they are already on the
/// machine the chat reads them from, so the marker becomes their own path.
// [impl->dsn~message-queue-send~5]
public class MessageSender {

    /// Refused above this size, per file. The file travels base64 through
    /// one ssh stdin and is held in memory as bytes and as base64 at once, so
    /// the bound caps memory as much as time. 64 MB rather than the 10 MB a
    /// screenshot or PDF needs: the field report that set it was a batch of
    /// 36–56 MB release jars, and refusing exactly those would answer "is
    /// this possible?" with no. Above it a build artefact is better fetched
    /// on the remote itself (`gh release download`, a URL, `scp`).
    public static final long MAX_UPLOAD_BYTES = 64L * 1024 * 1024;

    /// One upload at a time, app-wide. Every upload holds one of
    /// `ProcessSshRunner`'s six global ssh slots for as long as it runs —
    /// minutes for a large file — and a batch uploaded in parallel would hold
    /// several, stalling the mirror attach and the pollers behind it. Serial
    /// uploads cost at most one slot; the batch just takes longer.
    private static final ReentrantLock UPLOAD_LOCK = new ReentrantLock();

    private final SshCommandRunner ssh;
    /// For the uploads only: a file copy needs a longer timeout than the
    /// tmux round-trips `ssh` is sized for.
    private final SshCommandRunner uploads;
    private final Path attachmentsDir;
    /// Remote home per host, for absolute image paths in the message; a
    /// home does not move, so one `pwd` per host and app run suffices.
    private final Map<String, String> homeByRemote = new ConcurrentHashMap<>();

    /// Told about every message pasted into a chat, with the text as the chat
    /// received it (attachment markers rewritten) — slash commands of a mode
    /// switch excluded. Called on the sending thread after the paste; what it
    /// throws is logged and never fails the send.
    @FunctionalInterface
    public interface DeliveryListener {
        void delivered(String remote, String target, String deliveredText);
    }

    private volatile @Nullable DeliveryListener deliveryListener;

    /// Installs the one listener (the provenance recorder, `dsn~provenance-record~1`); null removes it.
    // [impl->dsn~provenance-record~1]
    public void setDeliveryListener(@Nullable DeliveryListener listener) {
        this.deliveryListener = listener;
    }

    private void notifyDelivered(String remote, String target, String deliveredText) {
        DeliveryListener listener = deliveryListener;
        if (listener == null) {
            return;
        }
        try {
            listener.delivered(remote, target, deliveredText);
        } catch (RuntimeException e) {
            Logger.warn("Recording the message sent to {} on {} failed: {}", target, remote, e.toString());
        }
    }

    public MessageSender(SshCommandRunner ssh, Path attachmentsDir) {
        this(ssh, ssh, attachmentsDir);
    }

    /// `uploads` carries the attachment copies, `ssh` everything else.
    public MessageSender(SshCommandRunner ssh, SshCommandRunner uploads, Path attachmentsDir) {
        this.ssh = ssh;
        this.uploads = uploads;
        this.attachmentsDir = attachmentsDir;
    }

    /// Sends `text` to the tmux `target` on `remote`; null on success, else
    /// a short human-readable failure (the pane's status line).
    public @Nullable String send(String remote, String target, String text) {
        return send(remote, target, text, ClaudeMode.DEFAULT);
    }

    /// As [#send(String, String, String)], but switches the session's model
    /// and effort first: `mode`'s slash commands are submitted as their own
    /// messages ahead of `text`. A failing command aborts before the message
    /// — sending it at the wrong model is worse than not sending it.
    // [impl->dsn~claude-mode-select~3]
    public @Nullable String send(String remote, String target, String text, ClaudeMode mode) {
        return send(remote, target, text, mode, line -> {});
    }

    /// As [#send(String, String, String, ClaudeMode)], reporting each upload
    /// (`Uploading 3/32: name …`) and, when there was one, the send's outcome
    /// to `progress` — a batch of large files takes minutes.
    ///
    /// An attachment that cannot be uploaded (too large, unreadable, failed)
    /// does not stop the others or the message: it is named in the outcome
    /// and its marker becomes its bare file name with a note, so the chat is
    /// never handed a local path it cannot read. Only a message left with no
    /// text at all is not sent.
    public @Nullable String send(String remote, String target, String text, ClaudeMode mode,
            Consumer<String> progress) {
        String modeError = switchMode(remote, target, mode);
        if (modeError != null) {
            return modeError;
        }
        // [impl->dsn~terminal-local-mirror~2]
        if (TmuxHost.isLocal(remote)) {
            String local = localText(text);
            String localError = paste(remote, target, local, false);
            if (localError == null) {
                notifyDelivered(remote, target, local);
            }
            return localError;
        }
        Map<Path, String> remoteByLocal = new LinkedHashMap<>();
        List<Path> locals = List.copyOf(Attachments.localRefs(text, attachmentsDir));
        List<String> skipped = new ArrayList<>();
        String sendText = text;
        String withoutFailed = text;
        for (int i = 0; i < locals.size(); i++) {
            Path local = locals.get(i);
            String home = remoteHome(remote);
            if (home == null) {
                return "Cannot resolve the home directory on " + remote;
            }
            String name = local.getFileName().toString();
            progress.accept("Uploading %d/%d: %s …".formatted(i + 1, locals.size(), name));
            String error = upload(remote, local);
            if (error == null) {
                remoteByLocal.put(local, QueueSendCommands.remotePath(home, name));
            } else {
                Logger.warn("Not attached to the message for {}: {}", target, error);
                skipped.add(error);
                sendText = Attachments.drop(sendText, local, name + " (not attached)");
                withoutFailed = Attachments.drop(withoutFailed, local, "");
            }
        }
        sendText = Attachments.rewrite(sendText, remoteByLocal);
        if (!skipped.isEmpty() && withoutFailed.isBlank()) {
            return "Not sent, no attachment arrived (kept in %s): %s"
                    .formatted(attachmentsDir, String.join("; ", skipped));
        }
        String pasteError = paste(remote, target, sendText, false);
        if (pasteError != null) {
            return pasteError;
        }
        notifyDelivered(remote, target, sendText);
        if (!skipped.isEmpty()) {
            progress.accept("Sent without %d of %d attachments (kept in %s): %s".formatted(
                    skipped.size(), locals.size(), attachmentsDir, String.join("; ", skipped)));
        } else if (!locals.isEmpty()) {
            progress.accept("Sent with %d attachment%s.".formatted(
                    locals.size(), locals.size() == 1 ? "" : "s"));
        }
        return null;
    }

    /// The message as a chat on **this machine** reads it: every attachment
    /// marker rewritten to the file's own absolute path — there is nothing to
    /// upload, the chat reads the file where it already is.
    // [impl->dsn~terminal-local-mirror~2]
    public String localText(String text) {
        Map<Path, String> ownPaths = new LinkedHashMap<>();
        for (Path local : Attachments.localRefs(text, attachmentsDir)) {
            ownPaths.put(local, local.toAbsolutePath().toString());
        }
        return Attachments.rewrite(text, ownPaths);
    }

    /// Submits only `mode`'s slash commands, stopping at the first failure;
    /// null on success (and for [ClaudeMode#DEFAULT], which sends nothing).
    public @Nullable String switchMode(String remote, String target, ClaudeMode mode) {
        for (String command : mode.commands()) {
            String error = paste(remote, target, command, true);
            if (error != null) {
                return error;
            }
        }
        return null;
    }

    /// One buffer-load-and-paste round: `message` verbatim into `target`,
    /// submitted. Null on success, else the failure text. `confirm` adds the
    /// extra `Enter` that answers a slash command's modal (see
    /// [QueueSendCommands#pasteCommand(String, boolean)]) — never for the
    /// message itself, whose reply may legitimately raise a prompt the user
    /// must answer.
    private @Nullable String paste(String remote, String target, String message, boolean confirm) {
        SshCommandRunner.SshResult loaded = ssh.runWithInput(remote,
                QueueSendCommands.loadBufferCommand(), message.getBytes(StandardCharsets.UTF_8));
        if (!loaded.ok()) {
            return "Cannot stage the message: " + loaded.stderr().strip();
        }
        SshCommandRunner.SshResult pasted =
                ssh.run(remote, QueueSendCommands.pasteCommand(target, confirm));
        return pasted.ok() ? null : "Cannot paste into " + target + ": " + pasted.stderr().strip();
    }

    /// Null on success, else `<name>: <reason>` — the outcome lists these.
    private @Nullable String upload(String remote, Path local) {
        String name = local.getFileName().toString();
        byte[] data;
        try {
            long size = Files.size(local);
            if (size > MAX_UPLOAD_BYTES) {
                return "%s: %d MB is over the %d MB limit, fetch it on the remote instead "
                        .formatted(name, size / (1024 * 1024), MAX_UPLOAD_BYTES / (1024 * 1024))
                        + "(gh release download, a URL, or scp)";
            }
            data = Files.readAllBytes(local);
        } catch (IOException e) {
            return name + ": cannot read (" + e.getMessage() + ")";
        }
        UPLOAD_LOCK.lock();
        try {
            SshCommandRunner.SshResult result = uploads.runWithInput(remote,
                    QueueSendCommands.uploadCommand(name), QueueSendCommands.uploadPayload(data));
            return result.ok() ? null : name + ": " + result.stderr().strip();
        } finally {
            UPLOAD_LOCK.unlock();
        }
    }

    /// Null on failure — and then nothing is cached, so the next send retries.
    private @Nullable String remoteHome(String remote) {
        String cached = homeByRemote.get(remote);
        if (cached != null) {
            return cached;
        }
        SshCommandRunner.SshResult result = ssh.run(remote, List.of("pwd"));
        if (!result.ok() || result.stdout().isBlank()) {
            return null;
        }
        String home = result.stdout().strip();
        homeByRemote.put(remote, home);
        return home;
    }
}
