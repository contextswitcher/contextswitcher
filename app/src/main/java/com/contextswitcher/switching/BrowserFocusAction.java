package com.contextswitcher.switching;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.contextswitcher.extension.ExtensionProtocol;
import com.contextswitcher.extension.TabCommands;
import com.contextswitcher.tasks.Task;
import org.jspecify.annotations.Nullable;

/// Focuses (or opens) the task's browser URLs via the extension server. Every
/// `browser.urls` entry is focus-or-opened in the order the task lists them,
/// so the tab group ends up in that order; the first URL is the one activated,
/// the rest are opened in the background. The extension answers with what it
/// did, which becomes the chip detail. No connected extension or a timeout
/// surfaces as a failed chip.
///
/// A task carrying `storedTabs:` — what a complete-control suspend left
/// behind (`req~complete-control-desktop~1`) — first reopens those URLs,
/// each as focus-or-open, so a tab that is still (or again) open is kept
/// rather than duplicated. On a complete-control category the first reopened
/// URL goes through the desktop preparer (switch to the category's desktop,
/// raise or launch a Firefox window there), so the restored tabs land on the
/// desktop they were stored from. The `storedTabs:` key is cleared only when
/// every reopen succeeded; otherwise it stays for the next resume.
///
/// Every focused or opened tab is collected in the task's tab group
/// ([Task#tabGroup()]), so the task's pages sit together in the tab bar.
// [impl->dsn~browser-focus-action~4]
// [impl->dsn~browser-tab-group~3]
public class BrowserFocusAction implements SwitchAction {

    /// Opens `firstUrl` on the named desktop: switch to it, raise a Firefox
    /// window there and open the tab into that window, or launch a fresh
    /// window carrying the URL when the desktop has none — the contract of
    /// `Main.openOnDesktop`. The result is the open's outcome.
    public interface DesktopPreparer {
        ExtensionProtocol.Result prepare(String desktop, String firstUrl);
    }

    /// Removes the `storedTabs:` key from the task's file once the tabs are
    /// reopened; returns an error text, or null on success.
    public interface StoredTabsCleaner {
        @Nullable String clear(Task task);
    }

    private final TabCommands tabs;
    private final Function<Task, @Nullable String> completeControlDesktop;
    private final DesktopPreparer desktopPreparer;
    private final StoredTabsCleaner storedTabs;

    /// Without stored-tab handling (tests, and any caller predating it).
    public BrowserFocusAction(TabCommands tabs) {
        this(tabs, task -> null, (desktop, url) -> tabs.focusUrl(url), task -> null);
    }

    public BrowserFocusAction(TabCommands tabs,
            Function<Task, @Nullable String> completeControlDesktop,
            DesktopPreparer desktopPreparer, StoredTabsCleaner storedTabs) {
        this.tabs = tabs;
        this.completeControlDesktop = completeControlDesktop;
        this.desktopPreparer = desktopPreparer;
        this.storedTabs = storedTabs;
    }

    @Override
    public String name() {
        return "browser";
    }

    @Override
    public boolean isConfigured(Task task) {
        return (task.browser() != null && !task.browser().urls().isEmpty())
                || !task.storedTabs().isEmpty();
    }

    @Override
    public ActionResult run(Task task) {
        List<String> failures = new ArrayList<>();
        List<String> details = new ArrayList<>();
        if (!task.storedTabs().isEmpty()) {
            reopenStoredTabs(task, failures, details);
        }
        Task.BrowserConfig browser = task.browser();
        if (browser != null && !browser.urls().isEmpty()) {
            openConfiguredUrls(browser.urls(), task.tabGroup(), failures, details);
        }
        if (details.isEmpty() && failures.isEmpty()) {
            return ActionResult.failure("no browser URL configured");
        }
        if (!failures.isEmpty()) {
            return ActionResult.failure(String.join("; ", failures));
        }
        return ActionResult.success(String.join("; ", details));
    }

    /// Focus-or-opens every configured URL in the task's order — the tab group
    /// is built front to back — with only the first one activated, so the
    /// task's primary page is what the user lands on and the rest arrive
    /// behind it.
    private void openConfiguredUrls(List<String> urls, String group,
            List<String> failures, List<String> details) {
        ExtensionProtocol.Result first = tabs.focusUrl(urls.getFirst(), true, null, group, false);
        for (String url : urls.subList(1, urls.size())) {
            ExtensionProtocol.Result result = tabs.focusUrl(url, true, null, group, true);
            if (!result.ok()) {
                failures.add(result.detail() + " — " + url);
            }
        }
        String detail = urls.size() == 1
                ? first.detail()
                : first.detail() + " (+" + (urls.size() - 1) + " more)";
        (first.ok() ? details : failures).add(detail);
    }

    /// Reopens the suspend's stored tabs. Overlap keeps: focus-or-open never
    /// closes and reopens a tab that already exists. The desktop dance runs
    /// only when the *first* URL has no tab yet (probed with
    /// `openIfMissing: false`) — when it is already open somewhere, yanking
    /// the desktop would be worse than opening beside it.
    private void reopenStoredTabs(Task task, List<String> failures, List<String> details) {
        List<String> urls = task.storedTabs();
        List<String> toOpen = urls;
        boolean allOk = true;
        // The first tab is the one activated — unless the desktop preparer
        // already opened (and focused) it, leaving only background tabs.
        boolean foregroundPending = true;
        String desktop = completeControlDesktop.apply(task);
        if (desktop != null) {
            ExtensionProtocol.Result probe = tabs.focusUrl(urls.getFirst(), false);
            if (!probe.ok() && ExtensionProtocol.NO_TAB.equals(probe.detail())) {
                ExtensionProtocol.Result opened = desktopPreparer.prepare(desktop, urls.getFirst());
                toOpen = urls.subList(1, urls.size());
                foregroundPending = false;
                if (opened.ok()) {
                    details.add(opened.detail());
                } else {
                    allOk = false;
                    failures.add(opened.detail() + " — " + urls.getFirst());
                }
            }
        }
        for (String url : toOpen) {
            ExtensionProtocol.Result result = tabs.focusUrl(url, true, null, task.tabGroup(), !foregroundPending);
            foregroundPending = false;
            if (!result.ok()) {
                allOk = false;
                failures.add(result.detail() + " — " + url);
            }
        }
        if (!allOk) {
            failures.add("stored tabs kept for the next resume");
            return;
        }
        String clearError = storedTabs.clear(task);
        if (clearError != null) {
            failures.add("stored tabs reopened but not cleared: " + clearError);
            return;
        }
        details.add("reopened " + urls.size() + " stored tab(s)");
    }
}
