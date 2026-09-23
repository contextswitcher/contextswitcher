package com.contextswitcher.ui;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;

import com.contextswitcher.Main;
import com.contextswitcher.tasks.TaskEntry;

import io.gitlab.fxlabs.testfx.junit.jupiter.TestFxApplication;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.contextswitcher.ui.UiTestSupport.awaitPresent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static io.gitlab.fxlabs.testfx.util.FxRobotService.FX_ROBOT;

/// "Show active desktop only" and "Show running tasks only" checked together must
/// narrow to the running tasks *of the active desktop* — an AND, as two
/// checkmarks suggest (`dsn~active-desktop-filter~6`). Field report 2026-07-29:
/// with both on, only "running" was respected — the desktop read had silently
/// failed (missing `CurrentVirtualDesktop` after login), leaving the desktop
/// filter a no-op behind a lying checkmark. This test pins the AND itself with
/// a known desktop; the read's robustness is unit-tested in
/// `WindowsVirtualDesktopFocusTest`.
// [utest->dsn~active-desktop-filter~6]
@Tag("ui")
@TestFxApplication(UiTestSupport.TestApp.class)
class FilterAndCombineUiTest {

    @Test
    void desktopAndRunningFiltersCombineAsAnd() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.createDirectories(dir.resolve("alpha"));
        Files.createDirectories(dir.resolve("beta"));
        Files.writeString(dir.resolve("alpha/CONTEXTSWITCHER.md"), "---\ndesktop: Alpha\n---\n");
        Files.writeString(dir.resolve("beta/CONTEXTSWITCHER.md"), "---\ndesktop: Beta\n---\n");
        Files.writeString(dir.resolve("alpha/build.md"),
                "---\ntitle: build\nstatus: active\n---\nnotes\n");
        Files.writeString(dir.resolve("alpha/paused.md"),
                "---\ntitle: paused\nstatus: suspended\n---\nnotes\n");
        Files.writeString(dir.resolve("beta/deploy.md"),
                "---\ntitle: deploy\nstatus: active\n---\nnotes\n");

        ListView<Object> list = awaitListView();
        resetFilters();
        awaitTasks(list, List.of("alpha/build", "alpha/paused", "beta/deploy"));

        // The poller cannot know a desktop on the CI box — push one directly,
        // as a successful registry read would. (A failed read must not clobber
        // it: that keep-last-known behavior is part of the fix under test.)
        Platform.runLater(() -> mainWindow().updateActiveDesktop("Alpha"));

        // The reported order: desktop filter first — only Alpha's tasks stay.
        Platform.runLater(() -> filterItem("Show active desktop only").setSelected(true));
        awaitTasks(list, List.of("alpha/build", "alpha/paused"));

        // Then "running" on top: paused drops out, Beta's runner must NOT come
        // back — the two filters AND.
        Platform.runLater(() -> filterItem("Show running tasks only").setSelected(true));
        awaitTasks(list, List.of("alpha/build"));
    }

    /// A category with no task file at all is exempt from the *content* filters
    /// — it has nothing to hide — but not from the desktop filter, which narrows
    /// by a property of the category itself. Field report 2026-07-29: `cloudref`
    /// (`desktop: jabref`, no tasks yet) sat in the list on the ContextSwitcher
    /// desktop, because empty folders were added as groups after the per-task
    /// filtering and only ever dropped again when they *did* have tasks.
    // [utest->dsn~active-desktop-filter~6]
    @Test
    void aTaskLessCategoryOfAnotherDesktopIsHidden() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.createDirectories(dir.resolve("gamma"));
        Files.createDirectories(dir.resolve("delta"));
        Files.writeString(dir.resolve("gamma/CONTEXTSWITCHER.md"), "---\ndesktop: Gamma\n---\n");
        // No task file in delta — the shape that escaped the filter.
        Files.writeString(dir.resolve("delta/CONTEXTSWITCHER.md"), "---\ndesktop: Delta\n---\n");
        Files.writeString(dir.resolve("gamma/ship.md"),
                "---\ntitle: ship\nstatus: active\n---\nnotes\n");

        ListView<Object> list = awaitListView();
        resetFilters();
        awaitGroups(list, groups -> groups.contains("delta") && groups.contains("gamma"));

        Platform.runLater(() -> mainWindow().updateActiveDesktop("Gamma"));
        Platform.runLater(() -> filterItem("Show active desktop only").setSelected(true));

        awaitGroups(list, groups -> groups.equals(List.of("gamma")));
    }

    /// The desktop filter hides the categories that name no `desktop:` at all —
    /// tooling and scratch categories nobody assigned. "Also show categories
    /// without a desktop" brings exactly those back, and not the categories of
    /// *other* desktops.
    // [utest->dsn~active-desktop-filter~6]
    @Test
    void theNoDesktopOptionKeepsUnassignedCategoriesOnly() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.createDirectories(dir.resolve("epsilon"));
        Files.createDirectories(dir.resolve("zeta"));
        Files.createDirectories(dir.resolve("tools"));
        Files.writeString(dir.resolve("epsilon/CONTEXTSWITCHER.md"), "---\ndesktop: Epsilon\n---\n");
        Files.writeString(dir.resolve("zeta/CONTEXTSWITCHER.md"), "---\ndesktop: Zeta\n---\n");
        // The shape under test: a category with a config but no desktop: at all.
        Files.writeString(dir.resolve("tools/CONTEXTSWITCHER.md"), "---\nremote: nowhere\n---\n");
        for (String group : List.of("epsilon", "zeta", "tools")) {
            Files.writeString(dir.resolve(group + "/work.md"),
                    "---\ntitle: work\nstatus: active\n---\nnotes\n");
        }

        ListView<Object> list = awaitListView();
        resetFilters();
        awaitTasks(list, List.of("epsilon/work", "tools/work", "zeta/work"));

        Platform.runLater(() -> mainWindow().updateActiveDesktop("Epsilon"));
        Platform.runLater(() -> filterItem("Show active desktop only").setSelected(true));
        awaitTasks(list, List.of("epsilon/work"));

        // The unassigned category comes back — Zeta's, on another desktop, does not.
        Platform.runLater(
                () -> filterItem("Also show categories without a desktop").setSelected(true));
        awaitTasks(list, List.of("epsilon/work", "tools/work"));

        Platform.runLater(
                () -> filterItem("Also show categories without a desktop").setSelected(false));
        awaitTasks(list, List.of("epsilon/work"));

        // On the fallback desktop itself the unassigned category shows without
        // the option — that is the desktop it is focused on
        // (`dsn~fallback-desktop~1`).
        Platform.runLater(() -> mainWindow()
                .updateActiveDesktop(com.contextswitcher.config.AppSettings.DEFAULT_FALLBACK_DESKTOP));
        awaitTasks(list, List.of("tools/work"));
    }

    /// A config that does not load has no `desktop:` only by accident — the
    /// category stays listed under the desktop filter, its header says why, and
    /// fixing the file lets the filter act on the real desktop again. Field
    /// report 2026-09-13: the category silently disappeared.
    // [utest->dsn~group-config-parse-error~1]
    @Test
    void aCategoryWhoseConfigDoesNotLoadStaysListedWithItsWarning() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.createDirectories(dir.resolve("iota"));
        Files.createDirectories(dir.resolve("kappa"));
        Files.writeString(dir.resolve("iota/CONTEXTSWITCHER.md"), "---\ndesktop: Iota\n---\n");
        Files.writeString(dir.resolve("kappa/CONTEXTSWITCHER.md"),
                "---\nhttps://example.org/keyless\nremote: nowhere\n---\n");

        ListView<Object> list = awaitListView();
        resetFilters();
        awaitGroups(list, groups -> groups.contains("iota") && groups.contains("kappa"));

        Platform.runLater(() -> mainWindow().updateActiveDesktop("Iota"));
        Platform.runLater(() -> filterItem("Show active desktop only").setSelected(true));
        awaitGroups(list, groups -> groups.equals(List.of("iota", "kappa")));
        assertTrue(List.copyOf(list.getItems()).stream().anyMatch(row ->
                row instanceof TaskListCell.GroupHeader header && header.name().equals("kappa")
                        && header.warning() != null && header.warning().contains("(line 3)")),
                "kappa's header names the line that does not load");

        Files.writeString(dir.resolve("kappa/CONTEXTSWITCHER.md"), "---\ndesktop: Kappa\n---\n");
        awaitGroups(list, groups -> groups.equals(List.of("iota")));
    }

    /// Switching desktops brings back the task that was selected while that
    /// desktop was last in view (`dsn~desktop-last-task-selection~4`). Field
    /// report 2026-09-08: the switch kept showing the *previous* desktop's task
    /// — the filter rebuild ran first, and the selection it left behind was
    /// recorded under the new desktop before the remembered id was read.
    // [utest->dsn~desktop-last-task-selection~4]
    @Test
    void aDesktopSwitchSelectsThatDesktopsLastTask() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.createDirectories(dir.resolve("eta"));
        Files.createDirectories(dir.resolve("theta"));
        Files.writeString(dir.resolve("eta/CONTEXTSWITCHER.md"), "---\ndesktop: Eta\n---\n");
        Files.writeString(dir.resolve("theta/CONTEXTSWITCHER.md"), "---\ndesktop: Theta\n---\n");
        Files.writeString(dir.resolve("eta/one.md"),
                "---\ntitle: one\nstatus: active\n---\nnotes\n");
        Files.writeString(dir.resolve("eta/two.md"),
                "---\ntitle: two\nstatus: active\n---\nnotes\n");
        Files.writeString(dir.resolve("theta/ship.md"),
                "---\ntitle: ship\nstatus: active\n---\nnotes\n");

        ListView<Object> list = awaitListView();
        resetFilters();
        awaitTasks(list, List.of("eta/one", "eta/two", "theta/ship"));

        Platform.runLater(() -> mainWindow().updateActiveDesktop("Eta"));
        Platform.runLater(() -> filterItem("Show active desktop only").setSelected(true));
        awaitTasks(list, List.of("eta/one", "eta/two"));
        Platform.runLater(() -> mainWindow().selectTask("eta/two"));
        awaitSelected("eta/two");

        Platform.runLater(() -> mainWindow().updateActiveDesktop("Theta"));
        awaitTasks(list, List.of("theta/ship"));
        Platform.runLater(() -> mainWindow().selectTask("theta/ship"));
        awaitSelected("theta/ship");

        // Back on Eta: the task selected there last, not Theta's.
        Platform.runLater(() -> mainWindow().updateActiveDesktop("Eta"));
        awaitSelected("eta/two");

        // And forward again: Theta's own last task, not the one just re-selected.
        Platform.runLater(() -> mainWindow().updateActiveDesktop("Theta"));
        awaitSelected("theta/ship");

        // A desktop nothing is remembered for takes its own first task rather
        // than leaving Theta's showing next to Eta's rows.
        Platform.runLater(() -> mainWindow().updateActiveDesktop("Iota"));
        Files.createDirectories(dir.resolve("iota"));
        Files.writeString(dir.resolve("iota/CONTEXTSWITCHER.md"), "---\ndesktop: Iota\n---\n");
        Files.writeString(dir.resolve("iota/fresh.md"),
                "---\ntitle: fresh\nstatus: active\n---\nnotes\n");
        awaitTasks(list, List.of("iota/fresh"));
        Platform.runLater(() -> mainWindow().updateActiveDesktop("Theta"));
        Platform.runLater(() -> mainWindow().updateActiveDesktop("Iota"));
        awaitSelected("iota/fresh");
    }

    /// A find query no longer lifts the desktop filter: a task of *another*
    /// desktop stays hidden, and the find bar reports it as a hit on another
    /// desktop instead (`dsn~search-hits-off-desktop~1`). Field report
    /// 2026-09-10: the earlier suspension moved the list to the other
    /// desktop's category, an implicit context switch worse than the miss.
    // [utest->dsn~search-hits-off-desktop~1]
    // [utest->dsn~task-find~9]
    // [utest->dsn~active-desktop-filter~6]
    @Test
    void aSearchKeepsTheDesktopFilterAndCountsTheHitsElsewhere() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.createDirectories(dir.resolve("kappa"));
        Files.createDirectories(dir.resolve("lambda"));
        Files.writeString(dir.resolve("kappa/CONTEXTSWITCHER.md"), "---\ndesktop: Kappa\n---\n");
        Files.writeString(dir.resolve("lambda/CONTEXTSWITCHER.md"), "---\ndesktop: Lambda\n---\n");
        Files.writeString(dir.resolve("kappa/here.md"),
                "---\ntitle: here\nstatus: active\n---\nnotes\n");
        Files.writeString(dir.resolve("lambda/faraway.md"),
                "---\ntitle: faraway\nstatus: active\n---\nnotes\n");

        ListView<Object> list = awaitListView();
        resetFilters();
        awaitTasks(list, List.of("kappa/here", "lambda/faraway"));

        Platform.runLater(() -> mainWindow().updateActiveDesktop("Kappa"));
        Platform.runLater(() -> filterItem("Show active desktop only").setSelected(true));
        awaitTasks(list, List.of("kappa/here"));

        // The other desktop's task stays hidden — but is counted, and the
        // filter stays ticked.
        TextField search = searchField();
        Platform.runLater(() -> search.setText("faraway"));
        awaitTasks(list, List.of());
        awaitMatchLabel("0 matches \u00b7 1 on other desktops");
        assertTrue(filterItem("Show active desktop only").isSelected(),
                "the desktop filter must survive a search");

        // A hit on the desktop in view counts as a plain match.
        Platform.runLater(() -> search.setText("here"));
        awaitTasks(list, List.of("kappa/here"));
        awaitMatchLabel("1 match");
        Platform.runLater(search::clear);
        awaitTasks(list, List.of("kappa/here"));
    }

    /// A changed query empties the list at once, before the worker's hits
    /// land — showing the previous rows meanwhile read as a search that does
    /// nothing. Checked in the same FX pulse as the keystroke, which the
    /// worker's `runLater` result cannot precede. The selected task, when it is
    /// a hit, is highlighted again once the hits land.
    // [utest->dsn~task-find~9]
    @Test
    void aChangedQueryClearsTheListBeforeItsHitsArrive() throws Exception {
        Path dir = UiTestSupport.tasksDir;
        Files.writeString(dir.resolve("mu.md"), "---\ntitle: mu\nstatus: active\n---\nnotes\n");
        Files.writeString(dir.resolve("nu.md"), "---\ntitle: nu\nstatus: active\n---\nnotes\n");

        ListView<Object> list = awaitListView();
        resetFilters();
        TextField search = searchField();
        Platform.runLater(search::clear);
        awaitPresent(() -> list.getItems().stream().anyMatch(row ->
                row instanceof TaskEntry.Loaded loaded && loaded.id().equals("nu"))
                ? Optional.of(true) : Optional.empty(), "task nu listed");
        Platform.runLater(() -> list.getItems().stream()
                .filter(row -> row instanceof TaskEntry.Loaded loaded && loaded.id().equals("mu"))
                .findFirst().ifPresent(list.getSelectionModel()::select));
        awaitSelected("mu");

        CompletableFuture<Long> rowsRightAfterTyping = new CompletableFuture<>();
        Platform.runLater(() -> {
            search.setText("mu");
            rowsRightAfterTyping.complete(list.getItems().stream()
                    .filter(TaskEntry.class::isInstance).count());
        });
        assertEquals(0L, rowsRightAfterTyping.get(10, TimeUnit.SECONDS));
        awaitPresent(() -> list.getItems().stream().anyMatch(row ->
                row instanceof TaskEntry.Loaded loaded && loaded.id().equals("mu"))
                ? Optional.of(true) : Optional.empty(), "task mu found");
        // Emptying the rows dropped the highlight; the hit takes it back.
        awaitSelected("mu");
        Platform.runLater(search::clear);
    }

    /// Waits until the find bar's match label reads exactly `text`.
    private static void awaitMatchLabel(String text) {
        awaitPresent(() -> FX_ROBOT.selectNodes(Label.class).fromAll()
                .filter(label -> label.getStyleClass().contains("find-match-count"))
                .filter(label -> text.equals(label.getText()))
                .findFirst(), "match label \"" + text + "\"");
    }

    /// Waits until the given task id is the selected row.
    private static void awaitSelected(String id) {
        awaitPresent(() -> awaitListView().getSelectionModel().getSelectedItem()
                        instanceof TaskEntry.Loaded loaded && loaded.id().equals(id)
                ? Optional.of(true) : Optional.empty(), "selected task " + id);
    }

    /// Unticks every toolbar filter: they survive the app restart between test
    /// methods now (`dsn~filter-persistence~1`), so each test starts from an
    /// unfiltered list whatever the previous one left checked.
    private static void resetFilters() {
        Platform.runLater(() -> {
            filterItem("Show running tasks only").setSelected(false);
            filterItem("Also show categories without a desktop").setSelected(false);
            filterItem("Show active desktop only").setSelected(false);
            filterItem("Awaits input only").setSelected(false);
        });
    }

    /// The filters are stored, so the last test method must not leave the next
    /// test *class* with a narrowed list.
    @AfterAll
    static void forgetFilters() {
        UiTestSupport.clearStoredFilters();
    }

    /// Waits until the visible category headers satisfy `check`.
    private static void awaitGroups(ListView<Object> list,
            java.util.function.Predicate<List<String>> check) {
        awaitPresent(() -> {
            List<String> headers = list.getItems().stream()
                    .filter(TaskListCell.GroupHeader.class::isInstance)
                    .map(row -> ((TaskListCell.GroupHeader) row).name())
                    .sorted().toList();
            return check.test(headers) ? Optional.of(true) : Optional.empty();
        }, "group headers");
    }

    /// Waits until exactly the given task ids are the visible Loaded rows.
    private static void awaitTasks(ListView<Object> list, List<String> ids) {
        awaitPresent(() -> {
            List<String> visible = list.getItems().stream()
                    .filter(TaskEntry.Loaded.class::isInstance)
                    .map(row -> ((TaskEntry.Loaded) row).id())
                    .sorted().toList();
            return visible.equals(ids) ? Optional.of(true) : Optional.empty();
        }, "visible tasks " + ids);
    }

    /// The find bar's inner text field (`dsn~task-find~9`), the only
    /// `TextField` carrying the `search-inner` style class.
    private static TextField searchField() {
        return awaitPresent(
                () -> FX_ROBOT.selectNodes(TextField.class).fromAll()
                        .filter(field -> field.getStyleClass().contains("search-inner"))
                        .findFirst(),
                "the find field");
    }

    /// A check item of the toolbar filter menu, by its text prefix — the
    /// active-desktop item's text carries a changing suffix once a desktop is
    /// known.
    private static CheckMenuItem filterItem(String textPrefix) {
        return FX_ROBOT.selectNodes(MenuButton.class).fromAll()
                .flatMap(button -> button.getItems().stream())
                .filter(CheckMenuItem.class::isInstance)
                .map(CheckMenuItem.class::cast)
                .filter(item -> item.getText().startsWith(textPrefix))
                .findFirst().orElseThrow();
    }

    /// The app's MainWindow, reached through the private `Main.window` field —
    /// `updateActiveDesktop` is its public API, only the handle is buried.
    private static MainWindow mainWindow() {
        try {
            Field field = Main.class.getDeclaredField("window");
            field.setAccessible(true);
            return (MainWindow) field.get(UiTestSupport.app);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ListView<Object> awaitListView() {
        return awaitPresent(
                () -> FX_ROBOT.selectNodes(ListView.class).fromAll()
                        .<ListView<Object>>map(v -> (ListView<Object>) v).findFirst(),
                "the task ListView");
    }
}
