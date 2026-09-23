package com.contextswitcher.switching;

import java.util.function.Consumer;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import org.tinylog.Logger;

import com.contextswitcher.tasks.Task;

/// Opens the task's `chat:` link on switch, so activating a task brings its
/// Matrix room forward with the terminal, IDE, and browser targets.
/// The effective link is the task's own `chat:`, falling back to the chat of
/// its category — the group's `CONTEXTSWITCHER.md` `chat:` (resolved by the
/// injected group-chat resolver), because a project usually has one room and
/// only some tasks a thread of their own.
/// A `matrix.to` permalink is rewritten to the `element:` deep link the
/// Element desktop app registers on Windows — the `https://matrix.to/…` form
/// would land in the browser instead. Every other URL is passed through
/// verbatim, so the key also takes an `element:` link (or a chat tool with
/// its own protocol handler) as it was copied.
/// The URL is launched via the injected opener (`Main::openUrl`), which keeps
/// this action free of AWT/JavaFX and thus unit-testable.
// [impl->dsn~chat-focus-action~2]
public class ChatFocusAction implements SwitchAction {

    private static final String MATRIX_TO = "https://matrix.to/#/";

    private final Consumer<String> opener;
    /// Resolves a group's `CONTEXTSWITCHER.md` `chat:` URL by group key;
    /// null when the group has no config file or no chat.
    private final Function<String, @Nullable String> groupChatResolver;

    public ChatFocusAction(Consumer<String> opener,
            Function<String, @Nullable String> groupChatResolver) {
        this.opener = opener;
        this.groupChatResolver = groupChatResolver;
    }

    @Override
    public String name() {
        return "chat";
    }

    @Override
    public boolean isConfigured(Task task) {
        return effectiveChat(task) != null;
    }

    @Override
    public ActionResult run(Task task) {
        boolean fromTask = task.chat() != null;
        String chat = effectiveChat(task);
        if (chat == null) {
            return ActionResult.failure("no chat link configured on the task or its category");
        }
        String url = deepLink(chat);
        Logger.debug("Opening chat {} ({})", url, fromTask ? "task" : "category");
        try {
            opener.accept(url);
        } catch (RuntimeException e) {
            return ActionResult.failure("cannot open chat: " + e.getMessage());
        }
        return ActionResult.success(fromTask ? "opening task chat" : "opening category chat");
    }

    /// The task's own `chat:`, or — when it has none — its category chat.
    /// A root-level task (no group) has no category, so the resolver is
    /// consulted only for a grouped task.
    private @Nullable String effectiveChat(Task task) {
        if (task.chat() != null) {
            return task.chat();
        }
        int slash = task.id().lastIndexOf('/');
        String group = slash < 0 ? "" : task.id().substring(0, slash);
        return group.isEmpty() ? null : groupChatResolver.apply(group);
    }

    /// The desktop link for a chat URL: a `matrix.to` permalink becomes the
    /// Element deep link (`element://#/room/…`, `#/user/…` for a user
    /// permalink — Element's routes carry the kind matrix.to leaves implicit
    /// in the sigil), anything else is returned unchanged.
    public static String deepLink(String chat) {
        String url = chat.strip();
        if (!url.startsWith(MATRIX_TO)) {
            return url;
        }
        String target = url.substring(MATRIX_TO.length());
        return "element://#/" + (target.startsWith("@") ? "user/" : "room/") + target;
    }
}
