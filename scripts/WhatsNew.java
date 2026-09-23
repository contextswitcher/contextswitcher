///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25
//DEPS org.openjfx:javafx-controls:26.0.2
//DEPS io.github.mkpaz:atlantafx-base:2.1.0
//DEPS org.jspecify:jspecify:1.0.1
//DEPS org.tinylog:tinylog-api:2.8.0
//DEPS org.tinylog:tinylog-impl:2.8.0
//JAVA_OPTIONS -Dtinylog.level=warn
// JavaFX loads glass/prism through System.load, which Java 25 flags (JEP 472).
// `javafx.graphics`, not ALL-UNNAMED: jbang resolves the //DEPS onto the module
// path, so the caller is that module — the app, which has JavaFX on the classpath,
// grants ALL-UNNAMED instead. Without this every `just run` prints four WARNING
// lines before the window (field report 2026-09-12: "OMG, what happens here?").
//JAVA_OPTIONS --enable-native-access=javafx.graphics
//SOURCES ../app/src/main/java/com/contextswitcher/ui/Themes.java
//SOURCES ../app/src/main/java/com/contextswitcher/ui/WhatsNew.java
//SOURCES ../core/src/main/java/com/contextswitcher/local/LocalCommandRunner.java
//FILES com/contextswitcher/ui/everforest-dark.css=../app/src/main/resources/com/contextswitcher/ui/everforest-dark.css
//FILES com/contextswitcher/ui/everforest-light.css=../app/src/main/resources/com/contextswitcher/ui/everforest-light.css

import atlantafx.base.theme.Styles;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import com.contextswitcher.ui.Themes;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// "What's new since you last ran the app" — personalized.
///
/// Compares the commit of the previous run (stored in `~/.contextswitcher/whats-new-last-commit`)
/// with `HEAD` and shows the CHANGELOG bullets that landed in between, grouped by author —
/// *changes by <name>* per colleague, then *changes by me* — the projection lives in the app's `com.contextswitcher.ui.WhatsNew`,
/// which the running app also uses for the news that arrives upstream while it runs.
/// Invoked from the `just` run recipes and the run loops; a first run only records the commit.
/// "Run" (or closing the window) exits 0 and the recipe launches the app; "Cancel run" exits 1
/// and stops it. Themed like the app: the same `Themes` class, fed the `theme` from `settings.yaml`.
/// Not itself an `Application`: the JDK launcher would start the FX toolkit before `main`,
/// which fails on a headless machine that only wants `--stdout`.
public class WhatsNew {

    static final Path STATE = Path.of(System.getProperty("user.home"), ".contextswitcher", "whats-new-last-commit");

    static List<com.contextswitcher.ui.WhatsNew.Item> items = List.of();
    static String since = "";
    static String now = "";
    static String previousLast = "";
    static byte @Nullable [] previousAnnounced;

    public static void main(String[] args) throws Exception {
        String head = git("rev-parse", "HEAD").getFirst();
        String last = Files.exists(STATE) ? Files.readString(STATE).strip() : "";
        previousLast = last;
        Path announced = STATE.resolveSibling("whats-new-announced.md");
        previousAnnounced = Files.exists(announced) ? Files.readAllBytes(announced) : null;
        // Record the new position first: a crash below must not replay the same news forever.
        Files.createDirectories(STATE.getParent());
        Files.writeString(STATE, head + "\n");
        // ...and the app's announced copy with it. The running app shows its
        // pending-news dots for every bullet the current CHANGELOG.md holds
        // beyond that copy, and an update restart pulls a pile of them - so
        // without this the user reads the news in this window and then finds
        // the very same bullets waiting as dots (field report 2026-09-12:
        // nine pending changes, all of them just installed). Two markers for
        // one question; this keeps them agreeing.
        Files.copy(Path.of("CHANGELOG.md"), announced,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        if (last.isEmpty() || last.equals(head)) {
            return;
        }
        items = com.contextswitcher.ui.WhatsNew.collect(Path.of(""), last, head);
        if (items.isEmpty()) {
            return;
        }
        since = describe(last);
        now = describe(head);
        if (args.length > 0 && args[0].equals("--stdout") || java.awt.GraphicsEnvironment.isHeadless()) {
            System.out.print(com.contextswitcher.ui.WhatsNew.plainText(items));
            return;
        }
        // The recipe waits here for as long as the window is open, so the
        // terminal has to say what it is waiting for — a window that ends up
        // behind another one is otherwise indistinguishable from a hang
        // (field report 2026-09-12).
        System.out.println("Showing what's new: " + items.size() + " change"
                + (items.size() == 1 ? "" : "s") + " since " + since
                + ". Close the window to start ContextSwitcher.");
        Application.launch(Window.class);
        System.out.println("Popup closed — starting ContextSwitcher.");
    }

    /// Puts both markers back: a cancelled run was not read, so the next run shows the same news.
    static void unrecord() {
        try {
            if (previousLast.isEmpty()) {
                Files.deleteIfExists(STATE);
            } else {
                Files.writeString(STATE, previousLast + "\n");
            }
            Path announced = STATE.resolveSibling("whats-new-announced.md");
            if (previousAnnounced == null) {
                Files.deleteIfExists(announced);
            } else {
                Files.write(announced, previousAnnounced);
            }
        } catch (IOException e) {
            System.err.println("Could not restore what's-new state: " + e.getMessage());
        }
    }

    /// `<short sha> (<date> <time>)`, the status bar's format for the running commit.
    static String describe(String commit) throws IOException, InterruptedException {
        return git("show", "--no-patch", "--date=format:%Y-%m-%d %H:%M", "--format=%h (%cd)", commit).getFirst();
    }

    /// The `theme` value of `~/.contextswitcher/settings.yaml`, or the app's default.
    static String themePref() {
        try {
            for (String l : Files.readAllLines(STATE.resolveSibling("settings.yaml"))) {
                if (l.startsWith("theme:")) {
                    return l.substring(6).replaceAll("#.*", "").strip();
                }
            }
        } catch (IOException ignored) {
            // no settings yet: the default below
        }
        return "everforest";
    }

    public static class Window extends Application {

        @Override
        public void start(Stage stage) {
            Themes.apply(themePref());
            Button run = new Button("Run");
            run.getStyleClass().addAll(Styles.SMALL, Styles.ACCENT);
            run.setDefaultButton(true);
            run.setOnAction(e -> stage.close());
            Button cancel = new Button("Cancel run");
            cancel.getStyleClass().add(Styles.SMALL);
            cancel.setCancelButton(true);
            cancel.setOnAction(e -> {
                System.out.println("Run cancelled.");
                unrecord();
                System.exit(1);
            });
            HBox buttons = new HBox(8, cancel, run);
            buttons.setAlignment(Pos.CENTER_RIGHT);
            buttons.setPadding(new Insets(8, 16, 12, 16));
            BorderPane root = new BorderPane(
                    com.contextswitcher.ui.WhatsNew.view(items, url -> getHostServices().showDocument(url)));
            root.setBottom(buttons);
            stage.setTitle("What's new since " + since + " — now at " + now);
            stage.setScene(new Scene(root, 900, 650));
            // This window is a gate: the recipe waits for it before starting the
            // app, so a window that opens *behind* the others reads as a hang —
            // the terminal sits there and nothing seems to happen (field report
            // 2026-09-12: "But it hangs" / "ah, i did not see the popup").
            // Windows does not let a process that is not in the foreground raise
            // itself, and `toFront` alone is ignored there; always-on-top is what
            // actually lifts it, and it is the right behaviour for the few
            // seconds this gate lives.
            stage.setAlwaysOnTop(true);
            stage.show();
            stage.toFront();
            stage.requestFocus();
        }
    }

    static List<String> git(String... args) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>(List.of("git"));
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        List<String> out = p.inputReader().lines().toList();
        if (p.waitFor() != 0) {
            throw new IOException("git " + String.join(" ", args) + " failed");
        }
        return out;
    }
}
