package com.contextswitcher.ui;

import org.jspecify.annotations.Nullable;

/// Shortens a task's paths below the directory its category header already
/// shows. Kept out of [TaskListCell], which cannot be loaded without a running
/// JavaFX toolkit, so the rule is unit-testable.
// [impl->dsn~category-header-path~1]
final class WorkspacePaths {

    private WorkspacePaths() {
    }

    /// `path` relative to `root` when it lies strictly below it (either
    /// separator, trailing ones ignored), else `path` unchanged.
    static String belowRoot(String path, @Nullable String root) {
        if (root == null) {
            return path;
        }
        String base = stripTrailingSeparators(root);
        if (base.isEmpty() || path.length() <= base.length() + 1 || !path.startsWith(base)) {
            return path;
        }
        char separator = path.charAt(base.length());
        if (separator != '/' && separator != '\\') {
            return path;
        }
        String below = stripTrailingSeparators(path.substring(base.length() + 1));
        return below.isEmpty() ? path : below;
    }

    private static String stripTrailingSeparators(String path) {
        int end = path.length();
        while (end > 0 && (path.charAt(end - 1) == '/' || path.charAt(end - 1) == '\\')) {
            end--;
        }
        return path.substring(0, end);
    }
}
