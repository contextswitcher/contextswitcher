package com.contextswitcher.ui;

import java.util.List;
import java.util.function.Consumer;

/// What a *Browser URLs* field does beyond holding text; [MainWindow] answers,
/// status messages included. Every answer arrives on the FX thread.
// [impl->dsn~browser-url-links~1]
interface BrowserUrlActions {

    /// Shows the page of a `url — title` line in the browser.
    void open(String line);

    /// Answers with the page's title, if one can be found.
    void pageTitle(String url, Consumer<String> answer);

    /// Whether the file is a task: a category has no tab group of its own.
    boolean hasTabGroup();

    /// Brings up the task's browser window for `lines`, then runs `done`.
    void openWindow(List<String> lines, Runnable done);

    /// Answers with `lines` in the browser's tab order, or unchanged if it
    /// cannot be had.
    void inTabOrder(List<String> lines, Consumer<List<String>> answer);
}
