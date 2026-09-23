package com.contextswitcher.switching;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import com.contextswitcher.extension.ExtensionProtocol;
import com.contextswitcher.extension.TabCommands;
import com.contextswitcher.tasks.Task;
import org.jspecify.annotations.Nullable;

/// Closes the task's browser tabs when the task is suspended
/// (`req~task-suspend-resume~2`): one `close-url` per `browser.urls` entry —
/// unlike focus, closing applies to all of the task's URLs. Resume needs no
/// counterpart action: it runs as a regular switch, and focus-or-open reopens
/// the URL.
///
/// When the task's category runs its desktop under **complete control**
/// (`req~complete-control-desktop~1`), the suspend additionally captures every
/// Firefox window on that desktop first: their tab URLs are stored into the
/// task file (so the next resume reopens them) and the windows closed —
/// stored strictly *before* closing, so a failing store never loses tabs.
/// Windows are matched between the OS desktop (captions from
/// [com.contextswitcher.local.BrowserDesktopWindows]) and the extension
/// (`list-tabs` titles) by caption containment, since both derive from the
/// active tab's title.
// [impl->dsn~browser-close-action~2]
public class BrowserCloseAction implements SwitchAction {

    /// Lists the captions of the Firefox windows on a named virtual desktop;
    /// null when the mechanism is unavailable (the capture is then skipped).
    public interface DesktopWindows {
        @Nullable List<String> captionsOn(String desktopName);
    }

    /// Writes the captured URLs into the task's file (`storedTabs:`);
    /// returns an error text, or null on success.
    public interface StoredTabsWriter {
        @Nullable String store(Task task, List<String> urls);
    }

    private final TabCommands tabs;
    private final Function<Task, @Nullable String> completeControlDesktop;
    private final DesktopWindows desktopWindows;
    private final StoredTabsWriter storedTabs;

    /// Without complete control — the plain per-URL close (tests, and any
    /// caller predating the feature).
    public BrowserCloseAction(TabCommands tabs) {
        this(tabs, task -> null, name -> null, (task, urls) -> null);
    }

    /// `completeControlDesktop` resolves the task's category to the desktop
    /// name its `desktop: {…, completeControl: true}` names — null when the
    /// category does not run complete control.
    public BrowserCloseAction(TabCommands tabs,
            Function<Task, @Nullable String> completeControlDesktop,
            DesktopWindows desktopWindows, StoredTabsWriter storedTabs) {
        this.tabs = tabs;
        this.completeControlDesktop = completeControlDesktop;
        this.desktopWindows = desktopWindows;
        this.storedTabs = storedTabs;
    }

    @Override
    public String name() {
        return "browser";
    }

    @Override
    public boolean isConfigured(Task task) {
        return (task.browser() != null && !task.browser().urls().isEmpty())
                || completeControlDesktop.apply(task) != null;
    }

    @Override
    public ActionResult run(Task task) {
        List<String> failures = new ArrayList<>();
        List<String> details = new ArrayList<>();
        String desktop = completeControlDesktop.apply(task);
        if (desktop != null) {
            captureAndCloseDesktop(desktop, task, failures, details);
        }
        Task.BrowserConfig browser = task.browser();
        if (browser != null) {
            for (String url : browser.urls()) {
                ExtensionProtocol.Result result = tabs.closeUrl(url);
                (result.ok() ? details : failures).add(result.detail());
            }
        }
        if (details.isEmpty() && failures.isEmpty()) {
            return ActionResult.failure("no browser URL configured");
        }
        if (!failures.isEmpty()) {
            return ActionResult.failure(String.join("; ", failures));
        }
        return ActionResult.success(String.join("; ", details));
    }

    /// The complete-control capture: desktop captions → matching extension
    /// windows → store their tab URLs → close the windows. Each step degrades
    /// gracefully — an unavailable lister (non-Windows) or no window on the
    /// desktop is a detail, not a failure; only a failing extension call or a
    /// failing store fails the chip (and a failing store closes nothing).
    private void captureAndCloseDesktop(String desktop, Task task,
            List<String> failures, List<String> details) {
        List<String> captions = desktopWindows.captionsOn(desktop);
        if (captions == null) {
            details.add("desktop capture unavailable");
            return;
        }
        if (captions.isEmpty()) {
            details.add("no Firefox window on \"" + desktop + "\"");
            return;
        }
        ExtensionProtocol.Result listed = tabs.listTabs();
        List<ExtensionProtocol.TabWindow> windows = listed.windows();
        if (!listed.ok() || windows == null) {
            failures.add(listed.ok() ? "extension listed no windows" : listed.detail());
            return;
        }
        List<ExtensionProtocol.TabWindow> matched = windows.stream()
                .filter(window -> matchesAny(window.title(), captions))
                .toList();
        if (matched.isEmpty()) {
            details.add("no extension window matches the desktop's " + captions.size() + " window(s)");
            return;
        }
        Set<String> urls = new LinkedHashSet<>();
        for (ExtensionProtocol.TabWindow window : matched) {
            window.urls().stream().filter(BrowserCloseAction::reopenable).forEach(urls::add);
        }
        if (!urls.isEmpty()) {
            String storeError = storedTabs.store(task, List.copyOf(urls));
            if (storeError != null) {
                failures.add("tabs not stored (" + storeError + "), windows left open");
                return;
            }
        }
        int closed = 0;
        for (ExtensionProtocol.TabWindow window : matched) {
            ExtensionProtocol.Result result = tabs.closeWindow(window.id());
            if (result.ok()) {
                closed++;
            } else {
                failures.add(result.detail());
            }
        }
        details.add("stored %d tab(s), closed %d window(s) on \"%s\""
                .formatted(urls.size(), closed, desktop));
    }

    /// A caption match in either direction: the OS caption is the extension
    /// window's title plus a browser suffix ("… — Mozilla Firefox"), and
    /// either side may truncate.
    private static boolean matchesAny(String title, List<String> captions) {
        if (title.isBlank()) {
            return false;
        }
        return captions.stream().anyMatch(caption ->
                caption.contains(title) || title.contains(caption));
    }

    /// Only web pages are worth storing: `about:`, `moz-extension:` and
    /// friends would reopen as noise (or not at all).
    private static boolean reopenable(String url) {
        return url.startsWith("https://") || url.startsWith("http://");
    }
}
