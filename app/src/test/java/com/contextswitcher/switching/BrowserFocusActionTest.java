package com.contextswitcher.switching;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.contextswitcher.extension.ExtensionProtocol;
import com.contextswitcher.extension.TabCommands;
import com.contextswitcher.tasks.Task;
import com.contextswitcher.tasks.TaskStatus;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~browser-focus-action~4]
class BrowserFocusActionTest {

    /// Records requested URLs and answers with a fixed result; `existing`
    /// URLs answer an `openIfMissing: false` probe as found.
    private static final class FakeTabs implements TabCommands {
        final List<String> focused = new ArrayList<>();
        final List<String> probed = new ArrayList<>();
        final List<@Nullable String> groups = new ArrayList<>();
        final List<Boolean> backgrounded = new ArrayList<>();
        Set<String> existing = Set.of();
        boolean ok = true;
        String detail = "focused existing tab";

        @Override
        public ExtensionProtocol.Result focusUrl(String url, boolean openIfMissing, @Nullable String windowTitle,
                @Nullable String group, boolean background) {
            if (!openIfMissing) {
                probed.add(url);
                return existing.contains(url)
                        ? new ExtensionProtocol.Result("id", true, "focused existing tab")
                        : new ExtensionProtocol.Result("id", false, ExtensionProtocol.NO_TAB);
            }
            focused.add(url);
            groups.add(group);
            backgrounded.add(background);
            return new ExtensionProtocol.Result("id", ok, detail);
        }

        @Override
        public ExtensionProtocol.Result closeUrl(String url) {
            throw new AssertionError("focus action must not close tabs");
        }

        @Override
        public ExtensionProtocol.Result listTabs() {
            throw new AssertionError("focus action must not list tabs");
        }

        @Override
        public ExtensionProtocol.Result closeWindow(int windowId) {
            throw new AssertionError("focus action must not close windows");
        }
    }

    private static Task task(Task.@Nullable BrowserConfig browser) {
        return new Task("t", "T", TaskStatus.ACTIVE, null, null, null, null, browser, null, null, "");
    }

    private static Task taskWithStoredTabs(List<String> storedTabs) {
        return new Task("t", "T", TaskStatus.ACTIVE, null, null, null, null, null, null, null,
                List.of(), List.of(), storedTabs, "");
    }

    @Test
    void notConfiguredWithoutBrowserSection() {
        assertThat(new BrowserFocusAction(new FakeTabs()).isConfigured(task(null))).isFalse();
    }

    @Test
    void notConfiguredWithEmptyUrlList() {
        assertThat(new BrowserFocusAction(new FakeTabs())
                .isConfigured(task(new Task.BrowserConfig(List.of())))).isFalse();
    }

    @Test
    void focusesTheSingleUrl() {
        FakeTabs tabs = new FakeTabs();
        ActionResult result = new BrowserFocusAction(tabs)
                .run(task(Task.BrowserConfig.ofUrls("https://a.example/")));
        assertThat(result).isEqualTo(ActionResult.success("focused existing tab"));
        assertThat(tabs.focused).containsExactly("https://a.example/");
    }

    @Test
    void opensAllUrlsInTaskOrderWithOnlyTheFirstInTheForeground() {
        FakeTabs tabs = new FakeTabs();
        ActionResult result = new BrowserFocusAction(tabs)
                .run(task(Task.BrowserConfig.ofUrls(
                        "https://a.example/", "https://b.example/", "https://c.example/")));
        assertThat(result).isEqualTo(ActionResult.success("focused existing tab (+2 more)"));
        assertThat(tabs.focused)
                .containsExactly("https://a.example/", "https://b.example/", "https://c.example/");
        assertThat(tabs.backgrounded).containsExactly(false, true, true);
    }

    @Test
    void extensionFailureBecomesFailedResult() {
        FakeTabs tabs = new FakeTabs();
        tabs.ok = false;
        tabs.detail = "Browser extension not connected";
        ActionResult result = new BrowserFocusAction(tabs)
                .run(task(Task.BrowserConfig.ofUrls("https://a.example/")));
        assertThat(result).isEqualTo(ActionResult.failure("Browser extension not connected"));
    }

    @Test
    void storedTabsMakeTheActionConfigured() {
        assertThat(new BrowserFocusAction(new FakeTabs())
                .isConfigured(taskWithStoredTabs(List.of("https://a.example/")))).isTrue();
    }

    @Test
    void reopensStoredTabsAndClearsThem() {
        FakeTabs tabs = new FakeTabs();
        List<Task> cleared = new ArrayList<>();
        BrowserFocusAction action = new BrowserFocusAction(tabs,
                t -> null, (desktop, url) -> null,
                t -> {
                    cleared.add(t);
                    return null;
                });
        ActionResult result = action
                .run(taskWithStoredTabs(List.of("https://a.example/", "https://b.example/")));
        assertThat(result.ok()).isTrue();
        assertThat(result.detail()).contains("reopened 2 stored tab(s)");
        assertThat(tabs.focused).containsExactly("https://a.example/", "https://b.example/");
        assertThat(cleared).hasSize(1);
    }

    @Test
    void failedReopenKeepsStoredTabsForTheNextResume() {
        FakeTabs tabs = new FakeTabs();
        tabs.ok = false;
        tabs.detail = "Browser extension not connected";
        BrowserFocusAction action = new BrowserFocusAction(tabs,
                t -> null, (desktop, url) -> tabs.focusUrl(url),
                t -> {
                    throw new AssertionError("a failed reopen must not clear the stored tabs");
                });
        ActionResult result = action.run(taskWithStoredTabs(List.of("https://a.example/")));
        assertThat(result.ok()).isFalse();
        assertThat(result.detail()).contains("stored tabs kept");
    }

    @Test
    void completeControlPreparesTheDesktopWhenTheFirstTabIsGone() {
        FakeTabs tabs = new FakeTabs();
        List<String> prepared = new ArrayList<>();
        BrowserFocusAction action = new BrowserFocusAction(tabs,
                t -> "jabref",
                (desktop, url) -> {
                    prepared.add(desktop + " " + url);
                    return new ExtensionProtocol.Result("id", true, "opened a window on desktop \"jabref\"");
                },
                t -> null);
        ActionResult result = action
                .run(taskWithStoredTabs(List.of("https://a.example/", "https://b.example/")));
        assertThat(result.ok()).isTrue();
        assertThat(prepared).containsExactly("jabref https://a.example/");
        // The preparer opened the first URL itself; only the rest go through
        // the extension.
        assertThat(tabs.focused).containsExactly("https://b.example/");
    }

    @Test
    void alreadyOpenFirstTabSkipsTheDesktopDance() {
        FakeTabs tabs = new FakeTabs();
        tabs.existing = Set.of("https://a.example/");
        BrowserFocusAction action = new BrowserFocusAction(tabs,
                t -> "jabref",
                (desktop, url) -> {
                    throw new AssertionError("no desktop switch when the tab is already open");
                },
                t -> null);
        ActionResult result = action.run(taskWithStoredTabs(List.of("https://a.example/")));
        assertThat(result.ok()).isTrue();
        assertThat(tabs.probed).containsExactly("https://a.example/");
        assertThat(tabs.focused).containsExactly("https://a.example/");
    }

    // [utest->dsn~browser-tab-group~3]
    @Test
    void everyUrlIsCollectedInTheTaskTabGroup() {
        FakeTabs tabs = new FakeTabs();
        new BrowserFocusAction(tabs).run(task(Task.BrowserConfig.ofUrls(
                "https://example.org/a", "https://example.org/b")));

        assertThat(tabs.groups).containsExactly("l:t", "l:t");
    }
}
