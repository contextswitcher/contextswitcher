package com.contextswitcher.ui;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~task-field-form~4]
class SuggestionsTest {

    private static final List<String> DESKTOPS = List.of("misc", "JabRef", "contextswitcher", "Ref Tools");

    @Test
    void blankMatchesEverythingInOrder() {
        assertThat(Suggestions.matching(DESKTOPS, " ")).isEqualTo(DESKTOPS);
    }

    @Test
    void prefixMatchesComeBeforeInfixMatchesIgnoringCase() {
        assertThat(Suggestions.matching(DESKTOPS, "ref")).containsExactly("Ref Tools", "JabRef");
    }

    @Test
    void theValueAlreadyTypedIsNotSuggested() {
        assertThat(Suggestions.matching(DESKTOPS, "jabref")).isEmpty();
    }

    @Test
    void noMatchSuggestsNothing() {
        assertThat(Suggestions.matching(DESKTOPS, "xyz")).isEmpty();
    }

    @Test
    void longListsAreCut() {
        List<String> many = IntStream.range(0, 20).mapToObj(i -> "desk" + i).toList();
        assertThat(Suggestions.matching(many, "desk")).hasSize(Suggestions.MAX_SHOWN);
    }
}
