package com.contextswitcher.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/// One entry of the `hosts:` list in `settings.yaml` — a machine sessions run
/// on and what ContextSwitcher knows about it there.
///
/// `destination` is the ssh destination as typed (`koppor@nixos`, an alias,
/// `wsl:Ubuntu`) — kept whole, user part included, the way ssh names it.
/// `active` false switches the host off: never contacted by the background
/// polls, its tasks and categories hidden (`dsn~host-on-off~2`).
/// `workspaces` is the directory new workspaces roots go under on that host
/// (asked when the host is added and on its first category), and
/// `refactoringMiner` the unzipped RefactoringMiner release there — null when
/// unset, which turns the refactoring view and badge off for that host.
// [impl->dsn~host-table~1]
public record HostSettings(String destination, boolean active, @Nullable String workspaces,
        @Nullable String refactoringMiner) {

    public HostSettings {
        destination = destination.strip();
        workspaces = blankToNull(workspaces);
        refactoringMiner = blankToNull(refactoringMiner);
    }

    /// An active host with nothing else known yet.
    public static HostSettings of(String destination) {
        return new HostSettings(destination, true, null, null);
    }

    /// A copy switched on or off.
    public HostSettings withActive(boolean on) {
        return new HostSettings(destination, on, workspaces, refactoringMiner);
    }

    /// A copy with `dir` as its workspaces directory.
    public HostSettings withWorkspaces(@Nullable String dir) {
        return new HostSettings(destination, active, dir, refactoringMiner);
    }

    /// A copy with `dir` as its RefactoringMiner directory.
    public HostSettings withRefactoringMiner(@Nullable String dir) {
        return new HostSettings(destination, active, workspaces, dir);
    }

    /// Whether this entry is `other` — the same destination, ignoring case
    /// (host names are case-insensitive, and so is a hand-typed alias in
    /// practice).
    public boolean is(@Nullable String other) {
        return other != null && destination.equalsIgnoreCase(other.strip());
    }

    /// The YAML shape: `destination` and `active` always, the directories
    /// only when set, so an entry stays one or two lines.
    public Map<String, Object> toYaml() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("destination", destination);
        map.put("active", active);
        if (workspaces != null) {
            map.put("workspaces", workspaces);
        }
        if (refactoringMiner != null) {
            map.put("refactoringMiner", refactoringMiner);
        }
        return map;
    }

    /// The entry a YAML list item describes: a map with at least
    /// `destination`, or a bare string (an active host). Null for anything
    /// else — the file is hand-edited, so a broken entry is skipped rather
    /// than failing the whole load.
    static @Nullable HostSettings fromYaml(@Nullable Object item) {
        if (item instanceof String destination && !destination.isBlank()) {
            return of(destination);
        }
        if (!(item instanceof Map<?, ?> map) || map.get("destination") == null
                || map.get("destination").toString().isBlank()) {
            return null;
        }
        boolean active = !(map.get("active") instanceof Boolean on) || on;
        return new HostSettings(map.get("destination").toString(), active,
                text(map.get("workspaces")), text(map.get("refactoringMiner")));
    }

    private static @Nullable String text(@Nullable Object value) {
        return value == null ? null : value.toString();
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
