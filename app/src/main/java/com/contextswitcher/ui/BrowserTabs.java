package com.contextswitcher.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.contextswitcher.extension.ExtensionProtocol;
import com.contextswitcher.extension.ExtensionProtocol.TabWindow;
import com.contextswitcher.tasks.Task;

/// What the *Browser URLs* field asks the browser, set on [MainWindow] by `Main`.
/// Both calls run off the FX thread and answer on it.
// [impl->dsn~browser-task-window~1]
// [impl->dsn~browser-url-order~1]
public interface BrowserTabs {

    /// Every open window with its tabs.
    void list(Consumer<ExtensionProtocol.Result> answer);

    /// Brings up the window holding the tab group `group` with all of `urls`,
    /// building it when there is none (`task-window`).
    void openTaskWindow(List<String> urls, String group, Consumer<ExtensionProtocol.Result> answer);

    /// `lines` (`url — title`) rearranged the way their pages stand in the
    /// browser. An entry without a tab keeps its place, so one closed tab does
    /// not throw it to an end of the list.
    static List<String> inTabOrder(List<String> lines, List<TabWindow> windows) {
        List<String> tabs = windows.stream().flatMap(window -> window.urls().stream()).toList();
        List<Integer> positions = lines.stream()
                .map(line -> position(ConfigForm.urlEntry(line).url(), tabs)).toList();
        List<Integer> open = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (positions.get(i) >= 0) {
                open.add(i);
            }
        }
        // Stable: two entries matching one tab stay in their order.
        Iterator<Integer> sorted = open.stream().sorted(Comparator.comparing(positions::get)).iterator();
        List<String> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            result.add(lines.get(positions.get(i) >= 0 ? sorted.next() : i));
        }
        return result;
    }

    /// The title of the tab showing `url`, or of one under it.
    static Optional<String> titleOf(String url, List<TabWindow> windows) {
        List<String> tabs = windows.stream().flatMap(window -> window.urls().stream()).toList();
        List<String> titles = windows.stream().flatMap(window -> window.titles().stream()).toList();
        int position = position(url, tabs);
        return position < 0 || titles.size() != tabs.size() || titles.get(position).isBlank()
                ? Optional.empty()
                : Optional.of(titles.get(position));
    }

    /// The tab showing `url`: the exact one, else the first page under it; -1
    /// when none does.
    private static int position(String url, List<String> tabs) {
        int under = -1;
        for (int i = 0; i < tabs.size(); i++) {
            int match = Task.urlMatch(tabs.get(i), url);
            if (match == 2) {
                return i;
            }
            if (match == 1 && under < 0) {
                under = i;
            }
        }
        return under;
    }
}
