package com.contextswitcher.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/// The setup prompt of a category created from a repository URL: the
/// repository type's clone clause and the optional first message.
// [utest->dsn~category-from-url~4]
class CategorySetupDescriptionTest {

    private static final String URL = "https://github.com/Gram21/SaiLoR";

    @Test
    void plainCloneWithoutExtras() {
        String description = MainWindow.categorySetupDescription(URL, "sailor",
                "/data/koppor/sailor-workspaces", RepoType.WRITE_ACCESS, null);
        assertThat(description).contains("Clone the repository into the subdirectory sailor")
                .doesNotContain("fork", "After setting things up");
    }

    @Test
    void forkTypeClonesTheForkInstead() {
        String description = MainWindow.categorySetupDescription(URL, "sailor",
                "/data/koppor/sailor-workspaces", RepoType.FORK_PR_UPSTREAM, "  ");
        assertThat(description).contains("Fork the repository", "gh repo fork --clone",
                        "subdirectory sailor")
                .doesNotContain("After setting things up");
    }

    @Test
    void firstMessageIsAppended() {
        String description = MainWindow.categorySetupDescription(URL, "sailor",
                "/data/koppor/sailor-workspaces", RepoType.ORIGIN_ONLY, " Then run the tests. ");
        assertThat(description).endsWith("After setting things up, please do the following: "
                + "Then run the tests.");
    }

    /// The description points at the template the prompt appends, so the two
    /// halves have to keep referring to the same thing.
    // [utest->dsn~claude-md-templates~1]
    @ParameterizedTest
    @EnumSource(RepoType.class)
    void everyTypeShipsATemplateTheDescriptionPointsAt(RepoType type) {
        String description = MainWindow.categorySetupDescription(URL, "sailor",
                "/data/koppor/sailor-workspaces", type, null);
        assertThat(description).contains("template at the end of this prompt", "<placeholder>");
        assertThat(type.template())
                .contains("-workspaces layout and workflow", "<YYYY-MM-DD>-<slug>", "<project>");
    }
}
