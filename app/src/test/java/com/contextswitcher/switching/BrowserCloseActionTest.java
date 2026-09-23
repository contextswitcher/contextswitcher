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

// [utest->dsn~browser-close-action~2]
class BrowserCloseActionTest {

    /// Closes tabs/windows, failing for URLs listed in `failing`; answers
    /// `list-tabs` with `windows`.
    private static final class FakeTabs implements TabCommands {
        final List<String> closed = new ArrayList<>();
        final List<Integer> closedWindows = new ArrayList<>();
        Set<String> failing = Set.of();
        @Nullable List<ExtensionProtocol.TabWindow> windows;
        boolean listOk = true;

        @Override
        public ExtensionProtocol.Result focusUrl(String url, boolean openIfMissing, @Nullable String windowTitle,
                @Nullable String group, boolean background) {
            throw new AssertionError("close action must not focus tabs");
        }

        @Override
        public ExtensionProtocol.Result closeUrl(String url) {
            closed.add(url);
            return failing.contains(url)
                    ? new ExtensionProtocol.Result("id", false, "cannot close " + url)
                    : new ExtensionProtocol.Result("id", true, "closed 1 tab(s)");
        }

        @Override
        public ExtensionProtocol.Result listTabs() {
            return listOk
                    ? new ExtensionProtocol.Result("id", true, "listed", null, windows)
                    : new ExtensionProtocol.Result("id", false, "Browser extension not connected");
        }

        @Override
        public ExtensionProtocol.Result closeWindow(int windowId) {
            closedWindows.add(windowId);
            return new ExtensionProtocol.Result("id", true, "closed window " + windowId);
        }
    }

    private static Task task(List<String> urls) {
        return new Task("t", "T", TaskStatus.ACTIVE, null, null, null, null,
                urls.isEmpty() ? null : Task.BrowserConfig.ofUrls(urls.toArray(String[]::new)),
                null, null, "");
    }

    @Test
    void closesAllConfiguredUrls() {
        FakeTabs tabs = new FakeTabs();
        ActionResult result = new BrowserCloseAction(tabs)
                .run(task(List.of("https://a.example/", "https://b.example/")));
        assertThat(result.ok()).isTrue();
        assertThat(tabs.closed).containsExactly("https://a.example/", "https://b.example/");
    }

    @Test
    void oneFailingUrlFailsTheActionButAllUrlsAreAttempted() {
        FakeTabs tabs = new FakeTabs();
        tabs.failing = Set.of("https://a.example/");
        ActionResult result = new BrowserCloseAction(tabs)
                .run(task(List.of("https://a.example/", "https://b.example/")));
        assertThat(result).isEqualTo(ActionResult.failure("cannot close https://a.example/"));
        assertThat(tabs.closed).containsExactly("https://a.example/", "https://b.example/");
    }

    @Test
    void completeControlStoresDesktopTabsBeforeClosingTheirWindows() {
        FakeTabs tabs = new FakeTabs();
        tabs.windows = List.of(
                new ExtensionProtocol.TabWindow(1, "GitHub PR",
                        List.of("https://github.com/a/pr/1", "https://docs.example/", "about:newtab")),
                new ExtensionProtocol.TabWindow(2, "Elsewhere", List.of("https://other.example/")));
        List<List<String>> stored = new ArrayList<>();
        List<Integer> closedBeforeStore = new ArrayList<>();
        BrowserCloseAction action = new BrowserCloseAction(tabs,
                t -> "jabref",
                desktop -> List.of("GitHub PR — Mozilla Firefox"),
                (t, urls) -> {
                    stored.add(urls);
                    closedBeforeStore.addAll(tabs.closedWindows);
                    return null;
                });
        assertThat(action.isConfigured(task(List.of()))).isTrue();
        ActionResult result = action.run(task(List.of()));
        assertThat(result.ok()).isTrue();
        // Only the caption-matched window is stored and closed; about: URLs
        // are not worth reopening. The store happens before any close.
        assertThat(stored).containsExactly(List.of("https://github.com/a/pr/1", "https://docs.example/"));
        assertThat(closedBeforeStore).isEmpty();
        assertThat(tabs.closedWindows).containsExactly(1);
        assertThat(tabs.closed).isEmpty();
    }

    @Test
    void failingStoreClosesNoWindow() {
        FakeTabs tabs = new FakeTabs();
        tabs.windows = List.of(
                new ExtensionProtocol.TabWindow(1, "GitHub PR", List.of("https://a.example/")));
        BrowserCloseAction action = new BrowserCloseAction(tabs,
                t -> "jabref",
                desktop -> List.of("GitHub PR — Mozilla Firefox"),
                (t, urls) -> "disk full");
        ActionResult result = action.run(task(List.of()));
        assertThat(result.ok()).isFalse();
        assertThat(result.detail()).contains("disk full");
        assertThat(tabs.closedWindows).isEmpty();
    }

    @Test
    void unavailableDesktopListerSkipsCaptureButStillClosesOwnUrls() {
        FakeTabs tabs = new FakeTabs();
        BrowserCloseAction action = new BrowserCloseAction(tabs,
                t -> "jabref",
                desktop -> null,
                (t, urls) -> {
                    throw new AssertionError("nothing to store without a capture");
                });
        ActionResult result = action.run(task(List.of("https://a.example/")));
        assertThat(result.ok()).isTrue();
        assertThat(result.detail()).contains("desktop capture unavailable");
        assertThat(tabs.closed).containsExactly("https://a.example/");
        assertThat(tabs.closedWindows).isEmpty();
    }

    @Test
    void disconnectedExtensionFailsTheCapture() {
        FakeTabs tabs = new FakeTabs();
        tabs.listOk = false;
        BrowserCloseAction action = new BrowserCloseAction(tabs,
                t -> "jabref",
                desktop -> List.of("GitHub PR — Mozilla Firefox"),
                (t, urls) -> null);
        ActionResult result = action.run(task(List.of()));
        assertThat(result).isEqualTo(ActionResult.failure("Browser extension not connected"));
        assertThat(tabs.closedWindows).isEmpty();
    }
}
