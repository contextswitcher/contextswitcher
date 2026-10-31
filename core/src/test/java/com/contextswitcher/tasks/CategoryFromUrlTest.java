package com.contextswitcher.tasks;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// A category created from a repository URL: the folder name derived from
/// the URL and the real-valued `CONTEXTSWITCHER.md` written for it.
// [utest->dsn~category-from-url~5]
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
        assertThat(TaskFileParser.repoName("Mail bob@example.org: the importer is broken")).isNull();
        assertThat(TaskFileParser.repoName("/home/me/SaiLoR")).isNull();
    }

    @Test
    void anSshCloneUrlNamesItsRepository() {
        assertThat(TaskFileParser.repoName("git@github.com:Gram21/SaiLoR.git")).isEqualTo("sailor");
        assertThat(TaskFileParser.repoName("ssh://git@github.com:22/Gram21/SaiLoR")).isEqualTo("sailor");
    }

    /// A link to a file, a pull request or a GitLab page inside the
    /// repository names the repository, not the file.
    @Test
    void aDeepLinkNamesItsRepository() {
        assertThat(TaskFileParser.repoWebUrl(
                "https://github.com/JabRef/jabref-demonstration-libraries/blob/main/.markdownlint.yml"))
                .isEqualTo("https://github.com/JabRef/jabref-demonstration-libraries");
        assertThat(TaskFileParser.repoName("https://github.com/JabRef/jabref/pull/12345"))
                .isEqualTo("jabref");
        assertThat(TaskFileParser.repoWebUrl("https://gitlab.com/group/sub/project/-/blob/main/README.md"))
                .isEqualTo("https://gitlab.com/group/sub/project");
    }

    /// The category's `repo:` is opened in the browser, so an ssh clone URL
    /// is written as its web page; an https URL stays as typed.
    @Test
    void theRepoIsTheWebUrl() {
        assertThat(TaskFileParser.repoWebUrl("git@github.com:JabRef/jabref-demonstration-libraries.git"))
                .isEqualTo("https://github.com/JabRef/jabref-demonstration-libraries");
        assertThat(TaskFileParser.repoWebUrl(" https://github.com/Gram21/SaiLoR.git/ "))
                .isEqualTo("https://github.com/Gram21/SaiLoR");
        assertThat(TaskFileParser.repoWebUrl("https://gitea.example.org/a/b/SaiLoR?tab=readme"))
                .isEqualTo("https://gitea.example.org/a/b/SaiLoR");
        assertThat(TaskFileParser.repoWebUrl("Fix the NPE in the importer")).isNull();
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
    // [utest->dsn~setup-wizard~11]
    @Test
    void withoutARemoteTheConfigIsALocalCategory() {
        String content = TaskFileParser.groupConfigForRepo("sailor", "https://github.com/Gram21/SaiLoR",
                null, "/home/me/sailor-workspaces", null);
        GroupConfig config = new TaskFileParser().parseGroupConfig(content);
        assertThat(config.remote()).isNull();
        assertThat(config.workspacesRoot()).isEqualTo("/home/me/sailor-workspaces");
        assertThat(config.repo()).isEqualTo("https://github.com/Gram21/SaiLoR");
    }

    /// A plain folder (OneDrive, no git): its path is the root, as written
    /// on Windows, and there is no clone to point `mainCheckout` or `repo` at.
    // [utest->dsn~category-from-folder~1]
    @Test
    void aFolderCategoryHasNoCheckoutAndNoRepo() {
        String content = TaskFileParser.groupConfigForRepo("paper", null, null,
                "C:\\Users\\me\\OneDrive\\paper", null);
        GroupConfig config = new TaskFileParser().parseGroupConfig(content);
        assertThat(config.workspacesRoot()).isEqualTo("C:\\Users\\me\\OneDrive\\paper");
        assertThat(config.mainCheckout()).isNull();
        assertThat(config.repo()).isNull();
    }
}
