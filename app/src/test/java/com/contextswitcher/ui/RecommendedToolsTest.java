package com.contextswitcher.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// The recommended tools' scripts obey the transport rule (no double quotes,
/// `dsn~ssh-command-runner~7`), the local set leaves RefactoringMiner out,
/// and a version is the command's last line (`dsn~recommended-tools~3`).
// [utest->dsn~recommended-tools~3]
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
                .containsExactly("claude-hooks", "ast-grep", "codegraph");
        assertThat(RecommendedTools.remote(null)).extracting(RecommendedTools.Tool::key)
                .containsExactly("claude-hooks", "refactoringminer", "ast-grep", "codegraph");
    }

    @Test
    void codegraphInstallWritesTheShippedSkill() {
        RecommendedTools.Tool codegraph = RecommendedTools.codegraph();
        assertThat(codegraph.installInput()).isNotNull();
        assertThat(new String(codegraph.installInput())).startsWith("---\nname: codegraph");
        assertThat(String.join(" ", codegraph.install())).contains("skills/codegraph/SKILL.md");
    }

    /// The installer adds a hook for every option the probe looks for, each
    /// guarded by and targeting the hook's own pane, and goes to `sh -s` on
    /// stdin, the only way its double quotes reach the host.
    // [utest->dsn~claude-report-hooks~1]
    @Test
    void reportingHooksPublishWhatTheProbeLooksFor() {
        RecommendedTools.Tool hooks = RecommendedTools.reportingHooks();
        assertThat(hooks.install()).containsExactly("sh", "-s");
        assertThat(new String(hooks.installInput(), java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo(RecommendedTools.HOOKS_INSTALL);
        for (String marker : RecommendedTools.HOOK_MARKERS) {
            assertThat(String.join(" ", hooks.probe())).contains("grep -q " + marker);
            assertThat(RecommendedTools.HOOKS_INSTALL).containsPattern("@" + marker);
        }
        assertThat(RecommendedTools.HOOKS_INSTALL.lines().filter(line -> line.contains("\"command\""))
                .toList()).hasSize(6).allMatch(line -> line.contains(
                        "[ -n \\\"$TMUX_PANE\\\" ] && tmux set -w -t \\\"$TMUX_PANE\\\" @cs_"));
        assertThat(RecommendedTools.HOOKS_INSTALL).doesNotContain("\r");
    }

    @Test
    void theVersionIsTheLastLineOfASuccessfulRun() {
        assertThat(RecommendedTools.version(0, "downloading…\nast-grep 0.45.3\n")).isEqualTo("ast-grep 0.45.3");
        assertThat(RecommendedTools.version(1, "ast-grep 0.45.3")).isNull();
        assertThat(RecommendedTools.version(0, "")).isNull();
    }

    /// A configured RefactoringMiner directory is what the probe looks at.
    // [utest->dsn~setup-wizard~10]
    @Test
    void aConfiguredRefactoringMinerDirectoryIsProbed() {
        assertThat(String.join(" ", RecommendedTools.refactoringMiner("/data/me/RM").probe()))
                .contains("H=/data/me/RM;");
        assertThat(String.join(" ", RecommendedTools.refactoringMiner(null).probe()))
                .contains("H=$HOME/.contextswitcher/RefactoringMiner-");
    }
}
