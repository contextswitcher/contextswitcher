package com.contextswitcher.switching;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import com.contextswitcher.config.EnvironmentProfile;
import com.contextswitcher.config.EnvironmentProfiles;
import com.contextswitcher.tasks.Task;
import org.jspecify.annotations.Nullable;

/// Puts the machine into the task's category's environment profile (chip
/// `profile`): `settings.yaml`'s `default`, under the category's `profile:`,
/// under the status bar's manual override — layered per key
/// ([EnvironmentProfiles#effective]).
///
/// Configured whenever `settings.yaml` defines any profile: a task of a
/// category without `profile:` still applies `default`, which is what turns
/// the lecture-hall look back into the desk look. The machine-level keys go
/// to an injected applier (`WindowsEnvironment`, which itself skips what is
/// already in place); the bookmarks bar to the browser extension, sent only
/// when it differs from what was last put into effect — so a desk session
/// with Firefox closed does not fail every switch over a bar nobody asked to
/// change.
///
/// [#reapply] re-runs the last category's profile without a switch — the
/// status bar's override toggle, which must take effect right away.
// [impl->dsn~environment-profiles~1]
public class EnvironmentProfileAction implements SwitchAction {

    private final Supplier<EnvironmentProfiles> profiles;
    /// A group's `CONTEXTSWITCHER.md` `profile:` by group key; null when the
    /// group has no config file or names none.
    private final Function<String, @Nullable String> groupProfile;
    private final Supplier<@Nullable String> override;
    private final Function<EnvironmentProfile, ActionResult> machine;
    private final Function<Boolean, ActionResult> bookmarksBar;

    /// The group of the task last switched to (`""`: a root-level task) —
    /// what [#reapply] re-applies; null before the first switch.
    private @Nullable String lastGroup;
    /// The bookmarks-bar state last put into effect; null when unknown.
    private EnvironmentProfile.@Nullable BookmarksBar appliedBookmarksBar;

    public EnvironmentProfileAction(Supplier<EnvironmentProfiles> profiles,
            Function<String, @Nullable String> groupProfile, Supplier<@Nullable String> override,
            Function<EnvironmentProfile, ActionResult> machine,
            Function<Boolean, ActionResult> bookmarksBar) {
        this.profiles = profiles;
        this.groupProfile = groupProfile;
        this.override = override;
        this.machine = machine;
        this.bookmarksBar = bookmarksBar;
    }

    @Override
    public String name() {
        return "profile";
    }

    @Override
    public boolean isConfigured(Task task) {
        return !profiles.get().isEmpty();
    }

    @Override
    public ActionResult run(Task task) {
        int slash = task.id().lastIndexOf('/');
        return apply(slash < 0 ? "" : task.id().substring(0, slash));
    }

    /// Applies the profile of the category last switched to (the default
    /// profile before any switch) under the current override.
    public ActionResult reapply() {
        String group;
        synchronized (this) {
            group = lastGroup;
        }
        return apply(group == null ? "" : group);
    }

    private synchronized ActionResult apply(String group) {
        lastGroup = group;
        EnvironmentProfiles all = profiles.get();
        String categoryProfile = group.isEmpty() ? null : groupProfile.apply(group);
        String manual = override.get();
        EnvironmentProfile effective;
        try {
            effective = all.effective(categoryProfile, manual);
        } catch (IllegalArgumentException e) {
            return ActionResult.failure(e.getMessage());
        }
        String label = categoryProfile == null ? EnvironmentProfiles.DEFAULT : categoryProfile;
        if (manual != null) {
            label += " + " + manual;
        }
        List<String> changes = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        ActionResult machineResult = machine.apply(effective);
        (machineResult.ok() ? changes : failures).add(machineResult.detail());
        EnvironmentProfile.BookmarksBar bar = effective.bookmarksBar();
        if (bar != null && bar != appliedBookmarksBar) {
            ActionResult barResult = bookmarksBar.apply(bar == EnvironmentProfile.BookmarksBar.HIDDEN);
            if (barResult.ok()) {
                appliedBookmarksBar = bar;
                changes.add("bookmarks bar " + bar.key());
            } else {
                failures.add("bookmarks bar: " + barResult.detail());
            }
        }
        changes.removeIf(String::isBlank);
        failures.removeIf(String::isBlank);
        if (!failures.isEmpty()) {
            return ActionResult.failure(label + ": " + String.join("; ", failures));
        }
        return ActionResult.success(label + ": "
                + (changes.isEmpty() ? "already in place" : String.join(", ", changes)));
    }
}
