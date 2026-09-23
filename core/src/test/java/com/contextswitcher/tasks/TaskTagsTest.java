package com.contextswitcher.tasks;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~task-tag-model~2]
class TaskTagsTest {

    @Test
    void parsesAYamlList() {
        assertThat(TaskTags.parse(List.of("phone", "jabref"))).containsExactly("phone", "jabref");
    }

    @Test
    void parsesACommaSeparatedScalar() {
        assertThat(TaskTags.parse("phone, jabref, follow up")).containsExactly("phone", "jabref", "follow up");
    }

    @Test
    void keepsSpacesInListItems() {
        assertThat(TaskTags.parse(List.of("follow up", " padded "))).containsExactly("follow up", "padded");
    }

    @Test
    void deduplicatesCaseInsensitivelyKeepingFirstSpelling() {
        assertThat(TaskTags.parse(List.of("Phone", "phone", "JABREF")))
                .containsExactly("Phone", "JABREF");
    }

    @Test
    void parsesNullAsEmpty() {
        assertThat(TaskTags.parse(null)).isEmpty();
    }

    @Test
    void matchesAllRequiresEveryTagCaseInsensitively() {
        assertThat(TaskTags.matchesAll(List.of("Phone", "jabref"), Set.of("phone", "JABREF"))).isTrue();
    }

    @Test
    void matchesAllFailsWhenATagIsMissing() {
        assertThat(TaskTags.matchesAll(List.of("phone"), Set.of("phone", "jabref"))).isFalse();
    }

    @Test
    void emptyRequiredMatchesEveryTask() {
        assertThat(TaskTags.matchesAll(List.of(), Set.of())).isTrue();
    }

    @Test
    void visibleReturnsAllTagsWithNoFilter() {
        assertThat(TaskTags.visible(List.of("phone", "jabref"), Set.of()))
                .containsExactly("phone", "jabref");
    }

    @Test
    void visibleReturnsOnlyActiveTagsInTaskOrder() {
        assertThat(TaskTags.visible(List.of("phone", "jabref", "urgent"), Set.of("URGENT", "phone")))
                .containsExactly("phone", "urgent");
    }

    // [utest->dsn~tag-selection-union~2]
    @Test
    void selectableUnionsConfiguredAndInUseTagsSortedAlphabetically() {
        assertThat(TaskTags.selectable(List.of("phone", "jabref"), List.of("urgent", "buch")))
                .containsExactly("buch", "jabref", "phone", "urgent");
    }

    // [utest->dsn~tag-selection-union~2]
    @Test
    void selectableKeepsConfiguredSpelling() {
        assertThat(TaskTags.selectable(List.of("JabRef", "Phone"), List.of("jabref", "PHONE")))
                .containsExactly("JabRef", "Phone");
    }

    // [utest->dsn~tag-selection-union~2]
    @Test
    void selectableSortsCaseInsensitively() {
        assertThat(TaskTags.selectable(List.of("zebra"), List.of("Alpha", "mitte")))
                .containsExactly("Alpha", "mitte", "zebra");
    }

    // [utest->dsn~tag-auto-color~2]
    @Test
    void autoColorIsStableAndCaseInsensitive() {
        assertThat(TaskTags.autoColor("jabref"))
                .isEqualTo(TaskTags.autoColor("JabRef"))
                .isEqualTo(TaskTags.autoColor(" jabref "))
                .matches("#[0-9a-f]{6}");
    }

    // [utest->dsn~tag-auto-color~2]
    @Test
    void autoColorSpreadsOverDistinctHues() {
        // Not a collision-freeness guarantee — just that different names can
        // land on different hues at all.
        assertThat(List.of("jabref", "paper", "leisure", "buch", "orga", "vortrag", "tool").stream()
                .map(TaskTags::autoColor)
                .distinct()
                .count()).isGreaterThan(1);
    }

    // [utest->dsn~tag-filter-recent~1]
    @Test
    void withRecentMovesTheTagToTheFrontAndCapsTheList() {
        assertThat(TaskTags.withRecent(List.of("a", "b", "c", "d", "e"), "C"))
                .containsExactly("C", "a", "b", "d", "e");
        assertThat(TaskTags.withRecent(List.of("a", "b", "c", "d", "e"), "f"))
                .containsExactly("f", "a", "b", "c", "d");
    }
}
