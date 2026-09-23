package com.contextswitcher.provenance;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

import com.contextswitcher.queue.MessageSender;
import com.contextswitcher.ssh.SshCommandRunner;
import com.contextswitcher.tasks.Task;
import com.contextswitcher.tasks.TaskFileParser;

/// Writes a provenance record for every message [MessageSender] delivers
/// (`dsn~provenance-record~1`): the task behind the chat window, and that
/// window's Claude session, workspace and last commit read right after the paste
/// — Claude has not committed anything for the new message by then.
/// Records nothing while `dir` yields null (no provenance repository), and
/// nothing for a window no task names. `afterWrite` lets the caller sync.
// [impl->dsn~provenance-record~1]
public final class ProvenanceRecorder implements MessageSender.DeliveryListener {

    private final Supplier<@Nullable Path> dir;
    private final String sender;
    private final SshCommandRunner ssh;
    private final BiFunction<String, String, @Nullable Task> taskOf;
    private final Function<String, @Nullable String> repoOfCategory;
    private final Clock clock;
    private final Runnable afterWrite;

    public ProvenanceRecorder(Supplier<@Nullable Path> dir, String sender, SshCommandRunner ssh,
            BiFunction<String, String, @Nullable Task> taskOf, Function<String, @Nullable String> repoOfCategory,
            Clock clock, Runnable afterWrite) {
        this.dir = dir;
        this.sender = TaskFileParser.slug(sender).isEmpty() ? "unknown" : TaskFileParser.slug(sender);
        this.ssh = ssh;
        this.taskOf = taskOf;
        this.repoOfCategory = repoOfCategory;
        this.clock = clock;
        this.afterWrite = afterWrite;
    }

    @Override
    public void delivered(String remote, String target, String deliveredText) {
        Path records = dir.get();
        if (records == null) {
            return;
        }
        Task task = taskOf.apply(remote, target);
        if (task == null) {
            Logger.info("No task for window {} on {}: message not recorded", target, remote);
            return;
        }
        String[] state = windowState(remote, target);
        ProvenanceRecord record = new ProvenanceRecord(clock.instant(), sender, task.id(), task.title(),
                repoOfCategory.apply(categoryOf(task.id())), remote, target,
                state[0], state[1], state[2], deliveredText);
        try {
            Path file = ProvenanceStore.write(records, record);
            Logger.debug("Provenance record {}", file);
            afterWrite.run();
        } catch (java.io.IOException e) {
            Logger.warn("Cannot write the provenance record for {}: {}", task.id(), e.getMessage());
        }
    }

    /// `@cs_session_id`, `@cs_workspace`, `@cs_commit` of the window; nulls when unset or unreadable.
    private @Nullable String[] windowState(String remote, String target) {
        SshCommandRunner.SshResult result = ssh.run(remote, windowStateCommand(target));
        @Nullable String[] values = new String[3];
        if (result.ok()) {
            String[] parts = result.stdout().strip().split("\\|", -1);
            for (int i = 0; i < values.length && i < parts.length; i++) {
                values[i] = parts[i].isBlank() ? null : parts[i].strip();
            }
        }
        return values;
    }

    static List<String> windowStateCommand(String target) {
        return List.of("tmux", "display-message", "-p", "-t", SshCommandRunner.quote(target),
                "'#{@cs_session_id}|#{@cs_workspace}|#{@cs_commit}'");
    }

    private static String categoryOf(String taskId) {
        int slash = taskId.lastIndexOf('/');
        return slash < 0 ? "" : taskId.substring(0, slash);
    }

    /// The task among `tasks` whose chat is `target` on `remote` — how both apps
    /// map a delivery back to its task.
    public static @Nullable Task taskIn(Collection<Task> tasks, String remote, String target) {
        for (Task task : tasks) {
            if (remote.equals(task.remote()) && task.tmux() != null && target.equals(task.tmux().target())) {
                return task;
            }
        }
        return null;
    }
}
