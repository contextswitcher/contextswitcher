package com.contextswitcher.ui;

import atlantafx.base.theme.Styles;
import com.contextswitcher.ui.DockLanes.Lane;
import com.techsenger.shellfx.core.style.CoreIcons;
import com.techsenger.shellfx.material.icon.FontIconView;
import com.techsenger.shellfx.material.style.Density;
import com.techsenger.shellfx.material.style.StyleClasses;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.css.PseudoClass;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.HeaderBar;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// Moves a lane between its dock tab and a window of its own. The window's
/// title bar (JavaFX `HeaderBar` on an `EXTENDED` stage) holds the lane's title
/// on the left, then **Dock back**, maximize and close at the top right; the
/// empty space between them drags the window.
///
/// ShellFX has no tab-to-window gesture: dropping a tab outside the tab
/// headers only re-docks it, and its drag-and-drop is a press-drag-release
/// gesture that never leaves the window it started in. So both directions
/// are ours — **Pop out** in the pane, and in the window **Dock back**,
/// closing it, or dragging its title and releasing it over the main window.
// [impl->dsn~shell-layout~2]
final class PaneDetacher {

    /// How far the pointer must travel before a press on the title counts as a drag.
    private static final double DRAG_THRESHOLD = 8;

    /// The window's opacity while a released drag would dock the pane back.
    private static final double DOCK_HINT_OPACITY = 0.6;

    private static final double WIDTH = 640;
    private static final double HEIGHT = 560;

    private final Stage owner;
    private final DockLanes lanes;
    private final Lane lane;
    private final Node content;
    /// Told `true` when the lane pops out and `false` when it docks back — the
    /// pane hides its own Pop out button while its window has Dock back.
    private final Consumer<Boolean> onPoppedOut;
    /// The lane's own window while it is popped out, else null.
    private @Nullable Stage window;
    /// Set when the lane is hidden from the toolbar while popped out: the
    /// window closes, and the lane stays hidden instead of docking back.
    private boolean hideOnClose;

    /// `owner` is the main window; `content` is the lane's node, which moves
    /// between its tab and the window.
    PaneDetacher(Stage owner, DockLanes lanes, Lane lane, Node content, Consumer<Boolean> onPoppedOut) {
        this.owner = owner;
        this.lanes = lanes;
        this.lane = lane;
        this.content = content;
        this.onPoppedOut = onPoppedOut;
    }

    /// Whether the lane is in its own window right now.
    boolean isOpen() {
        return window != null;
    }

    void toggle() {
        Stage open = window;
        if (open != null) {
            open.close();
        } else {
            popOut();
        }
    }

    void toFront() {
        Stage open = window;
        if (open != null) {
            open.toFront();
        }
    }

    void closeWithoutDocking() {
        Stage open = window;
        if (open != null) {
            hideOnClose = true;
            open.close();
        }
    }

    private void popOut() {
        String name = lane.title();
        Stage stage = new Stage(StageStyle.EXTENDED);
        // Before the tab closes: a closed tab reports its lane hidden unless the
        // lane is popped out, and isOpen() is how that is told.
        window = stage;
        lanes.closeTab(lane);

        // The title: plain text. A single-tab ShellFX tab header here looked
        // like a stray tab; the text stands in for it, and dragging it onto
        // the main window docks the pane back like dragging a tab would.
        var title = new Label(name);
        title.setCursor(Cursor.OPEN_HAND);
        title.setTooltip(new Tooltip("Drag onto the main window to dock back"));

        // JavaFX's own header buttons ignore the app theme (neither CSS nor the
        // scene's colour scheme gave them matching colours): hidden, and drawn
        // as ShellFX draws the buttons of its own windows instead.
        HeaderBar.setPrefButtonHeight(stage, 0);
        Function<FontIconView, Button> windowButton = icon -> {
            Button button = new Button(null, icon);
            button.getStyleClass().addAll(Styles.FLAT, StyleClasses.SQUARE, StyleClasses.SIZE_S);
            button.setFocusTraversable(false);
            return button;
        };
        Button dockBack = new Button("Dock back");
        dockBack.getStyleClass().add(Styles.SMALL);
        dockBack.setFocusTraversable(false);
        dockBack.setTooltip(new Tooltip("Put " + name + " back into the main window"));
        dockBack.setOnAction(event -> stage.close());
        // No minimize button: the window is owned by the main one, and on
        // Windows an owned window has no taskbar button — minimized, it could
        // not be brought back (see the iconified guard below).
        FontIconView maximizeIcon = new FontIconView(CoreIcons.WINDOW_MAXIMIZE);
        Button maximize = windowButton.apply(maximizeIcon);
        maximize.getStyleClass().add("maximize-button");
        maximize.setTooltip(new Tooltip("Maximize"));
        maximize.setOnAction(event -> stage.setMaximized(!stage.isMaximized()));
        Button close = windowButton.apply(new FontIconView(CoreIcons.WINDOW_CLOSE));
        close.getStyleClass().add("close-button");
        close.setTooltip(new Tooltip("Close — docks " + name + " back"));
        close.setOnAction(event -> stage.close());

        // ShellFX's core.css styles a window title bar only along
        // .window-box > .title-pane > .title-bar (> .left-box / .right-box):
        // the same structure and style classes give this window the chrome of
        // ShellFX's own windows, in whatever theme is active.
        var leftBox = new HBox(title);
        leftBox.getStyleClass().add("left-box");
        var rightBox = new HBox(6, dockBack, maximize, close);
        rightBox.getStyleClass().add("right-box");
        HeaderBar header = new HeaderBar(leftBox, null, rightBox);
        header.getStyleClass().add("title-bar");
        var headerPane = new StackPane(header);
        headerPane.getStyleClass().add("title-pane");

        VBox.setVgrow(content, Priority.ALWAYS);
        VBox root = new VBox(headerPane, content);
        root.getStyleClass().addAll("window-box", Density.S.getStyleClass());
        PseudoClass maximizedState = PseudoClass.getPseudoClass("maximized");
        PseudoClass inactiveState = PseudoClass.getPseudoClass("inactive");
        stage.maximizedProperty().addListener((obs, was, maximized) -> {
            maximizeIcon.setIcon(maximized ? CoreIcons.WINDOW_RESTORE : CoreIcons.WINDOW_MAXIMIZE);
            root.pseudoClassStateChanged(maximizedState, maximized);
        });
        stage.focusedProperty().addListener(
                (obs, was, focused) -> root.pseudoClassStateChanged(inactiveState, !focused));
        Scene scene = new Scene(root, WIDTH, HEIGHT);
        // ShellFX attaches its CSS per window (core, the theme's variant,
        // material, icon fonts) and MainWindow adds main.css: take the main
        // window's resolved set instead of re-deriving it.
        scene.getStylesheets().setAll(owner.getScene().getStylesheets());

        // Owned: stays above the main window and goes away with it.
        stage.initOwner(owner);
        stage.setTitle(name);
        stage.getIcons().setAll(owner.getIcons());
        stage.setScene(scene);
        stage.setX(owner.getX() + (owner.getWidth() - WIDTH) / 2);
        stage.setY(owner.getY() + (owner.getHeight() - HEIGHT) / 2);
        installDragToDock(title, stage, owner);
        // Minimizing anyway (Win+Down) would strand the pane in a window with
        // no taskbar button: dock it back instead.
        stage.iconifiedProperty().addListener((obs, was, iconified) -> {
            if (iconified) {
                stage.close();
            }
        });
        // Every way the window goes — Dock back, the close button, a drag
        // released over the main window — docks the pane back.
        stage.setOnHidden(event -> {
            Logger.info("{} window closed; {}", name, hideOnClose ? "keeping it hidden" : "docking it back");
            dockBack();
        });
        onPoppedOut.accept(true);
        stage.show();
        Logger.info("{} popped out into its own window at {},{}", name, stage.getX(), stage.getY());
    }

    /// Drag the window's title and release it over the main window (outside
    /// this window) to dock back; the window dims while that would happen.
    private static void installDragToDock(Node handle, Stage stage, Stage owner) {
        double[] pressed = new double[2];
        boolean[] dragging = {false};
        handle.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            pressed[0] = event.getScreenX();
            pressed[1] = event.getScreenY();
            dragging[0] = false;
        });
        handle.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (!dragging[0] && Math.hypot(event.getScreenX() - pressed[0],
                    event.getScreenY() - pressed[1]) > DRAG_THRESHOLD) {
                dragging[0] = true;
            }
            if (dragging[0]) {
                stage.setOpacity(wouldDock(event, stage, owner) ? DOCK_HINT_OPACITY : 1);
            }
        });
        handle.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            boolean dock = dragging[0] && wouldDock(event, stage, owner);
            dragging[0] = false;
            stage.setOpacity(1);
            if (dock) {
                stage.close();
            }
        });
    }

    private static boolean wouldDock(MouseEvent event, Stage stage, Stage owner) {
        return contains(owner, event) && !contains(stage, event);
    }

    private static boolean contains(Stage stage, MouseEvent event) {
        return event.getScreenX() >= stage.getX() && event.getScreenX() < stage.getX() + stage.getWidth()
                && event.getScreenY() >= stage.getY() && event.getScreenY() < stage.getY() + stage.getHeight();
    }

    private void dockBack() {
        window = null;
        onPoppedOut.accept(false);
        if (hideOnClose) {
            hideOnClose = false;
            return;
        }
        lanes.reopen(lane);
    }
}
