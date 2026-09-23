package com.contextswitcher.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

/// The rows of the `Ctrl+J` jump-to-category popup: the categories whose name
/// contains the query, those on the active virtual desktop first, then the
/// rest. Kept out of [MainWindow] so the grouping is unit-testable.
// [impl->dsn~category-jump~1]
final class CategoryJump {

    /// A category name, or a group heading (`header`) that cannot be picked.
    record Row(String text, boolean header) {
    }

    private CategoryJump() {
    }

    /// `desktopOf` maps a category to its desktop name (fallback included);
    /// `active` is the active desktop's name, null when unknown — then there
    /// is nothing to group by and the matches come without headings.
    static List<Row> rows(Collection<String> categories, Function<String, @Nullable String> desktopOf,
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
}
