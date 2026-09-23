package com.contextswitcher.tasks;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// A category's `intellij`/`browser`/`folders` defaults, and how a task's own
/// section overrides them.
// [utest->dsn~category-action-defaults~1]
class CategoryActionDefaultsTest {

    private final TaskFileParser parser = new TaskFileParser();

    private static final String CATEGORY = """
            ---
            remote: koppor@devbox
            intellij:
              projectPath: /data/koppor/jabref
            browser:
              urls:
                - https://github.com/JabRef/jabref
            folders:
              - /data/koppor/jabref-workspaces
              - /data/koppor/notes
            ---

            # jabref — group defaults
            """;

    private Task task(String frontmatter) throws TaskParseException {
        return parser.parse("jabref/fix-npe", "---\ntitle: Fix NPE\n" + frontmatter + "---\n\n# Notes\n");
    }

    @Test
    void theCategoryParsesItsActionDefaults() {
        GroupConfig category = parser.parseGroupConfig(CATEGORY);
        assertThat(category.intellij()).isNotNull();
        assertThat(category.intellij().projectPath()).isEqualTo("/data/koppor/jabref");
        assertThat(category.browser()).isNotNull();
        assertThat(category.browser().urls()).containsExactly("https://github.com/JabRef/jabref");
        assertThat(category.folders())
                .containsExactly("/data/koppor/jabref-workspaces", "/data/koppor/notes");
    }

    @Test
    void aTaskWithoutSectionsInheritsEveryCategoryDefault() throws Exception {
        Task effective = task("").withCategoryDefaults(parser.parseGroupConfig(CATEGORY));

        assertThat(effective.intellijProjectPath()).isEqualTo("/data/koppor/jabref");
        assertThat(effective.browser().urls()).containsExactly("https://github.com/JabRef/jabref");
        assertThat(effective.folders())
                .containsExactly("/data/koppor/jabref-workspaces", "/data/koppor/notes");
    }

    /// The task's own section wins as a whole — the category's URLs do not get
    /// appended to it.
    @Test
    void aTaskSectionOverridesTheCategoryDefaultEntirely() throws Exception {
        Task effective = task("""
                browser:
                  urls:
                    - https://github.com/JabRef/jabref/pull/12345
                folders:
                  - /data/koppor/jabref-workspaces/fix-npe
                """).withCategoryDefaults(parser.parseGroupConfig(CATEGORY));

        assertThat(effective.browser().urls())
                .containsExactly("https://github.com/JabRef/jabref/pull/12345");
        assertThat(effective.folders()).containsExactly("/data/koppor/jabref-workspaces/fix-npe");
        // Not overridden, so still the category's.
        assertThat(effective.intellijProjectPath()).isEqualTo("/data/koppor/jabref");
    }

    /// A bare `intellij:` keeps its documented meaning — the project path
    /// falls back to the Claude workspace, not to the category.
    @Test
    void aBareTaskSectionIsAnOverrideToo() throws Exception {
        Task effective = task("""
                intellij:
                claude:
                  cwd: /data/koppor/jabref-workspaces/fix-npe
                """).withCategoryDefaults(parser.parseGroupConfig(CATEGORY));

        assertThat(effective.intellijProjectPath()).isEqualTo("/data/koppor/jabref-workspaces/fix-npe");
    }

    @Test
    void anEmptyCategoryChangesNothing() throws Exception {
        Task plain = task("");
        assertThat(plain.withCategoryDefaults(GroupConfig.EMPTY)).isSameAs(plain);
    }

    /// The category config is user-edited: a half-written section must yield
    /// no default rather than break the whole category.
    @Test
    void aBrokenSectionYieldsNoDefault() {
        GroupConfig category = parser.parseGroupConfig("""
                ---
                remote: koppor@devbox
                browser:
                  urls: not-a-list
                ---
                """);
        assertThat(category.browser()).isNull();
        assertThat(category.remote()).isEqualTo("koppor@devbox");
    }

    @Test
    void foldersAcceptTheSingleFolderScalarToo() throws Exception {
        assertThat(task("folder: C:\\git\\jabref\n").folders()).containsExactly("C:\\git\\jabref");
        assertThat(task("folders:\n  - C:\\git\\jabref\n  - C:\\git\\notes\n").folders())
                .containsExactly("C:\\git\\jabref", "C:\\git\\notes");
        assertThat(task("folders: []\n").folders()).isEmpty();
        assertThat(task("").folders()).isEmpty();
    }

    /// The list wins over a leftover `folder:` scalar in the same file, and
    /// blank entries never reach the action.
    @Test
    void blankFolderEntriesAreDropped() throws Exception {
        assertThat(task("""
                folder: C:\\git\\old
                folders:
                  - C:\\git\\jabref
                  - ""
                """).folders()).containsExactly("C:\\git\\jabref");
    }

    @Test
    void aCategoryWithoutDefaultsLeavesTheTaskUnconfigured() throws Exception {
        GroupConfig category = parser.parseGroupConfig("""
                ---
                remote: koppor@devbox
                ---
                """);
        Task effective = task("").withCategoryDefaults(category);

        assertThat(effective.intellij()).isNull();
        assertThat(effective.browser()).isNull();
        assertThat(effective.folders()).isEqualTo(List.of());
    }
}
