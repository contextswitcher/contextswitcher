package com.contextswitcher.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import atlantafx.base.theme.Styles;
import com.contextswitcher.local.AppUpdate;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.jspecify.annotations.Nullable;

/// The main toolbar's update button and what hangs off it: the pending-change
/// badge, the tooltip listing the news, and the "What's new" window its click
/// opens — with the fetch behind "Checking remote …" and *Restart to update*.
/// `Main` owns the git calls and feeds the answers in; `MainWindow` only places
/// the button.
// [impl->dsn~restart-to-update~10]
// [impl->dsn~whats-new-upstream~7]
final class UpdateNews {

    private final Button button;

    /// The pending change count — what the click shows, not [#commits] — as a
    /// badge on the update glyph; hidden while nothing is pending.
    private final Badge badge;

    /// The main window, the What's new window's owner; null before it is shown.
    private final Supplier<@Nullable Stage> owner;

    /// The status bar's message line.
    private final Consumer<String> status;

    /// Opens a changelog link with the OS protocol handler.
    private final Consumer<String> openUrl;

    /// Whether [#showUpdateAvailable] has already turned the button blue — the
    /// button is always visible, so its visibility cannot say it.
    private boolean updateAvailable;

    /// The commit count the latest [#showUpdateAvailable] reported, for the
    /// tooltip.
    private int commits;

    /// The pending bullets and the upstream commit as [#showPendingNews] last
    /// saw them, and what makes them old — so the button can list them in its
    /// tooltip and open the window without a second projection.
    private List<WhatsNew.Item> pendingNews = List.of();
    private @Nullable String pendingUpstream;
    private @Nullable Runnable announcePending;

    /// Fetches and re-projects the news off the FX thread, answering through
    /// [#newsChecked]; `Main` installs it, and it is what the window waits for.
    /// Null before the update check is scheduled (an app started from a zip
    /// never gets one).
    private @Nullable Runnable onCheckRemote;

    /// The controls of the open "What's new" window — null while none is
    /// open. [#newsChecked] fills the body in and arms the restart once the
    /// fetch behind "Checking remote …" has answered.
    private @Nullable Stage newsDialog;
    private @Nullable BorderPane newsRoot;
    private @Nullable Button newsLater;
    private @Nullable Button newsRestart;
    private @Nullable Node newsChecking;
    /// True while the open window's fetch has not answered yet.
    private final BooleanProperty newsCheckPending = new SimpleBooleanProperty();
    /// Set by `Main`: a task creation is running, which a restart would cut off.
    // [impl->dsn~busy-while-creating~1]
    private ObservableBooleanValue restartBlocked = new SimpleBooleanProperty();

    /// `button` is the toolbar's icon button, which this takes over: its
    /// graphic gains the badge, its action opens the window.
    UpdateNews(Button button, Supplier<@Nullable Stage> owner, Consumer<String> status,
            Consumer<String> openUrl) {
        this.button = button;
        this.owner = owner;
        this.status = status;
        this.openUrl = openUrl;
        this.badge = new Badge(button);
        // Not the restart itself: the count that revealed this button is up to
        // five minutes old, so the click fetches once more and shows what is
        // waiting — the restart is the press in that window.
        button.setOnAction(event -> showWhatsNew(newsTitle(pendingNews, pendingUpstream), pendingNews, true));
        refreshTooltip();
    }

    /// The update button, for the main toolbar. Always there — a button that
    /// appears on an update check shifts every icon beside it; the glyph
    /// turning blue says the same thing without the toolbar hopping.
    Button button() {
        return button;
    }

    /// Paints the always-present button's glyph in the app's own blue
    /// (`update-available` in main.css, not the muted icon gray it sits in
    /// while there is nothing to get) and says so once in the status bar; the
    /// restart itself is the user's click, never automatic — a switch in
    /// progress or an unsaved note must not be interrupted. Every poll updates
    /// the tooltip's commit count; the recolour and the message happen once.
    void showUpdateAvailable(int commits) {
        this.commits = commits;
        if (updateAvailable) {
            refreshTooltip();
            return;
        }
        this.updateAvailable = true;
        if (button.getGraphic() instanceof StackPane graphic) {
            graphic.getChildren().getFirst().getStyleClass().add("update-available");
        }
        refreshTooltip();
        status.accept("A new version is available — restart to update.");
    }

    /// Records the pending changelog bullets, the upstream commit (`upstream`,
    /// null while nothing is pushed) and `announce`, which makes them old; the
    /// tooltip lists them. Never steals focus, unlike a window. FX thread.
    void showPendingNews(List<WhatsNew.Item> items, @Nullable String upstream, Runnable announce) {
        pendingNews = items;
        pendingUpstream = upstream;
        announcePending = announce;
        refreshTooltip();
    }

    /// Installs the fetch-and-re-project the window waits on ("Checking
    /// remote …"); `Main` owns the git call and answers through [#newsChecked].
    void setOnCheckRemote(Runnable check) {
        this.onCheckRemote = check;
    }

    /// The tooltip: the pending bullets under the window's heading, then
    /// "restart to update" once a newer version exists.
    private void refreshTooltip() {
        // Badge and window title count the same thing; with nothing pending
        // (merge-only updates, or news already read) there is no badge, never a 0.
        badge.show(pendingNews.size());
        List<String> parts = new ArrayList<>();
        if (!pendingNews.isEmpty()) {
            parts.add(newsTitle(pendingNews, pendingUpstream) + ":\n"
                    + pendingNews.stream().map(it -> "• " + WhatsNew.shortBy(it.by()) + ": " + it.lines().getFirst().replace("**", ""))
                            .collect(Collectors.joining("\n")));
        }
        if (updateAvailable) {
            // [impl->dsn~restart-label-by-launch~1]
            boolean looped = AppUpdate.startedByLoop();
            parts.add("A new version is available (%d commit%s) — %s. %s"
                    .formatted(commits, commits == 1 ? "" : "s",
                            AppUpdate.actionLabel(looped).toLowerCase(Locale.ROOT), AppUpdate.actionHint(looped)));
        }
        if (parts.isEmpty()) {
            parts.add("Check for updates — restart to update when one is available.");
        }
        button.setTooltip(new Tooltip(String.join("\n\n", parts)));
    }

    /// The heading both the tooltip and the window carry: how many bullets are
    /// pending since the user last looked — the announced copy they are counted
    /// against has no commit, so no commit is named as their start — and, when
    /// the upstream is another commit, where it is (`upstream`).
    static String newsTitle(List<WhatsNew.Item> items, @Nullable String upstream) {
        String count = items.isEmpty() ? "What's new"
                : "What's new — " + items.size() + " pending change" + (items.size() == 1 ? "" : "s")
                        + " since you last looked";
        return upstream == null ? count : count + " — now at " + upstream;
    }

    /// Makes the bullets on screen old — the announced copy is replaced and
    /// the tooltip and badge drop them. Runs on *Restart to update*, or once a
    /// check finds nothing to restart into; while an update waits, dismissing
    /// the window keeps them (field report 2026-09-13). FX thread.
    private void announceShownNews() {
        Runnable announce = announcePending;
        if (announce != null) {
            announce.run();
        }
        pendingNews = List.of();
        refreshTooltip();
    }

    /// Keeps *Restart to update* disabled while `busy`. FX thread.
    // [impl->dsn~busy-while-creating~1]
    void disableRestartWhile(ObservableBooleanValue busy) {
        restartBlocked = busy;
    }

    /// A non-modal window with pending changelog bullets (`WhatsNew.view`),
    /// a *Later* button that closes it and a *Restart to update* one. With
    /// `check` it opens on a fetch: an indeterminate bar says "Checking
    /// remote …" and the restart stays disabled until [#newsChecked] brings
    /// the answer, so nobody restarts into a version that is already stale.
    /// Owned by the main stage, so it stays with the app on a desktop switch.
    /// FX thread.
    void showWhatsNew(String title, List<WhatsNew.Item> items, boolean check) {
        Stage open = newsDialog;
        if (open != null) {
            // A second window would leave the first one's bar spinning with
            // nothing left to answer it.
            open.toFront();
            return;
        }
        Stage dialog = new Stage();
        dialog.initOwner(owner.get());
        Button later = new Button("Later");
        later.getStyleClass().add(Styles.SMALL);
        later.setCancelButton(true);
        later.setOnAction(event -> dialog.close());
        // [impl->dsn~restart-label-by-launch~1]
        boolean looped = AppUpdate.startedByLoop();
        Button restart = new Button(AppUpdate.actionLabel(looped));
        restart.getStyleClass().addAll(Styles.SMALL, Styles.ACCENT);
        // The restart is what makes the listed changes old, not the look at
        // them: a dismissed window keeps them for the next click.
        restart.setOnAction(event -> {
            announceShownNews();
            AppUpdate.requestRestart();
        });
        ProgressBar spinner = new ProgressBar();
        spinner.setPrefWidth(120);
        HBox checking = new HBox(8, spinner, new Label("Checking remote …"));
        checking.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(checking, Priority.ALWAYS);
        HBox buttons = new HBox(8, checking, later, restart);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(8, 16, 12, 16));
        BorderPane root = new BorderPane(newsBody(items));
        Label hint = new Label(AppUpdate.actionHint(looped));
        hint.getStyleClass().add(Styles.TEXT_MUTED);
        hint.setWrapText(true);
        hint.setPadding(new Insets(8, 16, 0, 16));
        // Goes with the button: nothing to restart into, nothing to explain.
        hint.visibleProperty().bind(restart.visibleProperty());
        hint.managedProperty().bind(restart.managedProperty());
        root.setBottom(new VBox(hint, buttons));
        dialog.setTitle(title);
        dialog.setScene(new Scene(root));
        fitNewsHeight(dialog, root);
        newsDialog = dialog;
        newsRoot = root;
        newsLater = later;
        newsRestart = restart;
        newsChecking = checking;
        dialog.setOnHidden(event -> {
            if (newsDialog == dialog) {
                newsDialog = null;
                newsRoot = null;
                newsLater = null;
                newsRestart = null;
                newsChecking = null;
            }
        });
        Runnable fetch = check ? onCheckRemote : null;
        checking.setVisible(fetch != null);
        checking.setManaged(fetch != null);
        newsCheckPending.set(fetch != null);
        // [impl->dsn~busy-while-creating~1]
        restart.disableProperty().bind(newsCheckPending.or(restartBlocked));
        dialog.show();
        if (fetch != null) {
            fetch.run();
        }
    }

    /// The fetch behind "Checking remote …" has answered: the open window
    /// takes the freshly projected bullets and upstream commit, and *Restart to update*
    /// goes live — or, when the app is not `behind` its upstream, goes away
    /// and *Later* becomes *Close*: there is nothing to restart into.
    /// Nothing to do when the user closed the window meanwhile. FX thread.
    void newsChecked(List<WhatsNew.Item> items, @Nullable String upstream, boolean behind) {
        Stage dialog = newsDialog;
        BorderPane root = newsRoot;
        Button later = newsLater;
        Button restart = newsRestart;
        Node checking = newsChecking;
        if (dialog == null || root == null || later == null || restart == null || checking == null) {
            return;
        }
        root.setCenter(newsBody(items));
        fitNewsHeight(dialog, root);
        dialog.setTitle(newsTitle(items, upstream));
        checking.setVisible(false);
        checking.setManaged(false);
        newsCheckPending.set(false);
        restart.setVisible(behind);
        restart.setManaged(behind);
        later.setText(behind ? "Later" : "Close");
        // While an update waits, the changes stay pending until the restart —
        // dismissing the window must not empty the next click's list. With
        // nothing to restart into, having seen them is all there is to do.
        if (!behind) {
            announceShownNews();
        }
    }

    /// Sizes the news window to show its whole body — a changelog in a
    /// 650-pixel sheet was a thumb-sized scrollbar — capped at 90 % of the
    /// screen the main window sits on. Never shrinks a window already shown:
    /// the user may have enlarged it. Keeps the window on that screen when it
    /// grows.
    private void fitNewsHeight(Stage dialog, BorderPane root) {
        root.applyCss();
        double width = dialog.isShowing() ? root.getWidth() : 900;
        Stage main = owner.get();
        Screen onScreen = main == null ? Screen.getPrimary()
                : Screen.getScreensForRectangle(main.getX(), main.getY(), main.getWidth(), main.getHeight())
                        .stream().findFirst().orElse(Screen.getPrimary());
        Rectangle2D screen = onScreen.getVisualBounds();
        double height = Math.min(Math.max(650, root.prefHeight(width)), screen.getHeight() * 0.9);
        if (!dialog.isShowing()) {
            root.setPrefSize(width, height);
            return;
        }
        double grow = height - root.getHeight();
        if (grow > 0) {
            dialog.setHeight(dialog.getHeight() + grow);
            dialog.setY(Math.max(screen.getMinY(), Math.min(dialog.getY(), screen.getMaxY() - dialog.getHeight())));
        }
    }

    /// `WhatsNew.view`, or a line saying so when there is nothing to list —
    /// a blank sheet reads as a rendering failure.
    private Node newsBody(List<WhatsNew.Item> items) {
        if (items.isEmpty()) {
            Label empty = new Label("Nothing new since the running version.");
            empty.getStyleClass().add(Styles.TEXT_MUTED);
            empty.setPadding(new Insets(16));
            return empty;
        }
        return WhatsNew.view(items, openUrl);
    }
}
