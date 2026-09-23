package com.contextswitcher.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// The recommended tools' scripts obey the transport rule (no double quotes,
/// `dsn~ssh-command-runner~6`), the local set leaves RefactoringMiner out,
/// and a version is the command's last line (`dsn~recommended-tools~1`).
// [utest->dsn~recommended-tools~1]
class RecommendedToolsTest {

    @Test
    void scriptsCarryNoDoubleQuotes() {
        for (RecommendedTools.Tool tool : RecommendedTools.remote(null)) {
            assertThat(String.join(" ", tool.probe())).as(tool.key() + " probe").doesNotContain("\"");
            assertThat(String.join(" ", tool.install())).as(tool.key() + " install").doesNotContain("\"");
        }
    }

    @Test
    void thisMachineGetsNoRefactoringMiner() {
        assertThat(RecommendedTools.local()).extracting(RecommendedTools.Tool::key)
                .containsExactly("ast-grep", "codegraph");
        assertThat(RecommendedTools.remote(null)).extracting(RecommendedTools.Tool::key)
                .containsExactly("refactoringminer", "ast-grep", "codegraph");
    }

    @Test
    void codegraphInstallWritesTheShippedSkill() {
        RecommendedTools.Tool codegraph = RecommendedTools.codegraph();
        assertThat(codegraph.installInput()).isNotNull();
        assertThat(new String(codegraph.installInput())).startsWith("---\nname: codegraph");
        assertThat(String.join(" ", codegraph.install())).contains("skills/codegraph/SKILL.md");
    }

    @Test
    void theVersionIsTheLastLineOfASuccessfulRun() {
        assertThat(RecommendedTools.version(0, "downloading…\nast-grep 0.45.3\n")).isEqualTo("ast-grep 0.45.3");
        assertThat(RecommendedTools.version(1, "ast-grep 0.45.3")).isNull();
        assertThat(RecommendedTools.version(0, "")).isNull();
    }

    /// A configured RefactoringMiner directory is what the probe looks at.
    // [utest->dsn~setup-wizard~8]
    @Test
    void aConfiguredRefactoringMinerDirectoryIsProbed() {
        assertThat(String.join(" ", RecommendedTools.refactoringMiner("/data/me/RM").probe()))
                .contains("H=/data/me/RM;");
        assertThat(String.join(" ", RecommendedTools.refactoringMiner(null).probe()))
                .contains("H=$HOME/.contextswitcher/RefactoringMiner-");
    }
}
