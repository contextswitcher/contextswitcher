package com.contextswitcher.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

/// The rows of the `Ctrl+J` jump-to-category popup — and of the Add-task
/// dialog's category combo: the categories whose name contains the query,
/// the recently jumped-to ones on top (popup only), then those on the active
/// virtual desktop, then the rest. Kept out of
/// [MainWindow] so the grouping is unit-testable.
// [impl->dsn~category-jump~3]
// [impl->dsn~task-create-category-pick~1]
final class CategoryJump {

    /// The host picker's "no filter" entry.
    static final String ALL_HOSTS = "(all hosts)";
    /// The host picker's entry for local categories (a CS config with a
    /// directory but no `remote:`).
    static final String LOCAL_HOST = "local";

    /// A category name, or a group heading (`header`) that cannot be picked.
    record Row(String text, boolean header) {
    }

    /// How many recent categories the popup lists on top.
    static final int RECENT_LIMIT = 5;

    private CategoryJump() {
    }

    /// The recent list after jumping to `picked`: it moves to the front,
    /// the oldest falls off beyond [#RECENT_LIMIT].
    // [impl->dsn~category-jump~3]
    static List<String> remember(List<String> recent, String picked) {
        List<String> next = new ArrayList<>(List.of(picked));
        recent.stream().filter(name -> !name.equals(picked)).forEach(next::add);
        return List.copyOf(next.subList(0, Math.min(RECENT_LIMIT, next.size())));
    }

    /// `desktopOf` maps a category to its desktop name (fallback included);
    /// `active` is the active desktop's name, null when unknown — then there
    /// is nothing to group by and the matches come without headings.
    static List<Row> rows(Collection<String> categories, Function<String, @Nullable String> desktopOf,
            @Nullable String active, String query) {
        return rows(categories, List.of(), desktopOf, active, query);
    }

    /// Like the other `rows`, with the matching `recent` categories (newest
    /// first, gone ones left out) under a "Recent" heading on top; they stay
    /// at their usual place below as well.
    // [impl->dsn~category-jump~3]
    static List<Row> rows(Collection<String> categories, List<String> recent,
            Function<String, @Nullable String> desktopOf, @Nullable String active, String query) {
        List<Row> top = new ArrayList<>();
        String lower = query.strip().toLowerCase(Locale.ROOT);
        recent.stream()
                .filter(name -> categories.contains(name) && name.toLowerCase(Locale.ROOT).contains(lower))
                .forEach(name -> top.add(new Row(name, false)));
        List<Row> rest = groupedRows(categories, desktopOf, active, query);
        if (top.isEmpty()) {
            return rest;
        }
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Recent", true));
        rows.addAll(top);
        if (!rest.isEmpty() && !rest.getFirst().header()) {
            rows.add(new Row("All categories", true));
        }
        rows.addAll(rest);
        return rows;
    }

    private static List<Row> groupedRows(Collection<String> categories, Function<String, @Nullable String> desktopOf,
            @Nullable String active, String query) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        List<String> matches = categories.stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).contains(needle))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        if (active == null) {
            return matches.stream().map(name -> new Row(name, false)).toList();
        }
        List<Row> here = new ArrayList<>();
        List<Row> elsewhere = new ArrayList<>();
        for (String name : matches) {
            (active.equalsIgnoreCase(desktopOf.apply(name)) ? here : elsewhere).add(new Row(name, false));
        }
        List<Row> rows = new ArrayList<>();
        if (!here.isEmpty()) {
            rows.add(new Row("This desktop (" + active + ")", true));
            rows.addAll(here);
        }
        if (!elsewhere.isEmpty()) {
            rows.add(new Row("Other desktops", true));
            rows.addAll(elsewhere);
        }
        return rows;
    }

    /// The host picker's entries: all, local, then every remote named by some
    /// category (`hosts` are the categories' hosts, null for one with neither).
    static List<String> hosts(Collection<@Nullable String> hosts) {
        List<String> entries = new ArrayList<>(List.of(ALL_HOSTS, LOCAL_HOST));
        TreeSet<String> remotes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        hosts.stream().filter(host -> host != null && !host.equals(LOCAL_HOST)).forEach(remotes::add);
        entries.addAll(remotes);
        return entries;
    }

    /// The categories the host picker keeps: all of them for [#ALL_HOSTS],
    /// else those whose host (`hostOf`) is `host`.
    static List<String> onHost(Collection<String> categories, Function<String, @Nullable String> hostOf,
            String host) {
        return categories.stream()
                .filter(name -> ALL_HOSTS.equals(host) || host.equalsIgnoreCase(hostOf.apply(name)))
                .toList();
    }
}
