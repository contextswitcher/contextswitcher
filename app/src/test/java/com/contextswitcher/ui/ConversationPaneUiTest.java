package com.contextswitcher.ui;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import atlantafx.base.theme.PrimerDark;
import com.contextswitcher.discovery.ClaudeConversation.Message;
import com.contextswitcher.tasks.Task;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.jabref.chatpane.ChatMessage;
import org.jabref.chatpane.ChatPane;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~conversation-pane~1]
@Tag("ui")
@TestFxApplication(ConversationPaneUiTest.App.class)
class ConversationPaneUiTest {

    private static final List<Message> TRANSCRIPT = List.of(
            new Message(true, "P1 - Claim (proven!) + Result\nP4 - Result (Artifact)\n\nSorted: P1 > P4 > P7 > P6",
                    Instant.parse("2026-11-28T11:50:00Z")),
            new Message(false, """
                    I've saved the plan in `notes/chapter-plan.md`.

                    **Your other points:**

                    - **NFRs:** measuring changed (S)LOC is reliable and cheap.
                    - **15 weeks:** fine as a contribution.

                    ```java
                    int answer = 42;
                    ```
                    """, Instant.parse("2026-11-28T11:52:00Z")));

    private static final List<String> FETCHED = new ArrayList<>();
    private static final List<String> COMPOSED = new ArrayList<>();
    private static ConversationPane pane;
    private static Scene scene;

    public static class App extends Application {
        @Override
        public void start(Stage stage) {
            Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
            pane = new ConversationPane(Runnable::run, (host, cwd, sessionId) -> {
                FETCHED.add(host + " " + cwd + " " + sessionId);
                return TRANSCRIPT;
            });
            pane.setActions(url -> { }, COMPOSED::add);
            scene = new Scene(new StackPane(pane.getRoot()), 700, 500);
            scene.getStylesheets().add(MainWindow.class.getResource("main.css").toExternalForm());
            stage.setScene(scene);
            stage.show();
        }
    }

    @Test
    void showsWhoSaidWhatAndReplyQuotesTheMessage() throws Exception {
        var source = new TerminalSession.ReplySource("devbox",
                new Task.ClaudeConfig("/home/o/repo", "abc", null, null), "abc");
        ChatPane chat = (ChatPane) scene.lookup(".chat-pane");
        onFx(() -> pane.show(source, "working"));
        onFx(() -> { });

        assertThat(FETCHED).containsExactly("devbox /home/o/repo abc");
        assertThat(chat.getMessages()).extracting(ChatMessage::sender, ChatMessage::direction).containsExactly(
                org.assertj.core.groups.Tuple.tuple(ConversationPane.YOU, ChatMessage.Direction.OUTGOING),
                org.assertj.core.groups.Tuple.tuple(ConversationPane.CLAUDE, ChatMessage.Direction.INCOMING));

        // The same session and status again: no second round-trip; a status edge reads again.
        onFx(() -> pane.show(source, "working"));
        assertThat(FETCHED).hasSize(1);
        onFx(() -> pane.show(source, "waiting"));
        assertThat(FETCHED).hasSize(2);

        onFx(() -> chat.getMessageActions().getFirst().getOnAction().accept(chat.getMessages().getLast()));
        assertThat(COMPOSED).containsExactly("> I've saved the plan in `notes/chapter-plan.md`.\n\n");
    }

    private static void onFx(Runnable action) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } finally {
                done.countDown();
            }
        });
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
    }
}
