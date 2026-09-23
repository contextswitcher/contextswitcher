package com.contextswitcher.ui;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.contextswitcher.ssh.SshCommandRunner;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static com.contextswitcher.ui.UiTestSupport.clickButton;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// The first-start wizard's page logic (`dsn~setup-wizard~8`): Next on the
/// remote page runs the connection check and only a success moves on, the
/// checked remote's home becomes the proposed workspaces root, and the
/// local choice skips the remote page and proposes a root under the local
/// home — after this machine's own check passed; the tools page probes and
/// installs through its own runner. Driven against fake runners and a fake
/// local check — no host is contacted, no tool run.
///
/// Needs a display: `gradlew :app:uiTest` on a desktop, `just uitest` headless.
// [utest->dsn~setup-wizard~8]
@Tag("ui")
@TestFxApplication(SetupWizardUiTest.TestApp.class)
class SetupWizardUiTest {

    public static class TestApp extends Application {

        @Override
        public void start(Stage stage) {
            stage.setScene(new Scene(new Pane(), 800, 600));
            stage.show();
        }
    }

    /// Answers the probe for `devbox` only.
    private static final SshCommandRunner FAKE_SSH = new SshCommandRunner() {
        @Override
        public SshResult run(String host, List<String> remoteCommand) {
            return "devbox".equals(host)
                    ? new SshResult(0, "/home/dev\ntmux 3.4\n2.0.5 (Claude Code)\n", "")
                    : new SshResult(255, "", "ssh: Could not resolve hostname " + host);
        }

        @Override
        public SshResult runWithInput(String host, List<String> remoteCommand, byte[] input) {
            return run(host, remoteCommand);
        }
    };

    /// The tools runner: nothing installed until its Install ran; codegraph's
    /// install must have received the skill on stdin.
    private static final java.util.Set<String> INSTALLED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final SshCommandRunner FAKE_TOOLS = new SshCommandRunner() {
        @Override
        public SshResult run(String host, List<String> command) {
            return runWithInput(host, command, new byte[0]);
        }

        @Override
        public SshResult runWithInput(String host, List<String> command, byte[] input) {
            String script = String.join(" ", command);
            String tool = script.contains("RefactoringMiner") ? "refactoringminer"
                    : script.contains("ast-grep") ? "ast-grep" : "codegraph";
            if (script.contains("install.sh") || script.contains("unzip")) {
                if (tool.equals("codegraph") && input.length == 0) {
                    return new SshResult(1, "", "no skill on stdin");
                }
                INSTALLED.add(host + "/" + tool);
            }
            return INSTALLED.contains(host + "/" + tool)
                    ? new SshResult(0, "1.0 (" + tool + ")\n", "")
                    : new SshResult(1, "", "not found");
        }
    };

    private static final java.util.List<String> OPENED = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final SetupWizard.Extension EXTENSION = new SetupWizard.Extension(17872, "t0k3n",
            com.contextswitcher.config.Browser.FIREFOX, OPENED::add, () -> null);

    private static final java.util.function.Supplier<SetupWizard.Verdict> LOCAL_OK =
            () -> SetupWizard.toolsVerdict("/home/local", "tmux 3.4", "2.0.5 (Claude Code)", true,
                    "on this machine");

    @Test
    void remoteIsCheckedOnNextAndItsHomeSeedsTheWorkspacesRoot() {
        AtomicReference<Optional<SetupWizard.Result>> result = new AtomicReference<>();
        Platform.runLater(() -> result.set(SetupWizard.show(FAKE_SSH, LOCAL_OK, FAKE_TOOLS, "/home/local", false, true, SetupWizard.Prefill.EMPTY, EXTENSION)));
        clickButton("Next");
        type(field("#setup-host"), "nowhere");
        clickButton("Next");
        Label verdict = awaitPresent(() -> FX_ROBOT.selectNodes(Label.class, "#setup-verdict")
                .fetchOptional().filter(label -> label.getText().startsWith("Failed")), "failed verdict");
        assertThat(verdict.getText()).contains("Could not resolve hostname");
        // The remote page was never left, so the local check played no part.
        type(field("#setup-host"), "devbox");
        clickButton("Next");
        // The failure kept the page; the success moved on to the tools page,
        // probed as not installed, and one Install makes codegraph OK.
        Label codegraph = awaitPresent(() -> FX_ROBOT.selectNodes(Label.class, "#setup-tool-codegraph")
                .fetchOptional().filter(label -> label.getText().equals("Not installed")), "codegraph status");
        var installCodegraph = FX_ROBOT.selectNodes(javafx.scene.control.Button.class, "#setup-install-codegraph")
                .fetchOptional().orElseThrow();
        clickButtonById("setup-install-codegraph");
        awaitPresent(() -> Optional.of(codegraph).filter(label -> label.getText().startsWith("OK: 1.0")),
                "installed codegraph");
        // Installed, there is nothing left to install.
        assertThat(installCodegraph.isVisible()).isFalse();
        assertThat(INSTALLED).contains("devbox/codegraph").doesNotContain("devbox/ast-grep");
        clickButton("Next");
        // The extension page: the token to paste, and the install notes for
        // the configured browser open through the app.
        assertThat(field("#setup-ws-token").getText()).isEqualTo("t0k3n");
        clickButtonById("setup-extension-notes");
        assertThat(OPENED).containsExactly(SetupWizard.installNotes(com.contextswitcher.config.Browser.FIREFOX));
        clickButton("Next");
        field("#setup-clone-url");
        clickButton("Next");
        type(field("#setup-repo-url"), "https://github.com/org/proj");
        assertThat(field("#setup-root").getText()).isEqualTo("/home/dev/proj-workspaces");
        clickButton("Finish");
        awaitPresent(() -> Optional.ofNullable(result.get()), "wizard result");
        assertThat(result.get()).hasValueSatisfying(r -> {
            assertThat(r.remote()).isEqualTo("devbox");
            assertThat(r.taskRepoUrl()).isNull();
            assertThat(r.repoUrl()).isEqualTo("https://github.com/org/proj");
            assertThat(r.workspacesRoot()).isEqualTo("/home/dev/proj-workspaces");
            assertThat(r.firstMessage()).isNull();
        });
    }

    @Test
    void localChoiceSkipsTheRemotePage() {
        AtomicReference<Optional<SetupWizard.Result>> result = new AtomicReference<>();
        Platform.runLater(() -> result.set(SetupWizard.show(FAKE_SSH, LOCAL_OK, FAKE_TOOLS, "/home/local", false, true, SetupWizard.Prefill.EMPTY, EXTENSION)));
        RadioButton local = awaitPresent(
                () -> FX_ROBOT.selectNodes(RadioButton.class, "#setup-local").fetchOptional(), "local choice");
        FX_ROBOT.mouse().moveTo(local).click();
        clickButton("Next");
        // The tools page, for this machine: no RefactoringMiner row.
        awaitPresent(() -> FX_ROBOT.selectNodes(Label.class, "#setup-tool-ast-grep").fetchOptional(),
                "ast-grep status");
        assertThat(FX_ROBOT.selectNodes(Label.class, "#setup-tool-refactoringminer").fetchOptional())
                .as("RefactoringMiner is ssh-only").isEmpty();
        clickButton("Next");
        field("#setup-ws-token");
        clickButton("Next");
        field("#setup-clone-url");
        clickButton("Next");
        type(field("#setup-repo-url"), "https://github.com/org/proj");
        assertThat(field("#setup-root").getText()).isEqualTo("/home/local/proj-workspaces");
        // The second way out: Finish, plus the checkup question for the setup session.
        clickButtonById("setup-checkup");
        awaitPresent(() -> Optional.ofNullable(result.get()), "wizard result");
        assertThat(result.get()).hasValueSatisfying(r -> {
            assertThat(r.local()).isTrue();
            assertThat(r.workspacesRoot()).isEqualTo("/home/local/proj-workspaces");
            assertThat(r.firstMessage()).isEqualTo(SetupWizard.CHECKUP_PROMPT);
        });
    }

    @Test
    void aMissingLocalToolKeepsTheFirstPage() {
        boolean[] installed = {false};
        java.util.function.Supplier<SetupWizard.Verdict> local = () -> SetupWizard.toolsVerdict(
                "/home/local", "tmux 3.4", installed[0] ? "2.0.5 (Claude Code)" : null, true,
                "on this machine");
        Platform.runLater(() -> SetupWizard.show(FAKE_SSH, local, FAKE_TOOLS, "/home/local", false, true, SetupWizard.Prefill.EMPTY, EXTENSION));
        RadioButton localChoice = awaitPresent(
                () -> FX_ROBOT.selectNodes(RadioButton.class, "#setup-local").fetchOptional(), "local choice");
        FX_ROBOT.mouse().moveTo(localChoice).click();
        clickButton("Next");
        Label verdict = awaitPresent(() -> FX_ROBOT.selectNodes(Label.class, "#setup-local-verdict")
                .fetchOptional().filter(label -> label.getText().startsWith("Missing")), "missing verdict");
        assertThat(verdict.getText()).contains("Claude Code");
        assertThat(FX_ROBOT.selectNodes(TextField.class, "#setup-clone-url").fetchOptional())
                .as("still on the first page").isEmpty();
        installed[0] = true;
        clickButton("Next");
        awaitPresent(() -> FX_ROBOT.selectNodes(Label.class, "#setup-tool-ast-grep").fetchOptional(),
                "tools page");
        clickButton("Cancel");
    }

    /// The menu re-run: no clone page between the tools and the project.
    @Test
    void aRerunSkipsTheClonePage() {
        AtomicReference<Optional<SetupWizard.Result>> result = new AtomicReference<>();
        Platform.runLater(() -> result.set(
                SetupWizard.show(FAKE_SSH, LOCAL_OK, FAKE_TOOLS, "/home/local", false, false, SetupWizard.Prefill.EMPTY, EXTENSION)));
        RadioButton local = awaitPresent(
                () -> FX_ROBOT.selectNodes(RadioButton.class, "#setup-local").fetchOptional(), "local choice");
        FX_ROBOT.mouse().moveTo(local).click();
        clickButton("Next");
        awaitPresent(() -> FX_ROBOT.selectNodes(Label.class, "#setup-tool-ast-grep").fetchOptional(),
                "tools page");
        clickButton("Next");
        field("#setup-ws-token");
        clickButton("Next");
        field("#setup-repo-url");
        assertThat(FX_ROBOT.selectNodes(TextField.class, "#setup-clone-url").fetchOptional())
                .as("clone page skipped").isEmpty();
        clickButton("Finish");
        awaitPresent(() -> Optional.ofNullable(result.get()), "wizard result");
        assertThat(result.get()).hasValueSatisfying(r -> assertThat(r.taskRepoUrl()).isNull());
    }

    /// A re-run starts from the configuration: the host is filled in and
    /// the root is proposed under the existing roots' parent, not the home.
    @Test
    void aRerunStartsFromTheConfiguredRemoteAndRoots() {
        AtomicReference<Optional<SetupWizard.Result>> result = new AtomicReference<>();
        Platform.runLater(() -> result.set(SetupWizard.show(FAKE_SSH, LOCAL_OK, FAKE_TOOLS,
                "/home/local", false, false, new SetupWizard.Prefill("devbox", "/data/me", null), EXTENSION)));
        clickButton("Next");
        assertThat(field("#setup-host").getText()).isEqualTo("devbox");
        clickButton("Next");
        awaitPresent(() -> FX_ROBOT.selectNodes(Label.class, "#setup-tool-ast-grep").fetchOptional(),
                "tools page");
        clickButton("Next");
        field("#setup-ws-token");
        clickButton("Next");
        type(field("#setup-repo-url"), "https://github.com/org/proj");
        assertThat(field("#setup-root").getText()).isEqualTo("/data/me/proj-workspaces");
        clickButton("Cancel");
    }

    /// No configured remote on a re-run preselects local sessions.
    @Test
    void aRerunWithoutARemotePreselectsThisMachine() {
        Platform.runLater(() -> SetupWizard.show(FAKE_SSH, LOCAL_OK, FAKE_TOOLS,
                "/home/local", false, false, SetupWizard.Prefill.EMPTY, EXTENSION));
        RadioButton local = awaitPresent(
                () -> FX_ROBOT.selectNodes(RadioButton.class, "#setup-local").fetchOptional(), "local choice");
        assertThat(local.isSelected()).isTrue();
        clickButton("Cancel");
    }

    private static void clickButtonById(String id) {
        var button = awaitPresent(() -> FX_ROBOT.selectNodes(javafx.scene.control.Button.class, "#" + id)
                .fetchOptional().filter(b -> !b.isDisabled()), "button #" + id);
        FX_ROBOT.mouse().moveTo(button).click();
    }

    private static TextField field(String selector) {
        return awaitPresent(() -> FX_ROBOT.selectNodes(TextField.class, selector).fetchOptional(),
                "field " + selector);
    }

    private static void type(TextField field, String text) {
        FX_ROBOT.mouse().moveTo(field).click();
        FX_ROBOT.keyboard().pressAndThenRelease(javafx.scene.input.KeyCode.CONTROL,
                javafx.scene.input.KeyCode.A).type(javafx.scene.input.KeyCode.DELETE).print(text);
    }
}
