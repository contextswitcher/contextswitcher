package com.contextswitcher.ui;

import atlantafx.base.theme.Styles;
import com.techsenger.shellfx.core.CloseCheckResult;
import com.techsenger.shellfx.core.ClosePreparationResult;
import com.techsenger.shellfx.core.ShellFxView;
import com.techsenger.shellfx.core.tab.AbstractTabFxView;
import com.techsenger.shellfx.core.tab.AbstractTabPresenter;
import com.techsenger.shellfx.core.tab.TabParams;
import com.techsenger.shellfx.core.tab.TabView;
import com.techsenger.shellfx.layout.dockhost.DockHostFxView;
import com.techsenger.shellfx.layout.dockhost.ModelNodeBuilder;
import com.techsenger.shellfx.layout.dockhost.TabDockFxView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jspecify.annotations.Nullable;
import tools.maran.svg.materialdesign.MDIInterface;
import tools.maran.svgnode.SvgNode;

/// The main window's panes in the dock host — one [Lane] each — and the toolbar
/// menu that shows or hides them. Closing a tab (any of the tab context menu's
/// close entries) hides its lane; checking its menu entry shows it again.
// [impl->dsn~shell-layout~2]
final class DockLanes {

    /// One pane of the main window and where it currently lives. The content
    /// node exists once, for the whole run: a closed tab only takes it out of
    /// the layout. Its state is private to [DockLanes]; a [PaneDetacher] works
    /// through [DockLanes#closeTab] and [DockLanes#reopen].
    static final class Lane {

        private final String title;
        private final Node content;
        private final CheckMenuItem item;
        /// The tab showing the lane, null while the lane is hidden or popped out.
        private @Nullable NodeTabFxView tab;
        /// The dock the lane last sat in — where showing it again puts it back.
        private @Nullable TabDockFxView<?> lastDock;
        /// Moves the lane into a window of its own and back; null for a lane
        /// that does not pop out.
        private @Nullable PaneDetacher detacher;
        /// Whether focusing the lane's tab moves the focus into its content.
        private boolean focusesContent;

        private Lane(String title, Node content) {
            this.title = title;
            this.content = content;
            this.item = new CheckMenuItem(title);
        }

        String title() {
            return title;
        }

        /// Focusing this lane's tab — clicking its header — moves the focus into
        /// its content.
        void focusContentOnTabFocus() {
            focusesContent = true;
        }

        void setDetacher(PaneDetacher detacher) {
            this.detacher = detacher;
        }

        /// Shown in a window of its own rather than in a tab. Asked of the
        /// detacher's window, so it cannot disagree with it.
        private boolean isPoppedOut() {
            PaneDetacher popOut = detacher;
            return popOut != null && popOut.isOpen();
        }
    }

    private final ShellFxView<?> shell;
    private final DockHostFxView<?> dockHost;
    private final List<Lane> all = new ArrayList<>();
    /// Set while the code itself checks or unchecks an entry, so that does
    /// not run as a user's show or hide.
    private boolean syncing;

    DockLanes(ShellFxView<?> shell, DockHostFxView<?> dockHost) {
        this.shell = shell;
        this.dockHost = dockHost;
    }

    Lane add(String title, Node content) {
        var lane = new Lane(title, content);
        lane.item.selectedProperty().addListener((obs, was, shown) -> onToggled(lane, shown));
        all.add(lane);
        return lane;
    }

    /// A flat icon-only menu button (the task toolbar's filter button shape)
    /// with one checkable entry per lane, in a toolbar above the dock host.
    ToolBar toolBar() {
        MenuButton button = new MenuButton(null, new SvgNode(MDIInterface.VIEW_DASHBOARD_OUTLINE.path(), 16));
        button.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
        button.setTooltip(new Tooltip("Show or hide panes"));
        for (Lane lane : all) {
            button.getItems().add(lane.item);
        }
        // Right-aligned: the left of the main toolbar is where app-wide actions go.
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return new ToolBar(spacer, button);
    }

    void open(Lane lane, TabDockFxView<?> dock) {
        var view = new NodeTabFxView(shell, lane.content);
        new NodeTabPresenter(view, lane.title, () -> onTabClosed(lane, view)).initialize();
        // Follows the lane across drags, so showing it again after a close
        // puts it where the user had moved it.
        view.getNode().tabPaneProperty().addListener((obs, old, tabPane) -> {
            TabDockFxView<?> holder = tabPane == null ? null : dockHolding(tabPane);
            if (holder != null) {
                lane.lastDock = holder;
            }
        });
        dock.getComposer().addTab(view);
        dock.getComposer().selectTab(view);
        // A header click may focus the tab pane before it selects the tab.
        view.getNode().selectedProperty().addListener((obs, was, selected) -> focusContent());
        lane.tab = view;
        lane.lastDock = dock;
        setChecked(lane, true);
    }

    /// Puts `lane` in front: its tab selected in its dock, a hidden lane shown
    /// again, a popped-out one's window raised.
    void bringToFront(Lane lane) {
        PaneDetacher popOut = lane.detacher;
        if (popOut != null && popOut.isOpen()) {
            popOut.toFront();
            return;
        }
        if (lane.tab == null) {
            reopen(lane);
        } else {
            select(lane);
        }
    }

    /// Selects `lane`'s tab in its dock; a hidden or popped-out lane is left
    /// as it is.
    void select(Lane lane) {
        NodeTabFxView tab = lane.tab;
        if (tab != null && tab.getComposer().getParent() instanceof TabDockFxView<?> dock) {
            dock.getComposer().selectTab(tab);
        }
    }

    /// Shows a hidden lane: in its last dock if that still exists (a dock
    /// that lost its last tab closes), else beside another shown lane, else —
    /// every lane hidden, so no dock left — in a new dock of its own.
    void reopen(Lane lane) {
        TabDockFxView<?> last = lane.lastDock;
        TabDockFxView<?> dock = last != null && ShellFxHost.isAttached(last) ? last : dockOfAnyShownLane();
        if (dock != null) {
            open(lane, dock);
            return;
        }
        TabDockFxView<?> created = ShellFxHost.tabDock(dockHost);
        // The tab goes in before the dock enters the layout, so the dock is
        // never empty there.
        open(lane, created);
        // Not addTabDock: on a layout with no dock left (every lane hidden,
        // e.g. by Close All) ShellFX 2.0.0-SNAPSHOT's Transformer.checkNewSide
        // looks for a neighbour at index -1 and throws. A fresh model with the
        // one dock replaces the empty layout instead.
        dockHost.getComposer().applyModel(ModelNodeBuilder.root(Orientation.HORIZONTAL, s -> s
                .area(created)));
    }

    /// Takes `lane`'s tab out of the layout — for a lane popping out, which
    /// stays shown.
    void closeTab(Lane lane) {
        NodeTabFxView tab = lane.tab;
        if (tab != null) {
            tab.getComposer().close();
        }
    }

    private void onTabClosed(Lane lane, NodeTabFxView view) {
        if (lane.tab == view) {
            lane.tab = null;
        }
        // Popping out closes the tab too, but the lane stays shown.
        if (!lane.isPoppedOut()) {
            setChecked(lane, false);
        }
    }

    private void onToggled(Lane lane, boolean shown) {
        if (syncing) {
            return;
        }
        if (shown) {
            if (lane.tab == null && !lane.isPoppedOut()) {
                reopen(lane);
            }
            return;
        }
        PaneDetacher popOut = lane.detacher;
        NodeTabFxView tab = lane.tab;
        if (popOut != null && popOut.isOpen()) {
            popOut.closeWithoutDocking();
        } else if (tab != null) {
            tab.getComposer().close();
        }
    }

    /// Moves the focus into the selected lane's content when the tab pane
    /// showing it holds the focus and the lane asks for that.
    void focusContent() {
        for (Lane lane : all) {
            NodeTabFxView view = lane.tab;
            if (lane.focusesContent && view != null && view.getNode().isSelected()) {
                TabPane tabPane = view.getNode().getTabPane();
                if (tabPane != null && tabPane.isFocused()) {
                    lane.content.requestFocus();
                }
            }
        }
    }

    private void setChecked(Lane lane, boolean checked) {
        syncing = true;
        try {
            lane.item.setSelected(checked);
        } finally {
            syncing = false;
        }
    }

    private @Nullable TabDockFxView<?> dockHolding(TabPane tabPane) {
        for (Object child : dockHost.getComposer().getChildren()) {
            if (child instanceof TabDockFxView<?> dock && dock.getNode() == tabPane) {
                return dock;
            }
        }
        return null;
    }

    private @Nullable TabDockFxView<?> dockOfAnyShownLane() {
        for (Lane lane : all) {
            NodeTabFxView tab = lane.tab;
            if (tab != null && tab.getComposer().getParent() instanceof TabDockFxView<?> dock
                    && ShellFxHost.isAttached(dock)) {
                return dock;
            }
        }
        return null;
    }

    /// A ShellFX tab whose whole content is an existing node.
    private static final class NodeTabFxView extends AbstractTabFxView<NodeTabPresenter> {

        private final Node content;

        NodeTabFxView(ShellFxView<?> shell, Node content) {
            super(shell);
            this.content = content;
        }

        @Override
        public void requestFocus() {
            content.requestFocus();
        }

        @Override
        protected void build() {
            super.build();
            getContentBox().getChildren().add(content);
            VBox.setVgrow(content, Priority.ALWAYS);
        }
    }

    private static final class NodeTabPresenter extends AbstractTabPresenter<TabView> {

        private final String title;
        private final Runnable onClosed;

        /// `onClosed` runs once the tab is torn down — the one hook every close
        /// path (the tab context menu's Close/All/Other/Left/Right; the × is
        /// hidden, see [ShellFxHost]'s `DOCK_STYLE`) goes through.
        NodeTabPresenter(TabView view, String title, Runnable onClosed) {
            super(view, new TabParams());
            this.title = title;
            this.onClosed = onClosed;
        }

        @Override
        public CloseCheckResult isReadyToClose() {
            return CloseCheckResult.READY;
        }

        @Override
        public void prepareToClose(Consumer<ClosePreparationResult> resultCallback) {
            resultCallback.accept(ClosePreparationResult.SUCCESS);
        }

        @Override
        protected void postInitialize() {
            super.postInitialize();
            setTitle(title);
            // Closable, so the tab context menu's close entries work: a closed
            // lane is only hidden, the toolbar's lanes menu brings it back. The
            // × closability draws is hidden by CSS instead (see DOCK_STYLE) —
            // Tab.setClosable(false) would take the menu's entries along.
            setClosable(true);
        }

        @Override
        protected void postDeinitialize() {
            super.postDeinitialize();
            onClosed.run();
        }
    }
}
