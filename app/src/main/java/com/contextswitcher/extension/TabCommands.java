package com.contextswitcher.extension;

import org.jspecify.annotations.Nullable;

/// Tab commands the browser extension executes for the app. Implemented by
/// `ExtensionServer`; an interface so switch actions stay unit-testable
/// without a running WebSocket server.
public interface TabCommands {

    /// Focuses the tab showing exactly `url` (raising its window), opening it
    /// when missing. Fails when no extension is connected or it does not
    /// answer in time.
    default ExtensionProtocol.Result focusUrl(String url) {
        return focusUrl(url, true);
    }

    /// Focus-or-open with the opening under the caller's control: `false` asks
    /// only whether a tab already exists (answered with
    /// [ExtensionProtocol#NO_TAB]), so the caller can prepare *where* a new tab
    /// is to land before asking for it.
    // [impl->dsn~pr-open-on-category-desktop~3]
    default ExtensionProtocol.Result focusUrl(String url, boolean openIfMissing) {
        return focusUrl(url, openIfMissing, null);
    }

    /// Focus-or-open into a named window: `windowTitle` is the OS caption of
    /// the window a tab that must be opened goes into (null: Firefox's own
    /// choice).
    // [impl->dsn~pr-open-on-category-desktop~3]
    default ExtensionProtocol.Result focusUrl(String url, boolean openIfMissing, @Nullable String windowTitle) {
        return focusUrl(url, openIfMissing, windowTitle, null);
    }

    /// Focus-or-open collecting the tab in a Firefox tab group: `group` is the
    /// group's name (null: leave the tab where it is).
    // [impl->dsn~browser-tab-group~2]
    default ExtensionProtocol.Result focusUrl(String url, boolean openIfMissing, @Nullable String windowTitle,
            @Nullable String group) {
        return focusUrl(url, openIfMissing, windowTitle, group, false);
    }

    /// Focus-or-open in the background: `background` leaves the tab
    /// unactivated and its window unraised, and appends it at the end of the
    /// group — how every URL of a task but the first is opened.
    // [impl->dsn~browser-tab-group~2]
    ExtensionProtocol.Result focusUrl(String url, boolean openIfMissing, @Nullable String windowTitle,
            @Nullable String group, boolean background);

    /// Closes all tabs showing exactly `url`.
    ExtensionProtocol.Result closeUrl(String url);

    /// Lists every open Firefox window with its title and tab URLs
    /// (`result.windows()`), for the complete-control suspend capture.
    // [impl->dsn~complete-control-desktop~1]
    ExtensionProtocol.Result listTabs();

    /// Closes the Firefox window `windowId` (an id from [#listTabs]) with all
    /// its tabs.
    // [impl->dsn~complete-control-desktop~1]
    ExtensionProtocol.Result closeWindow(int windowId);
}
