package com.contextswitcher.ui;

import com.contextswitcher.ui.DockLanes.Lane;
import com.dlsc.gemsfx.infocenter.InfoCenterPane;
import com.dlsc.gemsfx.infocenter.InfoCenterView;
import com.techsenger.shellfx.core.DefaultShellContext;
import com.techsenger.shellfx.core.DefaultShellFxView;
import com.techsenger.shellfx.core.DefaultShellParams;
import com.techsenger.shellfx.core.DefaultShellPresenter;
import com.techsenger.shellfx.core.ShellFxView;
import com.techsenger.shellfx.core.history.InMemoryHistoryManager;
import com.techsenger.shellfx.core.registry.ControlRegistry;
import com.techsenger.shellfx.core.settings.AppearanceSettings;
import com.techsenger.shellfx.core.settings.DefaultAppearanceSettings;
import com.techsenger.shellfx.core.settings.ShellSettings;
import com.techsenger.shellfx.core.window.WindowContainerFxView;
import com.techsenger.shellfx.icons.Fonts;
import com.techsenger.shellfx.icons.IconStylesheetFactory;
import com.techsenger.shellfx.layout.dockhost.DockHostFxView;
import com.techsenger.shellfx.layout.dockhost.DockHostHistory;
import com.techsenger.shellfx.layout.dockhost.DockHostParams;
import com.techsenger.shellfx.layout.dockhost.DockHostPresenter;
import com.techsenger.shellfx.layout.dockhost.ModelNodeBuilder;
import com.techsenger.shellfx.layout.dockhost.TabDockFxView;
import com.techsenger.shellfx.material.icon.FontIconView;
import com.techsenger.shellfx.material.menu.DefaultMenuGroupName;
import com.techsenger.shellfx.material.menu.MenuGroupName;
import com.techsenger.shellfx.material.style.Density;
import com.techsenger.shellfx.material.style.IconStylesheets;
import com.techsenger.shellfx.material.theme.AtlantaFxTheme;
import com.techsenger.shellfx.material.theme.Theme;
import com.techsenger.shellfx.material.theme.ThemePalette;
import com.techsenger.shellfx.material.theme.ThemePalette16;
import com.techsenger.shellfx.material.theme.ThemePalette32;
import java.util.List;
import java.util.Map;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ToolBar;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// Hosts the main window's panes in a ShellFX shell (MADR 0032) — task list,
/// terminal, notes, configuration and queue as dockable tabs, a toolbar above the
/// dock host to show or hide them, the status bar below it. A thin adapter: the
/// panes stay the nodes `MainWindow` builds, with all their behavior; only the
/// container is ShellFX's. The lanes themselves are [DockLanes]; a [ConfigFormPane]
/// can additionally pop out of its tab into a window of its own ([PaneDetacher]).
// [impl->dsn~shell-layout~2]
public final class ShellFxHost {

    /// What [#host] built: the scene ShellFX created on the stage, and how to put
    /// a pane in front. `showConfiguration` answers an explicit request, so it
    /// reopens a closed configuration pane and raises its window when popped
    /// out. `infoCenter` slides notifications about background work in over the
    /// shell's right edge (MADR 0034).
    public record Hosted(Scene scene, Runnable showConfiguration, InfoCenterView infoCenter) {
    }

    /// What [#host] puts in the shell: the panes `MainWindow` builds, the status
    /// bar below the docks and the app-wide controls for the main toolbar.
    /// Named rather than positional — six nodes in a row are easy to swap.
    record Panes(Node tasks, Node terminal, Node notes, ConfigFormPane configuration,
            Node queue, Node statusBar, List<Node> appControls) {

        Panes {
            appControls = List.copyOf(appControls);
        }
    }

    private static final MenuGroupName<ShellFxView<?>> MAIN_MENU_GROUP =
            new DefaultMenuGroupName<>(ShellFxView.class, "Main");

    /// The style class every dock carries: `main.css` hides its tabs' × under
    /// it — one click beside a tab title closed a pane by accident, and the tab
    /// context menu still closes one.
    // [impl->dsn~shell-layout~2]
    private static final String DOCK_STYLE = "pane-dock";

    private ShellFxHost() {
    }

    /// ShellFX's Nord of the current lightness, except that its user-agent
    /// stylesheet is the one [Themes#apply] resolved — so ShellFX's own
    /// `setUserAgentStylesheet` installs the app's theme, not Nord over it.
    /// Both reads are live, so a theme switch needs no new instance.
    ///
    /// It poses as that Nord constant to ShellFX (`name`, `equals`, `hashCode`):
    /// ShellFX adds its per-theme sheets (`material-nord-dark.css`, …) only for a
    /// theme in its own set of constants, named by `name()`. As an enum of its own
    /// it matched none, and without those sheets `-color-bg-extra` was undefined —
    /// every context menu drew no background, and on Windows clicks fell through
    /// the popup to the window behind (field report 2026-09-14).
    // [impl->dsn~theme-select~8]
    private static final class AppTheme implements Theme {
        static final AppTheme INSTANCE = new AppTheme();

        private static Theme base() {
            return Themes.isDark() ? AtlantaFxTheme.NORD_DARK : AtlantaFxTheme.NORD_LIGHT;
        }

        @Override
        public String name() {
            return base().name();
        }

        @Override
        public boolean equals(@Nullable Object other) {
            return other == this || base().equals(other);
        }

        @Override
        public int hashCode() {
            return base().hashCode();
        }

        @Override
        public String getUserAgentStylesheet() {
            @Nullable String own = Themes.stylesheet();
            return own != null ? own : base().getUserAgentStylesheet();
        }

        @Override
        public int getBorderRadius() {
            return base().getBorderRadius();
        }

        @Override
        public Map<String, Integer> getColorsByName() {
            return base().getColorsByName();
        }

        @Override
        public String getFileName() {
            return base().getFileName();
        }

        @Override
        public ThemePalette32 getHighContrastPalette32() {
            return base().getHighContrastPalette32();
        }

        @Override
        public ThemePalette32 getLowContrastPalette32() {
            return base().getLowContrastPalette32();
        }

        @Override
        public String getName() {
            return base().getName();
        }

        @Override
        public ThemePalette getPalette() {
            return base().getPalette();
        }

        @Override
        public ThemePalette16 getSimplePalette16() {
            return base().getSimplePalette16();
        }

        @Override
        public Map<String, String> getWebStyle(Font font) {
            return base().getWebStyle(font);
        }

        @Override
        public boolean isDark() {
            return base().isDark();
        }
    }

    /// Builds the shell on `stage` (not yet shown). The caller keeps installing
    /// its scene filters and stylesheets on the returned scene, and shows the stage.
    static Hosted host(Application application, Stage stage, Panes panes) {
        FontIconView.setDefaultIconFont(Fonts.MATERIAL_DESIGN_ICONS.getFamily());
        IconStylesheets.addAll(IconStylesheetFactory.forAll());

        var appearance = new DefaultAppearanceSettings(null, Font.getDefault(),
                Font.font("Monospace", Font.getDefault().getSize()));
        // ShellFX sets the user-agent stylesheet itself: every top-level window
        // presenter hands its theme's URL to Application.setUserAgentStylesheet
        // in postInitialize, which runs after the two refreshes below when the
        // FX thread is busy at start — the window then came up in ShellFX's Nord
        // (or JavaFX's Modena where that URL did not load) with Everforest gone
        // (field reports 2026-09-13/14). So the theme it gets *is* ours: Nord of
        // the same lightness for the chrome it styles itself, our stylesheet URL
        // for what it sets on JavaFX. [impl->dsn~theme-select~8]
        appearance.setTheme(AppTheme.INSTANCE);
        appearance.setDensity(Density.S);
        ShellSettings settings = new ShellSettings() {
            @Override
            public AppearanceSettings getAppearance() {
                return appearance;
            }
        };

        var shellView = new AppShellView(application, stage);
        var context = new DefaultShellContext(settings, new InMemoryHistoryManager(),
                application.getHostServices());
        var shellPresenter = new DefaultShellPresenter<>(shellView, new DefaultShellParams(context));
        shellPresenter.initialize();
        // The shell binds Stage.title to its own title property; MainWindow sets
        // the title itself (category + live counts), and the shell's header shows
        // the menu bar, not the title — so the stage title goes back to us.
        stage.titleProperty().unbind();
        // ShellFX builds every TOP_LEVEL window as an EXTENDED stage with its own
        // HeaderBar title pane (menu bar, its own window buttons). The main window
        // keeps the standard OS frame instead: the style may be set again until the
        // stage is first shown, and the title pane — which only held our empty menu
        // bar — is dropped.
        stage.initStyle(StageStyle.DECORATED);
        Node titlePane = stage.getScene().getRoot().lookup(".title-pane");
        if (titlePane != null) {
            titlePane.setVisible(false);
            titlePane.setManaged(false);
        }

        // The side bars keep their default policy: they appear only while a dock
        // is minimized into them — which, with minimize off (see tidyDocks), is never.
        var dockHost = new DockHostFxView<>();
        new DockHostPresenter<>(dockHost, new DockHostParams(() -> context.getHistoryManager()
                .getOrCreateHistory(DockHostHistory.class, DockHostHistory::new))).initialize();

        TabDockFxView<?> left = tabDock(dockHost);
        TabDockFxView<?> center = tabDock(dockHost);
        TabDockFxView<?> rightTop = tabDock(dockHost);
        TabDockFxView<?> rightBottom = tabDock(dockHost);
        var lanes = new DockLanes(shellView, dockHost);
        Lane tasks = lanes.add("Tasks", panes.tasks());
        Lane terminal = lanes.add("Terminal", panes.terminal());
        Lane notesLane = lanes.add("Notes", panes.notes());
        Lane configuration = lanes.add("Configuration", panes.configuration().getRoot());
        Lane queueLane = lanes.add("Queue", panes.queue());
        lanes.open(tasks, left);
        lanes.open(terminal, center);
        lanes.open(notesLane, rightTop);
        lanes.open(configuration, rightTop);
        lanes.open(queueLane, rightBottom);
        rightTop.selectTab(0);
        // Focusing the queue's tab — clicking its header — puts the caret in the
        // add box (QueuePane forwards its root's focus there), as if the user had
        // clicked into it. Only a focus the user gives the tab pane: a tab
        // selected by a drag or a reopen leaves the keyboard where it was.
        queueLane.focusContentOnTabFocus();
        stage.getScene().focusOwnerProperty().addListener((obs, old, owner) -> lanes.focusContent());
        // No main area. ShellFX keeps a main area's pane even when every tab has
        // been dragged out of it (an empty split stays behind), and dragging the
        // dock that sits there by its grip throws (a ClassCastException in
        // DropPositionResolver.validate, ShellFX 2.0.0-SNAPSHOT). Without one,
        // every dock is an ordinary tab dock: one that loses its last tab closes
        // and its split collapses, so the tabs share whatever panes are left.
        // The proportions keep the three-lane layout the shell replaced (0.28 /
        // 0.64, notes 0.62 over the queue).
        dockHost.getComposer().applyModel(ModelNodeBuilder.root(Orientation.HORIZONTAL, s -> s
                .area(left, 0.28)
                .area(center, 0.36)
                .group(Orientation.VERTICAL, 0.36, g -> g
                        .area(rightTop, 0.62)
                        .area(rightBottom, 0.38))));
        shellView.getComposer().addWorkspace(dockHost);
        // The info center overlays the workspace: its pane takes the dock host's
        // place in the shell, so a notification slides in over the docks from
        // the right. Not around the scene root — ShellFX casts the parent of its
        // window view to its own WindowPane when it opens a child window, and a
        // root wrapped in an InfoCenterPane threw a ClassCastException there
        // (field report 2026-09-14). [impl->dsn~refresh-progress~2]
        InfoCenterPane infoCenter = wrapInPlace(dockHost.getNode());
        // "transparent" drops GemsFX's grey backdrop behind the notification cards.
        infoCenter.getInfoCenterView().getStyleClass().add("transparent");
        // ShellFX closes a dock that loses its last tab only when the tab leaves
        // through the dock's own removeTab. A tab dragged onto another dock's
        // header is moved by TabPanePro directly in the TabPane, so the emptied
        // dock stays behind as an empty pane. After every drag in the dock host,
        // tidy the docks — on the next pulse, once ShellFX has finished its own
        // drop handling.
        dockHost.getNode().addEventFilter(MouseEvent.MOUSE_RELEASED,
                event -> Platform.runLater(() -> tidyDocks(dockHost)));
        ToolBar mainToolBar = lanes.toolBar();
        // The app-wide controls MainWindow hands over (energy saver, browser
        // status, update, settings): right-aligned, left of the panes button.
        mainToolBar.getItems().addAll(mainToolBar.getItems().size() - 1, panes.appControls());
        shellView.addAboveWorkspace(mainToolBar);
        shellView.addBelowWorkspace(panes.statusBar());
        shellView.upgradeMenuBar();

        ConfigFormPane configPane = panes.configuration();
        var detacher = new PaneDetacher(stage, lanes, configuration, configPane.getRoot(),
                configPane::setDetached);
        configuration.setDetacher(detacher);
        configPane.setOnDetachToggle(detacher::toggle);

        // ShellFX set its own Nord stylesheet while initializing; ours (the
        // Everforest recolouring in particular) goes back on top — forced, since
        // JavaFX ignores a re-set of the same Nord URL. MainWindow.show forces it
        // once more when the window is up.
        Themes.refresh();
        stage.setWidth(1100);
        stage.setHeight(650);
        return new Hosted(stage.getScene(), () -> lanes.bringToFront(configuration),
                infoCenter.getInfoCenterView());
    }

    /// Puts `node` into an [InfoCenterPane] that takes its place in its parent —
    /// the shell's workspace slot, whatever container ShellFX keeps it in.
    // [impl->dsn~refresh-progress~2]
    private static InfoCenterPane wrapInPlace(Node node) {
        Parent parent = node.getParent();
        InfoCenterPane wrapper = new InfoCenterPane();
        switch (parent) {
            case BorderPane border when border.getCenter() == node -> {
                border.setCenter(null);
                wrapper.setContent(node);
                border.setCenter(wrapper);
            }
            case Pane pane -> {
                // The grow constraint lives on the child: without it the shell's
                // VBox gives the wrapper its preferred height only, and a
                // maximized window showed the panes over a black bottom.
                VBox.setVgrow(wrapper, VBox.getVgrow(node));
                HBox.setHgrow(wrapper, HBox.getHgrow(node));
                int index = pane.getChildren().indexOf(node);
                pane.getChildren().remove(index);
                wrapper.setContent(node);
                pane.getChildren().add(index, wrapper);
            }
            default -> throw new IllegalStateException(
                    "Workspace parent is " + (parent == null ? "null" : parent.getClass().getName()));
        }
        return wrapper;
    }

    /// Keeps every dock in the layout to the shared-pane model (see the filter in
    /// [#host]): no dock grip and no minimize button — also on the docks ShellFX
    /// creates itself when a tab is dropped, which come with both — and no dock
    /// that holds no tab. Minimized docks park their tabs (areTabsDetached) and
    /// would be left alone; ShellFX's drop placeholder is an empty dock too, but
    /// out of the scene by then.
    ///
    /// Minimize is dropped rather than fixed: ShellFX minimizes whole docks only
    /// (every tab in it goes to the side bar, which read as the wrong tab going
    /// along), and has no public per-tab minimize to build one on. Hiding a single
    /// lane is the toolbar's lanes menu instead.
    private static void tidyDocks(DockHostFxView<?> dockHost) {
        for (Object child : List.copyOf(dockHost.getComposer().getChildren())) {
            if (!(child instanceof TabDockFxView<?> dock)) {
                continue;
            }
            dock.getPresenter().setMinimizable(false);
            dock.getPresenter().setDraggable(false);
            // The docks ShellFX creates on a drop come without it.
            if (!dock.getNode().getStyleClass().contains(DOCK_STYLE)) {
                dock.getNode().getStyleClass().add(DOCK_STYLE);
            }
            if (dock.getNode().getTabs().isEmpty()
                    && !dock.getComposer().areTabsDetached()
                    && isAttached(dock)) {
                Logger.debug("Closing empty dock {}", dock);
                dockHost.getComposer().closeTabDock(dock);
            }
        }
    }

    /// A new dock in the shared-pane model's shape (see [#tidyDocks]).
    static TabDockFxView<?> tabDock(DockHostFxView<?> dockHost) {
        var dock = dockHost.getComposer().createTabDock();
        // No minimize either: whole-dock only in ShellFX (see tidyDocks).
        dock.getPresenter().setMinimizable(false);
        // No dock grip: the docks ShellFX creates on a tab drop come without one,
        // so a grip on the initial docks only looked like a stray handle that
        // vanished as soon as a tab moved. Tabs stay draggable, which is all the
        // shared-pane layout needs.
        dock.getPresenter().setDraggable(false);
        dock.getNode().getStyleClass().add(DOCK_STYLE);
        return dock;
    }

    static boolean isAttached(TabDockFxView<?> dock) {
        return dock.getNode().getScene() != null;
    }

    /// The shell's window view: ShellFX's default, with a component-tree walk
    /// that ends at the shell and room above and below the workspace for the
    /// main toolbar and the status bar.
    private static final class AppShellView extends DefaultShellFxView<DefaultShellPresenter<?>> {

        AppShellView(Application application, Stage stage) {
            super(application, stage, null, MAIN_MENU_GROUP, new ControlRegistry());
        }

        public class Composer extends DefaultShellFxView<DefaultShellPresenter<?>>.Composer {

            // Works around a ShellFX 2.0.0-SNAPSHOT (2026-09-09) bug: on every
            // focus change its window manager walks the component tree up
            // through getParent(), and a TOP_LEVEL window's getParent() throws
            // instead of ending the walk. The shell never has a parent.
            @Override
            public @Nullable WindowContainerFxView<?> getParent() {
                return null;
            }
        }

        @Override
        protected Composer createComposer() {
            return new Composer();
        }

        void addAboveWorkspace(Node node) {
            getContentBox().getChildren().add(0, node);
        }

        void addBelowWorkspace(Node node) {
            getContentBox().getChildren().add(node);
        }
    }
}
