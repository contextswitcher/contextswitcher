package com.contextswitcher.tasks;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// A category created from a repository URL: the folder name derived from
/// the URL and the real-valued `CONTEXTSWITCHER.md` written for it.
// [utest->dsn~category-from-url~4]
class CategoryFromUrlTest {

    @Test
    void repoNameIsTheSluggedLastPathSegment() {
        assertThat(TaskFileParser.repoName("https://github.com/Gram21/SaiLoR")).isEqualTo("sailor");
        assertThat(TaskFileParser.repoName(" https://github.com/Gram21/SaiLoR.git/ ")).isEqualTo("sailor");
        assertThat(TaskFileParser.repoName("https://github.com/Gram21/SaiLoR?tab=readme#x")).isEqualTo("sailor");
        assertThat(TaskFileParser.repoName("https://github.com/JabRef/jabref-browser-extension"))
                .isEqualTo("jabref-browser-extension");
    }

    @Test
    void nonRepoTextYieldsNoName() {
        assertThat(TaskFileParser.repoName("Fix the NPE in the importer")).isNull();
        assertThat(TaskFileParser.repoName("https://github.com/")).isNull();
        assertThat(TaskFileParser.repoName("git@github.com:Gram21/SaiLoR.git")).isNull();
    }

    @Test
    void theConfigCarriesRealValuesAndParses() {
        String content = TaskFileParser.groupConfigForRepo("sailor", "https://github.com/Gram21/SaiLoR",
                "koppor@devbox", "/data/koppor/sailor-workspaces", null);
        GroupConfig config = new TaskFileParser().parseGroupConfig(content);
        assertThat(config.remote()).isEqualTo("koppor@devbox");
        assertThat(config.workspacesRoot()).isEqualTo("/data/koppor/sailor-workspaces");
        assertThat(config.mainCheckout()).isEqualTo("/data/koppor/sailor-workspaces/sailor");
        assertThat(config.repo()).isEqualTo("https://github.com/Gram21/SaiLoR");
        assertThat(config.tags()).isEmpty();
        assertThat(config.bootstrapWorktree()).isTrue();
        assertThat(config.desktop()).isNull();
    }

    /// Created while the active-desktop filter is on: the config names that
    /// desktop, so the new category is not hidden by the very filter it was
    /// created under.
    // [utest->dsn~category-create-ui~2]
    @Test
    void theConfigCarriesTheFilteredDesktop() {
        String content = TaskFileParser.groupConfigForRepo("sailor", "https://github.com/Gram21/SaiLoR",
                "koppor@devbox", "/data/koppor/sailor-workspaces", "research");
        GroupConfig config = new TaskFileParser().parseGroupConfig(content);
        assertThat(config.desktop()).isEqualTo("research");
        assertThat(config.repo()).isEqualTo("https://github.com/Gram21/SaiLoR");
    }

    /// The setup wizard's local choice: no `remote:` line, so the category
    /// is a local one (`Add local Claude`), the rest as for a remote.
    // [utest->dsn~setup-wizard~8]
    @Test
    void withoutARemoteTheConfigIsALocalCategory() {
        String content = TaskFileParser.groupConfigForRepo("sailor", "https://github.com/Gram21/SaiLoR",
                null, "/home/me/sailor-workspaces", null);
        GroupConfig config = new TaskFileParser().parseGroupConfig(content);
        assertThat(config.remote()).isNull();
        assertThat(config.workspacesRoot()).isEqualTo("/home/me/sailor-workspaces");
        assertThat(config.repo()).isEqualTo("https://github.com/Gram21/SaiLoR");
    }
}
