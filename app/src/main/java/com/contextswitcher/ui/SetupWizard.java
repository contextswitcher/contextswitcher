package com.contextswitcher.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import atlantafx.base.theme.Styles;
import org.jspecify.annotations.Nullable;

import com.contextswitcher.local.LocalCommandRunner;
import com.contextswitcher.local.RequiredTools;
import com.contextswitcher.analysis.RefactoringMinerCommands;
import com.contextswitcher.config.Browser;
import com.contextswitcher.ssh.SshCommandRunner;
import com.contextswitcher.terminal.TmuxHost;
import com.contextswitcher.terminal.WslTmuxRunner;
import com.contextswitcher.tasks.TaskFileParser;

/// The first-start wizard: the questions a newcomer would otherwise answer by
/// reading `settings.yaml` and `TEMPLATE.md` comments — where Claude sessions
/// run (this machine, a distribution in WSL, or a remote over ssh — each
/// checked live for `tmux` and `claude`, the remote for its ssh connection
/// first), whether an existing task
/// repository is to be cloned, and the first project's repository URL. The
/// answers come back as a [Result]; `Main` writes the remote into the
/// settings, clones, and hands the project to the same category-from-URL
/// flow the toolbar menu runs (`dsn~category-from-url~4`).
///
/// One `Dialog` with swapped pages rather than six dialogs: Back works, and
/// Cancel on any page leaves everything as it was.
// [impl->dsn~setup-wizard~8]
public final class SetupWizard {

    /// The wizard's answers. `remote` is null for local sessions; every
    /// other field is null when the page was left empty. `checkup` is true
    /// when the wizard was left through *Finish and check up*: the first
    /// project's setup session then goes on to [#CHECKUP_PROMPT].
    public record Result(@Nullable String remote, @Nullable String taskRepoUrl,
            @Nullable String repoUrl, @Nullable String workspacesRoot, RepoType repoType,
            @Nullable String refactoringMinerHome, boolean checkup) {

        public boolean local() {
            return remote == null;
        }

        /// The message the setup session continues with, null without a checkup.
        public @Nullable String firstMessage() {
            return checkup ? CHECKUP_PROMPT : null;
        }
    }

    /// Asked once the first project is set up: Claude's own answer shows
    /// whether permissions, tools and login are in place before a real task.
    public static final String CHECKUP_PROMPT = "If I gave you a task, can you work on it?";

    /// What an existing configuration pre-fills on a re-run: the `host`
    /// (the first configured remote; null selects the local choice), the
    /// `workspacesParent` a new root is proposed under (the existing roots'
    /// parent, else the checked home), and the configured
    /// `refactoringMinerHome` to probe. `EMPTY` on the first start.
    public record Prefill(@Nullable String host, @Nullable String workspacesParent,
            @Nullable String refactoringMinerHome) {

        public static final Prefill EMPTY = new Prefill(null, null, null);
    }

    /// The browser-extension page's facts: the app's WebSocket `port` and
    /// `token` the extension's options page needs, the configured `browser`
    /// (whose install notes the button opens through `openUrl`), and
    /// `connected` — the browser whose extension is talking to the app right
    /// now, null when none is or (first start) the server is not up yet.
    public record Extension(int port, String token, Browser browser, Consumer<String> openUrl,
            Supplier<@Nullable Browser> connected) {
    }

    /// The install notes per browser, on the repository's main branch.
    static String installNotes(Browser browser) {
        return "https://github.com/contextswitcher/contextswitcher/blob/main/extension/"
                + browser.key() + "/README.md";
    }

    /// What a check found: the home directory (the default parent of the
    /// workspaces roots) and the tmux and Claude Code version lines.
    record Probe(String home, @Nullable String tmuxVersion, String claudeVersion) {
    }

    /// A check's outcome: the [Probe] on success (null otherwise) and the
    /// line shown under the field either way.
    public record Verdict(@Nullable Probe probe, String message) {

        public boolean ok() {
            return probe != null;
        }
    }

    /// The remote probe, through a login shell so a `claude` installed under
    /// `~/.local/bin` by the native installer (or by nvm, from the profile)
    /// is found the way the tmux window will find it — a non-interactive
    /// ssh command's `PATH` is thinner than the shell a session runs in.
    static final List<String> REMOTE_PROBE = List.of("sh", "-lc",
            "'PATH=$PATH:$HOME/.local/bin; echo $HOME; tmux -V; claude --version'");

    private SetupWizard() {
    }

    /// The installed WSL distributions, for the distribution box.
    ///
    /// `wsl.exe` prints its listings as UTF-16LE, which arrives here decoded
    /// as UTF-8 — every ASCII character followed by a NUL, and a mangled BOM
    /// in front. Rather than teach the runner a second charset for this one
    /// call, the non-printable bytes are dropped: distribution names are
    /// ASCII in practice, and the box is editable for when they are not.
    // [impl->dsn~wsl-sessions~1]
    static List<String> wslDistros(LocalCommandRunner runner) {
        LocalCommandRunner.LocalResult result =
                runner.run(List.of(WslTmuxRunner.WSL, "--list", "--quiet"));
        if (!result.ok()) {
            return List.of();
        }
        return result.stdout().lines()
                .map(line -> line.replaceAll("[^\\x20-\\x7E]", "").strip())
                .filter(name -> !name.isEmpty())
                .distinct()
                .toList();
    }

    /// Reads the remote probe's answer: the first line is the home, then a
    /// `tmux x.y` line and a `x.y.z (Claude Code)` line, each missing when
    /// its tool is. No line at all means ssh itself failed.
    static Verdict remoteVerdict(SshCommandRunner.SshResult result, String host) {
        List<String> lines = result.stdout().lines().map(String::strip)
                .filter(line -> !line.isEmpty()).toList();
        if (lines.isEmpty()) {
            return new Verdict(null, "Failed: " + firstLine(result.stderr()));
        }
        return toolsVerdict(lines.getFirst(), versionLine(lines, "tmux "),
                versionLine(lines, "(Claude Code)"), true, "on " + host);
    }

    /// This machine's check: `tmux -V` (not on Windows, where a local
    /// session is a Windows Terminal tab) and `claude --version`, each
    /// resolved like the local launcher resolves them — a GUI app's `PATH`
    /// is thinner than the login shell's.
    public static Verdict localVerdict(LocalCommandRunner runner, String home, boolean onWindows) {
        String tmux = onWindows ? null
                : versionLine(runner.run(List.of(RequiredTools.resolve("tmux"), "-V")), "tmux ");
        List<String> claude = onWindows ? List.of("cmd", "/c", "claude", "--version")
                : List.of(RequiredTools.resolve("claude"), "--version");
        return toolsVerdict(home, tmux, versionLine(runner.run(claude), "(Claude Code)"),
                !onWindows, "on this machine");
    }

    static Verdict toolsVerdict(String home, @Nullable String tmux, @Nullable String claude,
            boolean needTmux, String where) {
        List<String> missing = new ArrayList<>();
        if (needTmux && tmux == null) {
            missing.add("tmux");
        }
        if (claude == null) {
            missing.add("Claude Code (the claude command)");
        }
        if (!missing.isEmpty()) {
            return new Verdict(null, "Missing " + where + ": " + String.join(" and ", missing)
                    + ". Install it and press Next again.");
        }
        String claudeVersion = claude.replace("(Claude Code)", "").strip();
        return new Verdict(new Probe(home, tmux, claudeVersion),
                "OK: " + (tmux == null ? "" : tmux + ", ") + "Claude Code " + claudeVersion + " " + where);
    }

    private static @Nullable String versionLine(LocalCommandRunner.LocalResult result, String mark) {
        return result.ok() ? versionLine(result.stdout().lines().map(String::strip).toList(), mark) : null;
    }

    private static @Nullable String versionLine(List<String> lines, String mark) {
        return lines.stream().filter(line -> line.contains(mark)).findFirst().orElse(null);
    }

    private static String firstLine(String text) {
        String stripped = text.strip();
        int newline = stripped.indexOf('\n');
        return stripped.isEmpty() ? "no answer" : newline < 0 ? stripped : stripped.substring(0, newline);
    }

    /// Shows the wizard and blocks until it is finished or cancelled. `ssh`
    /// runs the connection check and `localCheck` this machine's (both off
    /// the FX thread); `tools` runs the recommended tools' probes and
    /// installers, on the remote or — host `TmuxHost.LOCAL` — on this
    /// machine, with a timeout fit for a download. `localParent` is where a
    /// local workspaces root is proposed (`user.home`), `onWindows` picks
    /// the wording for local sessions (Windows Terminal, no tmux mirror).
    /// `offerClone` is false when the wizard is re-run from the menu: a
    /// `git clone` into the existing task directory would refuse anyway;
    /// `prefill` carries the configuration such a re-run starts from, and
    /// `extension` what the browser-extension page shows.
    public static Optional<Result> show(SshCommandRunner ssh, Supplier<Verdict> localCheck,
            SshCommandRunner tools, String localParent, boolean onWindows, boolean offerClone,
            Prefill prefill, Extension extension) {
        Dialog<Result> dialog = new Dialog<>();
        dialog.setTitle(offerClone ? "Welcome to ContextSwitcher" : "ContextSwitcher setup");
        dialog.setGraphic(new ImageView(AppIcon.image(48)));
        dialog.setResizable(true);
        ButtonType back = new ButtonType("Back", ButtonBar.ButtonData.BACK_PREVIOUS);
        ButtonType next = new ButtonType("Next", ButtonBar.ButtonData.NEXT_FORWARD);
        // OTHER sits between Next and Cancel in every platform's button order.
        ButtonType checkup = new ButtonType("Finish and check up", ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(back, next, checkup, ButtonType.CANCEL);
        Button backButton = (Button) dialog.getDialogPane().lookupButton(back);
        Button nextButton = (Button) dialog.getDialogPane().lookupButton(next);
        Button checkupButton = (Button) dialog.getDialogPane().lookupButton(checkup);
        checkupButton.setId("setup-checkup");
        checkupButton.setTooltip(new javafx.scene.control.Tooltip(
                "Once the first project is set up, its session is asked: " + CHECKUP_PROMPT));
        nextButton.setDefaultButton(true);

        // Page 1: where sessions run.
        ToggleGroup where = new ToggleGroup();
        RadioButton remoteChoice = new RadioButton("On a remote machine, over ssh");
        remoteChoice.setId("setup-remote");
        RadioButton localChoice = new RadioButton("On this machine");
        localChoice.setId("setup-local");
        remoteChoice.setToggleGroup(where);
        localChoice.setToggleGroup(where);
        // A re-run starts from the configuration: a configured remote is the
        // remote choice with its host; none at all means local sessions.
        RadioButton wslChoice = new RadioButton("In WSL on this machine");
        wslChoice.setId("setup-wsl");
        wslChoice.setToggleGroup(where);
        // WSL is a Windows feature; elsewhere the choice would only confuse.
        // [impl->dsn~wsl-sessions~1]
        boolean offerWsl = onWindows;
        RadioButton configured = prefill.host() == null ? localChoice
                : offerWsl && TmuxHost.isWsl(prefill.host()) ? wslChoice : remoteChoice;
        (offerClone && prefill.host() == null ? remoteChoice : configured).setSelected(true);
        Label localVerdict = new Label();
        localVerdict.setId("setup-local-verdict");
        localVerdict.setWrapText(true);
        boolean[] localOk = {false};
        // This machine's tools, checked in the background as soon as the
        // wizard opens; `onOk` runs (on the FX thread) after a success.
        java.util.function.Consumer<Runnable> runLocalCheck = onOk -> {
            localVerdict.setText("Checking this machine …");
            Thread.ofVirtual().start(() -> {
                Verdict verdict = localCheck.get();
                Platform.runLater(() -> {
                    localOk[0] = verdict.ok();
                    localVerdict.setText(verdict.message());
                    if (verdict.ok()) {
                        onOk.run();
                    }
                });
            });
        };
        runLocalCheck.accept(() -> { });
        List<Node> page1Rows = new ArrayList<>(List.of(
                remoteChoice,
                muted("A Linux box with tmux and Claude Code, reached through your ~/.ssh/config. "
                        + "Sessions survive closing the laptop; the app mirrors them live.")));
        if (offerWsl) {
            page1Rows.add(wslChoice);
            page1Rows.add(muted("A Linux distribution on this machine, reached through wsl.exe "
                    + "instead of ssh. Sessions live in WSL's own tmux and are mirrored like a "
                    + "remote one; no sshd to set up."));
        }
        page1Rows.add(localChoice);
        page1Rows.add(muted(onWindows
                ? "Claude runs in the app's own terminal pane; closing the app ends it, resume brings it back."
                : "Claude runs in a local tmux window, mirrored like a remote one."));
        page1Rows.add(localVerdict);
        VBox page1 = page("Where do your Claude sessions run?", page1Rows.toArray(new Node[0]));

        // Page 2: the remote, checked.
        TextField host = new TextField(prefill.host() == null ? "" : prefill.host());
        host.setId("setup-host");
        host.setPromptText("devbox  or  me@host.example.org");
        host.setPrefColumnCount(30);
        Button test = new Button("Test connection");
        test.setId("setup-test");
        test.getStyleClass().add(Styles.SMALL);
        Label verdict = new Label();
        verdict.setId("setup-verdict");
        verdict.setWrapText(true);
        // The distribution, editable so a listing that fails (or a name the
        // UTF-16 listing mangles) still leaves the field usable; blank means
        // whichever distribution `wsl.exe` starts by default.
        // [impl->dsn~wsl-sessions~1]
        ComboBox<String> distro = new ComboBox<>();
        distro.setId("setup-distro");
        distro.setEditable(true);
        distro.getStyleClass().add(Styles.SMALL);
        distro.setPromptText("(the default distribution)");
        if (offerWsl) {
            distro.getItems().setAll(wslDistros(new LocalCommandRunner()));
        }
        if (prefill.host() != null && TmuxHost.isWsl(prefill.host())) {
            distro.setValue(TmuxHost.distro(prefill.host()));
        }
        @Nullable String[] remoteHome = {null};
        boolean[] verified = {false};
        // The host the answers are about: the pseudo-host for this machine, a
        // `wsl:<distro>` one for WSL, the typed ssh destination otherwise.
        // [impl->dsn~wsl-sessions~1]
        Supplier<String> sessionHost = () -> localChoice.isSelected() ? TmuxHost.LOCAL
                : wslChoice.isSelected()
                        ? TmuxHost.wslHost(distro.getEditor().getText())
                        : host.getText().strip();
        // The check, off the FX thread; `onOk` runs (on it) after a success.
        java.util.function.Consumer<Runnable> runTest = onOk -> {
            String target = sessionHost.get();
            test.setDisable(true);
            verdict.setText(wslChoice.isSelected() ? "Checking " + target + " …"
                    : "Connecting to " + target + " …");
            Thread.ofVirtual().start(() -> {
                Verdict outcome = remoteVerdict(ssh.run(target, REMOTE_PROBE), target);
                Platform.runLater(() -> {
                    test.setDisable(false);
                    verified[0] = outcome.ok();
                    remoteHome[0] = outcome.ok() ? outcome.probe().home() : null;
                    verdict.setText(outcome.ok() ? outcome.message() : outcome.message()
                            + (wslChoice.isSelected()
                                    ? "\nNext tries again; check `wsl -l -v` in a terminal if the "
                                            + "distribution itself cannot be reached."
                                    : "\nNext tries again; cancel and fix ~/.ssh/config if the "
                                            + "connection itself keeps failing."));
                    if (outcome.ok()) {
                        onOk.run();
                    }
                });
            });
        };
        test.setOnAction(event -> runTest.accept(() -> { }));
        host.textProperty().addListener((obs, old, text) -> {
            verified[0] = false;
            verdict.setText("");
        });
        VBox sshRows = new VBox(8,
                new Label("ssh destination (an alias from ~/.ssh/config, or user@host)"),
                new HBox(6, host, test),
                muted("Key-based access is required — the app never asks for a password. "
                        + "The check runs tmux -V and claude --version there."));
        Button testWsl = new Button("Test");
        testWsl.setId("setup-test-wsl");
        testWsl.getStyleClass().add(Styles.SMALL);
        testWsl.setOnAction(event -> runTest.accept(() -> { }));
        VBox wslRows = new VBox(8,
                new Label("WSL distribution"),
                new HBox(6, distro, testWsl),
                muted("Leave it empty for the default distribution. The check runs tmux -V and "
                        + "claude --version inside it, through wsl.exe — no ssh, no password."));
        // One page, two field groups: which questions belong to the chosen
        // transport is decided when the page is entered, so the page count
        // and the Back/Next skipping stay as they were.
        // [impl->dsn~wsl-sessions~1]
        Runnable showTransport = () -> {
            boolean wsl = wslChoice.isSelected();
            sshRows.setVisible(!wsl);
            sshRows.setManaged(!wsl);
            wslRows.setVisible(wsl);
            wslRows.setManaged(wsl);
        };
        distro.getEditor().textProperty().addListener((obs, old2, text) -> {
            verified[0] = false;
            verdict.setText("");
        });
        VBox page2 = page("Which machine?", sshRows, wslRows, verdict);

        // Page 3: the recommended tools, one row each — probed when the page
        // is entered, installed on its button, re-probed after.
        // [impl->dsn~recommended-tools~1]
        VBox toolRows = new VBox(6);
        @Nullable String[] rmHome = {null};
        Runnable probeTools = () -> {
            boolean local = localChoice.isSelected();
            String hostName = sessionHost.get();
            String home = local ? localParent : remoteHome[0] == null ? "~" : remoteHome[0];
            toolRows.getChildren().clear();
            rmHome[0] = null;
            for (RecommendedTools.Tool tool : local ? RecommendedTools.local()
                    : RecommendedTools.remote(prefill.refactoringMinerHome())) {
                Label status = new Label("Checking …");
                status.setId("setup-tool-" + tool.key());
                status.setWrapText(true);
                Button install = new Button("Install");
                install.setId("setup-install-" + tool.key());
                install.getStyleClass().add(Styles.SMALL);
                install.setDisable(true);
                // The probe, or the install followed by its own version print.
                java.util.function.Consumer<Boolean> run = installing -> {
                    install.setDisable(true);
                    status.setText(installing ? "Installing …" : "Checking …");
                    Thread.ofVirtual().start(() -> {
                        List<String> argv = installing ? tool.install() : tool.probe();
                        SshCommandRunner.SshResult result = installing && tool.installInput() != null
                                ? tools.runWithInput(hostName, argv, tool.installInput())
                                : tools.run(hostName, argv);
                        String version = RecommendedTools.version(result.exitCode(), result.stdout());
                        Platform.runLater(() -> {
                            // Installed: nothing to offer — the installers do not update.
                            install.setDisable(version != null);
                            install.setVisible(version == null);
                            install.setManaged(version == null);
                            if (version != null) {
                                status.setText("OK: " + version);
                                if (tool.key().equals("refactoringminer")) {
                                    // Found where configured, or (installed) at
                                    // the default — the install ignores the setting.
                                    rmHome[0] = !installing && prefill.refactoringMinerHome() != null
                                            ? prefill.refactoringMinerHome()
                                            : RefactoringMinerCommands.REMOTE_HOME.replace("$HOME", home);
                                }
                            } else if (installing) {
                                status.setText("Install failed: " + firstLine(result.stderr()));
                            } else {
                                status.setText("Not installed");
                            }
                        });
                    });
                };
                install.setOnAction(event -> run.accept(true));
                Label name = new Label(tool.label());
                name.getStyleClass().add(Styles.TEXT_BOLD);
                Label why = muted(tool.why());
                HBox line = new HBox(8, name, status, install);
                line.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                toolRows.getChildren().addAll(line, why);
                run.accept(false);
            }
        };
        VBox page3 = page("Recommended for the sessions",
                muted("None of these is required. Each is installed into the home directory "
                        + "of the session host with one click; Next continues either way."),
                toolRows);

        // Page 4: an existing task repository.
        // Page 4: the browser extension — the one part the app cannot
        // install itself; the port and token are what its options page needs.
        String browserName = extension.browser() == Browser.CHROME ? "Chrome" : "Firefox";
        TextField token = new TextField(extension.token());
        token.setId("setup-ws-token");
        token.setEditable(false);
        token.setPrefColumnCount(32);
        Button copyToken = new Button("Copy");
        copyToken.getStyleClass().add(Styles.SMALL);
        copyToken.setOnAction(event -> {
            javafx.scene.input.ClipboardContent clip = new javafx.scene.input.ClipboardContent();
            clip.putString(extension.token());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(clip);
        });
        Button installNotes = new Button("Install notes for " + browserName + "…");
        installNotes.setId("setup-extension-notes");
        installNotes.getStyleClass().add(Styles.SMALL);
        installNotes.setOnAction(event -> extension.openUrl().accept(installNotes(extension.browser())));
        Label connected = new Label();
        connected.setId("setup-extension-connected");
        Runnable refreshConnected = () -> {
            Browser browser = extension.connected().get();
            connected.setText(browser == null ? "" : "Connected: the "
                    + (browser == Browser.CHROME ? "Chrome" : "Firefox") + " extension is talking to the app.");
        };
        VBox page4 = page("Browser extension",
                muted("Focuses or opens a task's pages on switch, closes them on suspend, and "
                        + "selects the task whose tab you activate. There is one for Firefox and one "
                        + "for Chrome; the browser has to load it itself, so this page only helps."),
                muted("On its options page enter the port " + extension.port()
                        + " and this token:"),
                new HBox(6, token, copyToken),
                installNotes,
                connected);

        // Page 5: an existing task repository.
        TextField cloneUrl = new TextField();
        cloneUrl.setId("setup-clone-url");
        cloneUrl.setPromptText("git@github.com:me/my-tasks.git  or  https://…  (leave empty to start fresh)");
        cloneUrl.setPrefColumnCount(40);
        VBox page5 = page("Already using ContextSwitcher on another machine?",
                new Label("Clone URL of your task repository"), cloneUrl,
                muted("The tasks directory is cloned from it, so your categories and tasks come "
                        + "along. Leave it empty on a first install."));

        // Page 6: the first project.
        TextField repoUrl = new TextField();
        repoUrl.setId("setup-repo-url");
        repoUrl.setPromptText("https://github.com/org/project  (optional)");
        repoUrl.setPrefColumnCount(40);
        String clip = javafx.scene.input.Clipboard.getSystemClipboard().getString();
        if (clip != null && TaskFileParser.repoName(clip) != null) {
            repoUrl.setText(clip.strip());
        }
        TextField root = new TextField();
        root.setId("setup-root");
        root.setPrefColumnCount(40);
        boolean[] rootEdited = {false};
        root.setOnKeyTyped(event -> rootEdited[0] = true);
        Runnable followUrl = () -> {
            String name = TaskFileParser.repoName(repoUrl.getText());
            if (!rootEdited[0]) {
                String parent = prefill.workspacesParent() != null ? prefill.workspacesParent()
                        : localChoice.isSelected() ? localParent
                                : remoteHome[0] == null ? "~" : remoteHome[0];
                // Only a session on Windows itself has Windows paths; a WSL
                // one lives on the Linux side of the boundary.
                String separator = localChoice.isSelected() && onWindows ? "\\" : "/";
                root.setText(name == null ? "" : parent + separator + name + "-workspaces");
            }
        };
        repoUrl.textProperty().addListener((obs, old, text) -> followUrl.run());
        ComboBox<RepoType> repoType = new ComboBox<>();
        repoType.getItems().setAll(RepoType.values());
        repoType.setValue(RepoType.WRITE_ACCESS);
        repoType.getStyleClass().add(Styles.SMALL);
        VBox page6 = page("Your first project",
                new Label("Repository URL"), repoUrl,
                new Label("Workspaces root (the primary clone and per-task worktrees go under it)"),
                root,
                new Label("How you work with the repository"), repoType,
                muted("A category is created for it and a Claude session sets the root up: "
                        + "clones the repository and writes its CLAUDE.md. Leave the URL empty "
                        + "to add categories later from the toolbar."));

        List<Node> pages = List.of(page1, page2, page3, page4, page5, page6);
        StackPane content = new StackPane(page1);
        content.setPrefWidth(520);
        dialog.getDialogPane().setContent(content);
        int[] current = {0};
        // The remote page is skipped for local sessions; the tools page also
        // on Windows, where the installers (sh scripts) cannot run; the clone
        // page on a re-run, when the task directory is no longer empty.
        java.util.function.IntPredicate skipped = index ->
                index == 1 && localChoice.isSelected()
                        || index == 2 && localChoice.isSelected() && onWindows
                        || index == 4 && !offerClone;
        int last = pages.size() - 1;
        Runnable render = () -> {
            content.getChildren().setAll(pages.get(current[0]));
            backButton.setDisable(current[0] == 0);
            nextButton.setText(current[0] == last ? "Finish" : "Next");
            checkupButton.setVisible(current[0] == last);
            checkupButton.setManaged(current[0] == last);
            if (current[0] == 1) {
                showTransport.run();
            }
            if (current[0] == 2) {
                probeTools.run();
            }
            if (current[0] == 3) {
                refreshConnected.run();
            }
            if (current[0] == last) {
                followUrl.run();
            }
            // A dialog sizes itself to its content once, at show; a taller
            // page would otherwise push its buttons out of the window.
            javafx.scene.Scene scene = dialog.getDialogPane().getScene();
            if (scene != null && scene.getWindow() != null) {
                scene.getWindow().sizeToScene();
            }
            Platform.runLater(() -> (current[0] == 1
                    ? (wslChoice.isSelected() ? (Node) distro : host) : current[0] == 4 ? cloneUrl
                    : current[0] == last ? repoUrl : (Node) nextButton).requestFocus());
        };
        nextButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> current[0] == 1 && remoteChoice.isSelected() && host.getText().isBlank()
                        || current[0] == last && !repoUrl.getText().isBlank()
                                && (TaskFileParser.repoName(repoUrl.getText()) == null
                                        || root.getText().isBlank()),
                host.textProperty(), repoUrl.textProperty(), root.textProperty(),
                content.getChildren()));
        // The checkup runs in the first project's setup session, so it needs one.
        checkupButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> TaskFileParser.repoName(repoUrl.getText()) == null || root.getText().isBlank(),
                repoUrl.textProperty(), root.textProperty()));
        Runnable forward = () -> {
            int index = current[0] + 1;
            while (skipped.test(index)) {
                index++;
            }
            current[0] = index;
            render.run();
        };
        // Next and Back stay inside the dialog: the ACTION is consumed, the
        // page swapped; only the last page's Finish lets the dialog close.
        backButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            int index = current[0] - 1;
            while (skipped.test(index)) {
                index--;
            }
            current[0] = index;
            render.run();
        });
        nextButton.addEventFilter(ActionEvent.ACTION, event -> {
            if (current[0] == last) {
                return;
            }
            event.consume();
            if (current[0] == 0 && localChoice.isSelected() && !localOk[0]) {
                // Same rule as the remote page: local sessions need tmux and
                // Claude here, and a missing one is better found now than at
                // the first task's empty terminal.
                runLocalCheck.accept(forward);
                return;
            }
            if (current[0] == 1 && !verified[0]) {
                // The check is the point of the page: Next runs it and only
                // a success moves on — the verdict says what to fix otherwise.
                runTest.accept(forward);
                return;
            }
            forward.run();
        });
        render.run();
        dialog.setResultConverter(button -> button != next && button != checkup ? null
                : new Result(localChoice.isSelected() ? null : sessionHost.get(),
                        blankToNull(cloneUrl.getText()), blankToNull(repoUrl.getText()),
                        blankToNull(repoUrl.getText()) == null ? null : blankToNull(root.getText()),
                        repoType.getValue(), rmHome[0], button == checkup));
        return dialog.showAndWait();
    }

    private static @Nullable String blankToNull(String text) {
        return text.isBlank() ? null : text.strip();
    }

    private static VBox page(String heading, Node... children) {
        Label title = new Label(heading);
        title.getStyleClass().add(Styles.TITLE_4);
        List<Node> nodes = new ArrayList<>();
        nodes.add(title);
        nodes.addAll(List.of(children));
        VBox box = new VBox(8);
        box.getChildren().setAll(nodes);
        box.setPadding(new Insets(8));
        return box;
    }

    private static Label muted(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add(Styles.TEXT_MUTED);
        return label;
    }
}
