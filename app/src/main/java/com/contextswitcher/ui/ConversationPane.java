package com.contextswitcher.ui;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import atlantafx.base.theme.Styles;
import com.contextswitcher.discovery.ClaudeConversation;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jabref.chatpane.ChatMessage;
import org.jabref.chatpane.ChatPane;
import org.jabref.chatpane.MessageAction;
import org.jabref.chatpane.MessageLayout;
import org.jabref.chatpane.MessageRenderer;
import org.jspecify.annotations.Nullable;

/// The chat of the session the terminal shows, as a conversation: the user's
/// messages on one side, Claude's replies — Markdown rendered — on the other,
/// read from the transcript rather than off the screen. Read-only; **Reply**
/// on a message starts an answer in the queue's add box.
// [impl->dsn~conversation-pane~1]
final class ConversationPane {

    /// Reads a session's recent messages; blocking ([ClaudeConversation#fetch]).
    interface Source {
        List<ClaudeConversation.Message> fetch(String host, String cwd, String sessionId);
    }

    static final String YOU = "You";
    static final String CLAUDE = "Claude";

    private final Executor executor;
    private final Source source;
    private final ChatPane chat = new ChatPane();
    private final Label hint = new Label();
    private final VBox root;

    private TerminalSession.@Nullable ReplySource shown;
    private @Nullable String status;
    /// Counts loads, so only the newest one's answer is shown.
    private int load;
    /// A change came while the pane was not on screen; loaded when it is.
    private boolean stale;
    private Consumer<String> composer = text -> { };

    ConversationPane(Executor executor, Source source) {
        this.executor = executor;
        this.source = source;
        chat.setMessageLayout(MessageLayout.BUBBLES);
        chat.setMessageRenderer(MessageRenderer.markdown());
        chat.getMessageActions().add(new MessageAction("Reply", message -> composer.accept(quote(message.text()))));
        hint.getStyleClass().add(Styles.TEXT_MUTED);
        hint.setMouseTransparent(true);
        Button refresh = new Button("Refresh");
        refresh.setId("conversation-refresh");
        refresh.getStyleClass().addAll(Styles.SMALL, Styles.FLAT);
        refresh.setOnAction(event -> reload());
        HBox bar = new HBox(refresh);
        bar.setAlignment(Pos.CENTER_RIGHT);
        StackPane body = new StackPane(chat, hint);
        VBox.setVgrow(body, Priority.ALWAYS);
        root = new VBox(bar, body);
        root.sceneProperty().addListener((obs, was, scene) -> {
            if (scene != null && stale) {
                reload();
            }
        });
        showHint();
    }

    Node getRoot() {
        return root;
    }

    /// Where a clicked link goes and where **Reply** puts its quote.
    void setActions(Consumer<String> openUrl, Consumer<String> replyComposer) {
        chat.setLinkHandler(openUrl);
        this.composer = replyComposer;
    }

    /// The transcript in view and its window's poll status: a new session, or
    /// a status edge (a message taken, a reply finished), reads it again.
    void show(TerminalSession.@Nullable ReplySource replySource, @Nullable String pollStatus) {
        if (Objects.equals(replySource, shown) && Objects.equals(pollStatus, status)) {
            return;
        }
        if (!Objects.equals(replySource, shown)) {
            chat.getMessages().clear();
        }
        shown = replySource;
        status = pollStatus;
        reload();
    }

    private void reload() {
        TerminalSession.ReplySource from = shown;
        int mine = ++load;
        // Not on screen (its dock tab is behind another): no ssh round-trip for nobody.
        stale = root.getScene() == null;
        if (from == null || stale) {
            showHint();
            return;
        }
        executor.execute(() -> {
            List<ChatMessage> messages = source.fetch(from.host(), from.claude().cwd(), from.sessionId()).stream()
                    .map(message -> new ChatMessage(message.fromUser() ? YOU : CLAUDE, message.text(), message.at(),
                            message.fromUser() ? ChatMessage.Direction.OUTGOING : ChatMessage.Direction.INCOMING))
                    .toList();
            Platform.runLater(() -> {
                // An unchanged list is left alone: it keeps the scroll position and a selection.
                if (mine == load && !messages.equals(chat.getMessages())) {
                    chat.getMessages().setAll(messages);
                }
                showHint();
            });
        });
    }

    private void showHint() {
        hint.setText(shown == null ? "No Claude session recorded for this window."
                : "No messages in the transcript yet.");
        hint.setVisible(chat.getMessages().isEmpty());
    }

    /// The start of an answer to `text`: its first line as a Markdown quote,
    /// so Claude knows what the answer is about, then room to type.
    static String quote(String text) {
        String first = text.lines().filter(line -> !line.isBlank()).findFirst().orElse("").strip();
        return "> " + (first.length() > 200 ? first.substring(0, 200) + "…" : first) + "\n\n";
    }
}
