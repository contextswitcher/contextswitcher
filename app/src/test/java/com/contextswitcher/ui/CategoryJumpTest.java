package com.contextswitcher.ui;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// The jump-to-category popup lists the active desktop's categories first;
/// the Add-task dialog's host picker narrows them to one host.
// [utest->dsn~category-jump~1]
// [utest->dsn~task-create-category-pick~1]
class CategoryJumpTest {

    private static final Map<String, String> DESKTOPS = Map.of("jabref", "work", "Blog", "home", "jabkit", "work");

    @Test
    void theActiveDesktopsCategoriesComeFirst() {
        assertThat(CategoryJump.rows(List.of("jabref", "Blog", "jabkit", "tools"), DESKTOPS::get, "Work", ""))
                .containsExactly(
                        new CategoryJump.Row("This desktop (Work)", true),
                        new CategoryJump.Row("jabkit", false),
                        new CategoryJump.Row("jabref", false),
                        new CategoryJump.Row("Other desktops", true),
                        new CategoryJump.Row("Blog", false),
                        new CategoryJump.Row("tools", false));
    }

    @Test
    void theQueryNarrowsAndEmptyGroupsLoseTheirHeading() {
        assertThat(CategoryJump.rows(List.of("jabref", "Blog", "jabkit"), DESKTOPS::get, "work", "BLO"))
                .containsExactly(new CategoryJump.Row("Other desktops", true), new CategoryJump.Row("Blog", false));
    }

    @Test
    void anUnknownDesktopListsWithoutHeadings() {
        assertThat(CategoryJump.rows(List.of("jabref", "Blog"), DESKTOPS::get, null, ""))
                .containsExactly(new CategoryJump.Row("Blog", false), new CategoryJump.Row("jabref", false));
    }

    @Test
    void theHostPickerListsAllLocalThenEachRemoteOnce() {
        assertThat(CategoryJump.hosts(java.util.Arrays.asList("devbox", "local", null, "Devbox", "alpha")))
                .containsExactly("(all hosts)", "local", "alpha", "devbox");
    }

    @Test
    void aHostKeepsOnlyItsCategories() {
        Map<String, String> hosts = Map.of("jabref", "devbox", "jabref-local", "local", "Blog", "alpha");
        assertThat(CategoryJump.onHost(List.of("jabref", "jabref-local", "Blog", "tools"), hosts::get, "(all hosts)"))
                .containsExactly("jabref", "jabref-local", "Blog", "tools");
        assertThat(CategoryJump.onHost(List.of("jabref", "jabref-local", "Blog", "tools"), hosts::get, "local"))
                .containsExactly("jabref-local");
        assertThat(CategoryJump.onHost(List.of("jabref", "jabref-local", "Blog", "tools"), hosts::get, "DEVBOX"))
                .containsExactly("jabref");
    }
}
