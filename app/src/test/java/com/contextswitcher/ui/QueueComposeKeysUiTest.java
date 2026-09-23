package com.contextswitcher.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.contextswitcher.tasks.TaskEntry;
import com.contextswitcher.terminal.LocalClaudeLauncher;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// The queue boxes' compose chords (`dsn~message-queue-ui~26`), driven on
/// the real app: three plain Enters queue the typed message, and the
/// Ctrl+Enter chord queues on its third press, greening the box up a shade
/// per press until then. The card edit boxes get the chord tested at their own
/// call site too — the same implementation, installed without the
/// triple-Enter commit. The Add-task dialog's half of the shared
/// implementation has its own test ([AddTaskDialogUiTest]); this one covers
/// the queue side, which had no automated check of its own.
///
/// The send the third Ctrl+Enter fires needs a real remote tmux window, so it
/// stays a manual E2E item — the test task has none, its send button is
/// disabled, and firing it does nothing.
// [utest->dsn~message-queue-ui~26]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class QueueComposeKeysUiTest {

    @Test
    void threeEntersQueueTheMessage() throws Exception {
        selectAlphaAndFocusAddBox();
        FX_ROBOT.keyboard().print("hello queue").type(KeyCode.ENTER, KeyCode.ENTER, KeyCode.ENTER);
        assertThat(awaitQueue("hello queue")).contains("hello queue");
    }

    @Test
    void secondMessageAlsoQueuesWithThreeEnters() throws Exception {
        selectAlphaAndFocusAddBox();
        FX_ROBOT.keyboard().print("first msg").type(KeyCode.ENTER, KeyCode.ENTER, KeyCode.ENTER);
        assertThat(awaitQueue("first msg")).contains("first msg");
        FX_ROBOT.keyboard().print("second msg").type(KeyCode.ENTER, KeyCode.ENTER, KeyCode.ENTER);
        assertThat(awaitQueue("second msg")).contains("second msg");
    }

    /// The message stays in the add box until the chord is complete — the
    /// first two presses only colour it, the third queues and sends it. The
    /// test task has no remote, so the send is a no-op and the queued message
    /// is what the file shows.
    @Test
    void theChordQueuesOnlyOnItsThirdPress() throws Exception {
        TextArea addBox = selectAlphaAndFocusAddBox();
        FX_ROBOT.keyboard().print("chord msg").pressAndThenRelease(KeyCode.CONTROL, KeyCode.ENTER);
        assertThat(shade(addBox)).isEqualTo("queue-chord-1");
        assertThat(addBox.getText()).contains("chord msg");
        FX_ROBOT.keyboard().pressAndThenRelease(KeyCode.CONTROL, KeyCode.ENTER);
        assertThat(shade(addBox)).isEqualTo("queue-chord-2");
        assertThat(addBox.getText()).contains("chord msg");
        FX_ROBOT.keyboard().pressAndThenRelease(KeyCode.CONTROL, KeyCode.ENTER);
        // The firing press wears no shade: the box is empty again and green on
        // it would colour the box the next message is typed into.
        assertThat(shade(addBox)).isNull();
        assertThat(awaitQueue("chord msg")).contains("chord msg");
    }

    /// On a **sendable** task the third press must actually reach the send —
    /// the remote is bogus, so the send fails and its error appears in the
    /// pane's status line; without a send attempt the line stays empty.
    @Test
    void theThirdPressReachesTheSendOnASendableTask() throws Exception {
        selectAndFocusAddBox("beta", """
                ---
                title: beta
                status: active
                remote: nosuchhost.invalid
                tmux:
                  session: "0"
                  window: "@1"
                ---
                notes
                """);
        FX_ROBOT.keyboard().print("sendable msg")
                .pressAndThenRelease(KeyCode.CONTROL, KeyCode.ENTER)
                .pressAndThenRelease(KeyCode.CONTROL, KeyCode.ENTER)
                .pressAndThenRelease(KeyCode.CONTROL, KeyCode.ENTER);
        assertThat(awaitQueue("beta", "sendable msg")).contains("sendable msg");
        // "Sending…" while the round-trip runs, the ssh error once it fails —
        // either proves the press got past the queueing and into the send.
        assertThat(awaitStatusLine()).isNotEqualTo("");
    }

    /// A send that failed hands its card back editable, ready for a fix and
    /// a retry — the box is read-only only while the message is in flight
    /// (the bogus remote fails too fast to catch that state from here; it is
    /// a manual check, `docs/manual-test.md`).
    @Test
    void aFailedSendLeavesItsCardEditable() throws Exception {
        selectAndFocusAddBox("gamma", """
                ---
                title: gamma
                status: active
                remote: nosuchhost.invalid
                tmux:
                  session: "0"
                  window: "@1"
                ---
                notes
                """);
        FX_ROBOT.keyboard().print("retry msg")
                .pressAndThenRelease(KeyCode.CONTROL, KeyCode.ENTER)
                .pressAndThenRelease(KeyCode.CONTROL, KeyCode.ENTER)
                .pressAndThenRelease(KeyCode.CONTROL, KeyCode.ENTER);
        // The ssh error proves the send ran and failed, so the card below is
        // in its post-failure state rather than one never sent.
        assertThat(awaitStatusLine()).contains("ssh");
        TextArea card = focusCardWithText("retry msg");
        FX_ROBOT.keyboard().print(" fixed");
        fxSync();
        assertThat(card.getText()).isEqualTo("retry msg fixed");
    }

    /// A task with no remote has no tmux to send to: the chord queues the
    /// message and says so, instead of flashing green over a card that quietly
    /// stayed behind. On Windows the app hosts such a task's chat itself, so
    /// the chord does reach the send — which, with no session started, names
    /// what is missing instead.
    // [utest->dsn~terminal-owned-session~3]
    @Test
    void withoutARemoteTheChordSaysItOnlyQueued() throws Exception {
        selectAlphaAndFocusAddBox();
        FX_ROBOT.keyboard().print("no remote msg")
                .pressAndThenRelease(KeyCode.CONTROL, KeyCode.ENTER)
                .pressAndThenRelease(KeyCode.CONTROL, KeyCode.ENTER)
                .pressAndThenRelease(KeyCode.CONTROL, KeyCode.ENTER);
        assertThat(awaitQueue("no remote msg")).contains("no remote msg");
        assertThat(awaitStatusLine()).contains(LocalClaudeLauncher.onWindows()
                ? "No Claude session is running" : "no tmux window");
    }

    /// A card edit box carries the same chord: three Ctrl+Enters save the
    /// edit and send that card. The test task has nothing to send to, so the
    /// third press says so — and the edit is in the queue file either way.
    @Test
    void theCardChordSavesTheEditAndSaysItCouldNotSend() throws Exception {
        selectAlphaAndFocusAddBox();
        FX_ROBOT.keyboard().print("card msg").type(KeyCode.ENTER, KeyCode.ENTER, KeyCode.ENTER);
        assertThat(awaitQueue("card msg")).contains("card msg");
        TextArea card = focusCardWithText("card msg");
        FX_ROBOT.keyboard().print(" edited").pressAndThenRelease(KeyCode.CONTROL, KeyCode.ENTER);
        assertThat(shade(card)).isEqualTo("queue-chord-1");
        FX_ROBOT.keyboard().pressAndThenRelease(KeyCode.CONTROL, KeyCode.ENTER);
        assertThat(shade(card)).isEqualTo("queue-chord-2");
        FX_ROBOT.keyboard().pressAndThenRelease(KeyCode.CONTROL, KeyCode.ENTER);
        assertThat(awaitQueue("card msg edited")).contains("card msg edited");
        // "Saved", not the add box's "Queued" — the chord came from the card.
        assertThat(awaitStatusLine()).startsWith("Saved");
    }

    /// The card boxes are exempt from the triple-Enter commit — blank lines
    /// are content while editing an existing message, so all three newlines
    /// stay in the box and nothing is submitted.
    @Test
    void threeEntersInACardOnlyInsertNewlines() throws Exception {
        selectAlphaAndFocusAddBox();
        FX_ROBOT.keyboard().print("keep blanks").type(KeyCode.ENTER, KeyCode.ENTER, KeyCode.ENTER);
        assertThat(awaitQueue("keep blanks")).contains("keep blanks");
        TextArea card = focusCardWithText("keep blanks");
        FX_ROBOT.keyboard().type(KeyCode.ENTER, KeyCode.ENTER, KeyCode.ENTER);
        fxSync();
        assertThat(card.getText()).isEqualTo("keep blanks\n\n\n");
    }

    /// Anything typed between two Ctrl+Enters drops the run — a chord only
    /// counts presses that actually follow one another.
    @Test
    void aKeystrokeBetweenTheChordPressesDropsTheRun() throws Exception {
        TextArea addBox = selectAlphaAndFocusAddBox();
        FX_ROBOT.keyboard().print("dropped").pressAndThenRelease(KeyCode.CONTROL, KeyCode.ENTER);
        assertThat(shade(addBox)).isEqualTo("queue-chord-1");
        FX_ROBOT.keyboard().type(KeyCode.X);
        assertThat(shade(addBox)).isNull();
        FX_ROBOT.keyboard().pressAndThenRelease(KeyCode.CONTROL, KeyCode.ENTER);
        assertThat(shade(addBox)).isEqualTo("queue-chord-1");
        // Two presses and a stray key later, nothing has left the box.
        assertThat(addBox.getText()).contains("dropped");
    }

    /// The chord shade currently on the box, or null while no chord runs.
    /// Drains the FX queue first: the robot's key press is dispatched there,
    /// so reading the style class right after it would race the handler.
    private static @org.jspecify.annotations.Nullable String shade(TextArea addBox) {
        fxSync();
        return addBox.getStyleClass().stream()
                .filter(name -> name.startsWith("queue-chord-"))
                .findFirst().orElse(null);
    }

    /// The queued card whose text is `text`, focused with the caret at its
    /// end (a fresh card starts the caret at 0, where an Enter would only
    /// split the first line).
    private static TextArea focusCardWithText(String text) throws Exception {
        TextArea card = UiTestSupport.awaitPresent(
                () -> FX_ROBOT.selectNodes(TextArea.class).fromAll()
                        .filter(area -> area.isEditable() && text.equals(area.getText()))
                        .findFirst(),
                "the card for \"" + text + "\"");
        UiTestSupport.awaitFocused(card, "card");
        Platform.runLater(() -> card.positionCaret(card.getLength()));
        fxSync();
        return card;
    }

    private static TextArea selectAlphaAndFocusAddBox() throws Exception {
        return selectAndFocusAddBox("alpha", "---\ntitle: alpha\nstatus: active\n---\nnotes\n");
    }

    private static TextArea selectAndFocusAddBox(String id, String file) throws Exception {
        Files.writeString(UiTestSupport.tasksDir.resolve(id + ".md"), file);
        ListView<Object> list = UiTestSupport.awaitPresent(
                () -> FX_ROBOT.selectNodes(ListView.class).fromAll()
                        .<ListView<Object>>map(view -> (ListView<Object>) view).findFirst(),
                "the task ListView");
        UiTestSupport.awaitPresent(() -> {
            int index = indexOf(list, id);
            if (index < 0) {
                return Optional.empty();
            }
            Platform.runLater(() -> list.getSelectionModel().select(index));
            return Optional.of(index);
        }, "the " + id + " row");
        TextArea addBox = UiTestSupport.awaitPresent(
                () -> FX_ROBOT.selectNodes(TextArea.class).fromAll()
                        .filter(area -> area.getPromptText() != null
                                && area.getPromptText().startsWith("Queue a message"))
                        .findFirst(),
                "the queue add box");
        UiTestSupport.awaitFocused(addBox, "add box");
        return addBox;
    }

    /// The queue file's content once it holds `marker` — the commit runs
    /// through the FX thread, so poll rather than read once.
    private static String awaitQueue(String marker) throws Exception {
        return awaitQueue("alpha", marker);
    }

    private static String awaitQueue(String id, String marker) throws Exception {
        Path file = UiTestSupport.tasksDir.resolve(".queues").resolve(id + ".yaml");
        long deadline = System.currentTimeMillis() + 10_000;
        String content = "";
        while (System.currentTimeMillis() < deadline) {
            content = Files.exists(file) ? Files.readString(file) : "";
            if (content.contains(marker)) {
                return content;
            }
            UiTestSupport.sleep();
        }
        return content;
    }

    /// The queue pane's status line once it says anything, else "".
    private static String awaitStatusLine() {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            fxSync();
            String text = FX_ROBOT.selectNodes(Label.class).fromAll()
                    .map(Label::getText)
                    .filter(value -> value != null
                            && (value.startsWith("Sending") || value.startsWith("Sent")
                                    || value.startsWith("Queued") || value.startsWith("Saved")
                                    || value.contains("ssh")
                                    || value.contains("annot") || value.contains("ailed")))
                    .findFirst().orElse("");
            if (!text.isEmpty()) {
                return text;
            }
            UiTestSupport.sleep();
        }
        return "";
    }

    /// Waits until everything already queued on the FX thread has run.
    private static void fxSync() {
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(done::countDown);
        try {
            if (!done.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("The FX thread did not drain");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static int indexOf(ListView<Object> list, String id) {
        for (int i = 0; i < list.getItems().size(); i++) {
            if (list.getItems().get(i) instanceof TaskEntry.Loaded loaded && loaded.id().equals(id)) {
                return i;
            }
        }
        return -1;
    }
}
