package com.contextswitcher.ui;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import com.contextswitcher.Main;
import com.contextswitcher.tasks.TaskEntry;

import javafx.application.Platform;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.control.skin.VirtualFlow;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;
import static org.assertj.core.api.Assertions.assertThat;

/// `MainWindow.selectTask` has to reveal a row the current view hides
/// (`dsn~browser-tab-selects-task~5`). Field report 2026-09-05: activating a
/// browser tab of a task in a collapsed category selected nothing at all — the
/// selection scanned the visible rows only, so the id lingered as a pending
/// selection and the user saw the app do nothing. Only a running list can show
/// the reveal (collapse/find both go through a row rebuild), hence a TestFX
/// test.
// [utest->dsn~browser-tab-selects-task~5]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class SelectTaskRevealsRowUiTest {

    @Test
    void selectingATaskExpandsItsCollapsedCategory() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.createDirectories(dir.resolve("alpha"));
        Files.writeString(dir.resolve("alpha/build.md"), taskFile("build"));

        ListView<Object> list = awaitListView();
        resetFilters();
        awaitFx(() -> indexOfId(list, "alpha/build") >= 0, "the alpha/build row to load");

        collapse("alpha");
        awaitFx(() -> indexOfId(list, "alpha/build") < 0, "the collapsed category to hide the row");

        runFx(() -> mainWindow().selectTask("alpha/build"));
        awaitFx(() -> selectedId(list).equals("alpha/build"),
                "the row to be revealed and selected");
    }

    @Test
    void selectingATaskClearsAFindQueryThatHidesIt() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.createDirectories(dir.resolve("beta"));
        Files.writeString(dir.resolve("beta/gamma.md"), taskFile("gamma"));
        Files.writeString(dir.resolve("beta/delta.md"), taskFile("delta"));

        ListView<Object> list = awaitListView();
        resetFilters();
        awaitFx(() -> indexOfId(list, "beta/gamma") >= 0 && indexOfId(list, "beta/delta") >= 0,
                "both rows to load");

        TextField search = searchField();
        runFx(() -> search.setText("delta"));
        awaitFx(() -> indexOfId(list, "beta/gamma") < 0, "the query to hide gamma");

        runFx(() -> mainWindow().selectTask("beta/gamma"));
        awaitFx(() -> selectedId(list).equals("beta/gamma"), "gamma to be revealed and selected");
        assertThat(callFx(() -> search.getText().isEmpty()))
                .as("the find query was cleared")
                .isTrue();
    }

    /// Field report 2026-09-08: moving between the tabs of one task's group in
    /// the browser reports every tab, all matching the task already selected —
    /// and each report re-revealed and re-scrolled its row, a visible flicker.
    /// The task is left alone once it is the selected one.
    @Test
    void activatingAnotherTabOfTheSelectedTaskLeavesTheViewAlone() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.createDirectories(dir.resolve("eps"));
        Files.writeString(dir.resolve("eps/review.md"), """
                ---
                title: review
                status: active
                browser:
                  urls:
                    - https://example.org/one
                    - https://example.org/two
                ---
                notes
                """);

        ListView<Object> list = awaitListView();
        resetFilters();
        awaitFx(() -> indexOfId(list, "eps/review") >= 0, "the eps/review row to load");

        runFx(() -> mainWindow().selectTask("eps/review"));
        awaitFx(() -> selectedId(list).equals("eps/review"), "the row to be selected");

        collapse("eps");
        awaitFx(() -> indexOfId(list, "eps/review") < 0, "the collapsed category to hide the row");

        // The second tab of the same group: it matches the very task in view.
        selectTaskForTab("https://example.org/two");

        // The category stays collapsed — nothing was re-selected, so nothing
        // was revealed, scrolled or repainted.
        UiTestSupport.sleep();
        assertThat(callFx(() -> indexOfId(list, "eps/review") < 0))
                .as("the collapsed category was left collapsed")
                .isTrue();
    }

    /// Field report 2026-09-10: leaving the app and coming back left the task
    /// list with *nothing* selected. A `selectTask` for a task the narrowing
    /// filters hide (the browser-tab reporter leaves it pending on purpose)
    /// suppressed the selection preservation of every following rebuild — and
    /// a rebuild comes with each task file write.
    @Test
    void aPendingSelectionNoRowShowsKeepsTheTaskOnScreenSelected() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.createDirectories(dir.resolve("zeta"));
        Files.writeString(dir.resolve("zeta/live.md"), taskFile("live"));
        Files.writeString(dir.resolve("zeta/parked.md"),
                "---\ntitle: parked\nstatus: suspended\n---\nnotes\n");

        ListView<Object> list = awaitListView();
        resetFilters();
        awaitFx(() -> indexOfId(list, "zeta/live") >= 0 && indexOfId(list, "zeta/parked") >= 0,
                "both rows to load");

        runFx(() -> mainWindow().selectTask("zeta/live"));
        awaitFx(() -> selectedId(list).equals("zeta/live"), "the live row to be selected");

        // "Show running tasks only" hides the suspended one — the deliberate
        // working set a browser event must not widen.
        runFx(() -> filterItem("Show running tasks only").setSelected(true));
        awaitFx(() -> indexOfId(list, "zeta/parked") < 0, "the filter to hide the parked row");

        runFx(() -> mainWindow().selectTask("zeta/parked"));

        // A task arriving in a category that sorts first pushes the selected
        // row down — the rebuild the pending id kept from preserving the
        // selection, which then stayed on the vacated index.
        Files.createDirectories(dir.resolve("aaa"));
        Files.writeString(dir.resolve("aaa/extra.md"), taskFile("extra"));
        awaitFx(() -> indexOfId(list, "aaa/extra") >= 0, "the new row to shift the live row down");

        assertThat(callFx(() -> selectedId(list).equals("zeta/live")))
                .as("the task on screen kept the selection")
                .isTrue();

        runFx(() -> filterItem("Show running tasks only").setSelected(false));
    }

    /// Field report 2026-09-11: a suspended task activated from its browser
    /// tab was selected and resumed — and its row was then nowhere in view.
    /// `status:` is the list's primary sort key, so the resume moves the row
    /// out of the suspended block and the rebuild's preserved selection
    /// follows it off screen without scrolling.
    // [utest->dsn~status-flip-keeps-row-in-view~1]
    @Test
    void resumingTheSelectedTaskRevealsItsMovedRow() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.createDirectories(dir.resolve("eta"));
        // Enough rows that the two status blocks cannot share a viewport.
        for (int i = 0; i < 60; i++) {
            Files.writeString(dir.resolve("eta/busy-%02d.md".formatted(i)),
                    taskFile("busy-%02d".formatted(i)));
        }
        // Sorts to the head of the active block in every sort mode (title
        // first, and it is the most recently written file), so the resume
        // moves it from the very end of the list to the very beginning.
        Files.writeString(dir.resolve("eta/aaa-parked.md"),
                "---\ntitle: aaa-parked\nstatus: suspended\n---\nnotes\n");

        ListView<Object> list = awaitListView();
        resetFilters();
        awaitFx(() -> indexOfId(list, "eta/aaa-parked") >= 0 && indexOfId(list, "eta/busy-59") >= 0,
                "all rows to load");

        runFx(() -> mainWindow().selectTask("eta/aaa-parked"));
        awaitFx(() -> selectedId(list).equals("eta/aaa-parked"), "the parked row to be selected");
        awaitFx(() -> rowVisible(list, "eta/aaa-parked"), "the parked row to be scrolled into view");

        runFx(() -> mainWindow().resumeTask(taskOf(list, "eta/aaa-parked")));

        awaitFx(() -> indexOfId(list, "eta/aaa-parked") < indexOfId(list, "eta/busy-00"),
                "the resumed row to move to the head of the active block");
        awaitFx(() -> rowVisible(list, "eta/aaa-parked"),
                "the resumed row to be revealed at its new place");
        assertThat(callFx(() -> selectedId(list).equals("eta/aaa-parked")))
                .as("the resumed task kept the selection")
                .isTrue();
    }

    /// Field report 2026-09-12, twice: "the highlight the task on the left
    /// while creation still does not work — the terminal content is right
    /// though". Creating a task writes its file several times in a row (tmux
    /// window, claude section, published title), and every write rebuilds the
    /// rows underneath the selection. The panes kept following the task, so
    /// the selection *model* was right — which is all the tests above check.
    /// This one checks what the user actually looks at: the selected row is
    /// the one the list paints.
    @Test
    void arebuildUnderTheSelectionKeepsTheRowPainted() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.createDirectories(dir.resolve("theta"));
        Files.writeString(dir.resolve("theta/live.md"), taskFile("live"));

        ListView<Object> list = awaitListView();
        resetFilters();
        awaitFx(() -> indexOfId(list, "theta/live") >= 0, "the live row to load");

        runFx(() -> mainWindow().selectTask("theta/live"));
        awaitFx(() -> selectedId(list).equals("theta/live"), "the live row to be selected");
        assertThat(callFx(() -> paintedSelected(list, "theta/live")))
                .as("the freshly selected row is painted selected")
                .isTrue();

        // A rebuild that moves the selected row: a category sorting before
        // "theta" pushes it down, exactly as a created task's own writes and
        // its adopted title do.
        Files.createDirectories(dir.resolve("alpha2"));
        Files.writeString(dir.resolve("alpha2/other.md"), taskFile("other"));
        awaitFx(() -> indexOfId(list, "alpha2/other") >= 0, "the new row to shift the live row");

        assertThat(callFx(() -> selectedId(list).equals("theta/live")))
                .as("the selection model still holds the task")
                .isTrue();
        assertThat(callFx(() -> paintedSelected(list, "theta/live")))
                .as("the moved row is still the row the list paints selected")
                .isTrue();
    }

    /// The creation shape itself: the file appears, the row is selected while
    /// it may not exist yet, the progress bar goes on and off, and the file is
    /// rewritten twice under it (the tmux window, the claude section).
    @Test
    void aTaskSelectedWhileItIsBeingCreatedStaysPainted() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.createDirectories(dir.resolve("iota"));
        Path file = dir.resolve("iota/fresh.md");
        Files.writeString(file, taskFile("fresh"));
        // Selected before the watcher can have delivered the row, the way
        // `Main.createLiveTask` selects the task it just wrote.
        runFx(() -> mainWindow().selectTask("iota/fresh"));

        ListView<Object> list = awaitListView();
        resetFilters();
        // The reported app had a narrowing filter on (the toolbar badge said
        // "1"), which is what gates the rebuild's selection preservation.
        runFx(() -> filterItem("Show running tasks only").setSelected(true));
        awaitFx(() -> selectedId(list).equals("iota/fresh"), "the created row to be selected");

        runFx(() -> mainWindow().creationProgress("iota/fresh", "Creating the tmux window …"));
        Files.writeString(file, taskFile("fresh") + TMUX);
        UiTestSupport.sleep();
        runFx(() -> mainWindow().creationProgress("iota/fresh", "Starting Claude …"));
        Files.writeString(file, taskFile("fresh") + TMUX + CLAUDE);
        awaitFx(() -> selectedId(list).equals("iota/fresh"), "the selection to survive the writes");
        runFx(() -> mainWindow().creationDone("iota/fresh"));

        assertThat(callFx(() -> paintedSelected(list, "iota/fresh")))
                .as("the created row is the row the list paints selected")
                .isTrue();
        runFx(() -> filterItem("Show running tasks only").setSelected(false));
    }

    /// Field report 2026-09-12: a filter that hides the selected task for a
    /// moment used to drop its selection for good — the row came back, the
    /// highlight did not, while the terminal kept showing the task (a cleared
    /// selection is deliberately a no-op for the panes). Only a *search* may
    /// drop it.
    // [utest->dsn~selection-survives-a-filter~2]
    @Test
    void aFilterThatHidesTheSelectedTaskGivesItsHighlightBack() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.createDirectories(dir.resolve("kappa"));
        Files.writeString(dir.resolve("kappa/parked.md"), PARKED);
        Files.writeString(dir.resolve("kappa/busy.md"), taskFile("busy"));

        ListView<Object> list = awaitListView();
        resetFilters();
        awaitFx(() -> indexOfId(list, "kappa/parked") >= 0, "the parked row to load");

        runFx(() -> mainWindow().selectTask("kappa/parked"));
        awaitFx(() -> paintedSelected(list, "kappa/parked"), "the parked row to be painted");

        // "Show running tasks only" hides it — as the desktop and running filters
        // hide a task whose window is still being created.
        runFx(() -> filterItem("Show running tasks only").setSelected(true));
        awaitFx(() -> indexOfId(list, "kappa/parked") < 0, "the filter to hide the parked row");

        // A rebuild while it is hidden: the write that used to cost it its
        // selection.
        Files.writeString(dir.resolve("kappa/busy.md"), taskFile("busy") + "touched");
        UiTestSupport.sleep();

        runFx(() -> filterItem("Show running tasks only").setSelected(false));
        awaitFx(() -> paintedSelected(list, "kappa/parked"),
                "the row to take its highlight back when it reappears");
    }

    /// Field report 2026-09-12, with "Show active desktop only" on:
    /// "collapsing click doesn' work". Force-expanding every group belongs to
    /// a find — a query or a tag selection — not to the standing filters the
    /// user reads their list with all day; while every filter drove it, the
    /// collapse flipped the state and the rebuild ignored it.
    // [utest->dsn~collapse-survives-a-filter~1]
    @Test
    void aStandingFilterStillLetsACategoryCollapse() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.createDirectories(dir.resolve("lambda"));
        Files.writeString(dir.resolve("lambda/busy.md"), taskFile("busy"));

        ListView<Object> list = awaitListView();
        resetFilters();
        awaitFx(() -> indexOfId(list, "lambda/busy") >= 0, "the row to load");

        runFx(() -> filterItem("Show running tasks only").setSelected(true));
        UiTestSupport.sleep();

        collapse("lambda");
        awaitFx(() -> indexOfId(list, "lambda/busy") < 0,
                "the category to collapse while a standing filter is on");

        // A query still forces every group open, so a match is never hidden
        // behind a collapsed header.
        TextField search = searchField();
        runFx(() -> search.setText("busy"));
        awaitFx(() -> indexOfId(list, "lambda/busy") >= 0,
                "the search to reveal the row inside the collapsed category");

        runFx(() -> search.setText(""));
        runFx(() -> filterItem("Show running tasks only").setSelected(false));
        collapse("lambda");
    }

    /// Field report 2026-09-12: "task on the left not focussed. IDK why is it
    /// so hard to always have a task focussed". Both ids the rebuild can fall
    /// back on were unresolvable at once — the on-screen one had just been
    /// renamed by a title adoption, the pending one was a browser-tab report
    /// for a task the active-desktop filter hides — so the list ended up with
    /// nothing selected. The previewed task is the last resort.
    ///
    /// This pins the *scenario* — an unreachable pending id plus a renamed row
    /// must leave a task selected — not the new fallback itself: it passes
    /// with the fallback removed, because `taskRenamed` re-selects directly
    /// and the rebuild never has to fall back. The exact field interleaving
    /// (both ids unresolvable at the same rebuild) needs a browser-tab report
    /// landing between the rename and the next write, which is not reachable
    /// from here. The `previewed` field in the "Rebuild left nothing selected"
    /// log line is what shows whether the fallback fires in the field.
    @Test
    void anUnreachablePendingIdStillLeavesTheShownTaskSelected() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.createDirectories(dir.resolve("nu"));
        Files.writeString(dir.resolve("nu/shown.md"), taskFile("shown"));
        Files.writeString(dir.resolve("nu/parked.md"), PARKED);

        ListView<Object> list = awaitListView();
        resetFilters();
        awaitFx(() -> indexOfId(list, "nu/shown") >= 0 && indexOfId(list, "nu/parked") >= 0,
                "both rows to load");

        runFx(() -> mainWindow().selectTask("nu/shown"));
        awaitFx(() -> paintedSelected(list, "nu/shown"), "the shown row to be painted");

        // A filter that hides the other task, then a pending selection for it:
        // an id that can never resolve while the filter is on.
        runFx(() -> filterItem("Show running tasks only").setSelected(true));
        awaitFx(() -> indexOfId(list, "nu/parked") < 0, "the filter to hide the parked row");
        runFx(() -> mainWindow().selectTask("nu/parked"));

        // …and the selected task's own row renamed out from under it, the way
        // an adopted title renames the file. Now *both* ids the rebuild falls
        // back on are unresolvable: the old one has no row, the pending one is
        // filtered out.
        Files.move(dir.resolve("nu/shown.md"), dir.resolve("nu/renamed.md"));
        runFx(() -> mainWindow().taskRenamed("nu/shown", "nu/renamed"));
        awaitFx(() -> indexOfId(list, "nu/renamed") >= 0, "the renamed row to load");

        // Any rebuild now must not leave the list with nothing selected.
        Files.writeString(dir.resolve("nu/renamed.md"), taskFile("shown") + "touched");
        UiTestSupport.sleep();

        assertThat(callFx(() -> paintedSelected(list, "nu/renamed")))
                .as("the task on screen keeps the highlight")
                .isTrue();

        runFx(() -> filterItem("Show running tasks only").setSelected(false));
    }

    /// Whether the list really paints `id`'s row as the selected one: the
    /// selection *index* points at it, and the cell rendering it reports
    /// itself selected. `getSelectedItem` alone does not say this — an index
    /// left behind by a row rebuild keeps the item while the highlight sits
    /// on another row, or on none.
    private static boolean paintedSelected(ListView<Object> list, String id) {
        int row = indexOfId(list, id);
        if (row < 0 || list.getSelectionModel().getSelectedIndex() != row) {
            return false;
        }
        VirtualFlow<?> flow = (VirtualFlow<?>) list.lookup(".virtual-flow");
        if (flow == null) {
            return false;
        }
        for (int i = 0; i < flow.getCellCount(); i++) {
            IndexedCell<?> cell = flow.getCell(i);
            if (cell.getIndex() == row) {
                return cell.isSelected();
            }
        }
        return false;
    }

    /// Whether the row of `id` is inside the list's visible cell range — the
    /// question "did the viewport follow the row", which the selection alone
    /// does not answer.
    private static boolean rowVisible(ListView<Object> list, String id) {
        int row = indexOfId(list, id);
        VirtualFlow<?> flow = (VirtualFlow<?>) list.lookup(".virtual-flow");
        if (row < 0 || flow == null) {
            return false;
        }
        IndexedCell<?> first = flow.getFirstVisibleCell();
        IndexedCell<?> last = flow.getLastVisibleCell();
        return first != null && last != null && row >= first.getIndex() && row <= last.getIndex();
    }

    private static com.contextswitcher.tasks.Task taskOf(ListView<Object> list, String id) {
        return ((TaskEntry.Loaded) list.getItems().get(indexOfId(list, id))).task();
    }

    /// Drives the extension's report through the real path — `selectTaskForTab`
    /// is private and takes the repository `Main` keeps in a private field.
    private static void selectTaskForTab(String url) throws Exception {
        Object repository = field(UiTestSupport.app, "repository");
        Method select = Main.class.getDeclaredMethod(
                "selectTaskForTab", repository.getClass(), String.class, boolean.class);
        select.setAccessible(true);
        runFx(() -> {
            try {
                // resume = true: what a tab activation passes.
                select.invoke(UiTestSupport.app, repository, url, true);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    /// The narrowing filters are stored across runs, so a leftover one would
    /// hide the rows this test is about — the same reset `FilterAndCombineUiTest`
    /// does.
    private static void resetFilters() {
        runFx(() -> {
            filterItem("Show running tasks only").setSelected(false);
            filterItem("Also show categories without a desktop").setSelected(false);
            filterItem("Show active desktop only").setSelected(false);
            filterItem("Awaits input only").setSelected(false);
        });
    }

    @AfterAll
    static void forgetFilters() {
        UiTestSupport.clearStoredFilters();
    }

    private static CheckMenuItem filterItem(String textPrefix) {
        return FX_ROBOT.selectNodes(MenuButton.class).fromAll()
                .flatMap(button -> button.getItems().stream())
                .filter(CheckMenuItem.class::isInstance)
                .map(CheckMenuItem.class::cast)
                .filter(item -> item.getText().startsWith(textPrefix))
                .findFirst().orElseThrow();
    }

    /// Collapses a category the way the header's click does — `toggleGroup` is
    /// private, and clicking the header would need the row on screen.
    private static void collapse(String group) throws Exception {
        Method toggle = MainWindow.class.getDeclaredMethod("toggleGroup", String.class);
        toggle.setAccessible(true);
        runFx(() -> {
            try {
                toggle.invoke(mainWindow(), group);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static TextField searchField() {
        return (TextField) field(mainWindow(), "searchField");
    }

    /// The app's MainWindow, reached through the private `Main.window` field.
    private static MainWindow mainWindow() {
        return (MainWindow) field(UiTestSupport.app, "window");
    }

    private static Object field(Object owner, String name) {
        try {
            Field field = (owner instanceof Main ? Main.class : MainWindow.class)
                    .getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final String PARKED = """
            ---
            title: parked
            status: suspended
            ---
            notes
            """;

    private static final String TMUX = """
            tmux:
              session: "s"
              window: "@1"
            """;

    private static final String CLAUDE = """
            claude:
              cwd: /w
            """;

    private static String taskFile(String title) {
        return "---\ntitle: " + title + "\nstatus: active\n---\nnotes\n";
    }

    private static ListView<Object> awaitListView() {
        return UiTestSupport.awaitPresent(
                () -> FX_ROBOT.selectNodes(ListView.class).fromAll()
                        .<ListView<Object>>map(v -> (ListView<Object>) v).findFirst(),
                "the task ListView");
    }

    private static int indexOfId(ListView<Object> list, String id) {
        for (int i = 0; i < list.getItems().size(); i++) {
            if (list.getItems().get(i) instanceof TaskEntry.Loaded loaded && loaded.id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static String selectedId(ListView<Object> list) {
        return list.getSelectionModel().getSelectedItem() instanceof TaskEntry.Loaded loaded
                ? loaded.id() : "";
    }

    private static void awaitFx(BooleanSupplier condition, String what) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (callFx(condition)) {
                return;
            }
            UiTestSupport.sleep();
        }
        throw new AssertionError("Timed out waiting for " + what + "; rows: "
                + callFxValue(() -> awaitListView().getItems().toString()));
    }

    private static String callFxValue(java.util.function.Supplier<String> body) {
        String[] result = {""};
        runFx(() -> result[0] = body.get());
        return result[0];
    }

    private static void runFx(Runnable action) {
        callFx(() -> {
            action.run();
            return false;
        });
    }

    private static boolean callFx(BooleanSupplier body) {
        if (Platform.isFxApplicationThread()) {
            return body.getAsBoolean();
        }
        boolean[] result = {false};
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result[0] = body.getAsBoolean();
            } finally {
                done.countDown();
            }
        });
        try {
            if (!done.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("FX action did not complete");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
        return result[0];
    }
}
