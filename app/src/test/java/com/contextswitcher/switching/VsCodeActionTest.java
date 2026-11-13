package com.contextswitcher.switching;

import java.util.List;

import com.contextswitcher.tasks.Task;
import com.contextswitcher.tasks.TaskFileParser;
import com.contextswitcher.tasks.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~vscode-action~1]
class VsCodeActionTest {

    private static Task task(String remote) {
        return new Task("t", "T", TaskStatus.ACTIVE, remote, null, null, null, null,
                new Task.ClaudeConfig("/repo", "id", "/repo/wt"), null, "");
    }

    @Test
    void localTaskOpensWorkspaceInNewWindow() {
        assertThat(VsCodeAction.command(task(null))).containsExactly("code", "-n", "/repo/wt");
    }

    @Test
    void remoteTaskOpensViaRemoteSsh() {
        assertThat(VsCodeAction.command(task("koppor@devbox")))
                .containsExactly("code", "-n", "--remote", "ssh-remote+koppor@devbox", "/repo/wt");
    }

    @Test
    void launcherFailureFailsTheChip() {
        ActionResult result = new VsCodeAction(t -> true, argv -> "boom").run(task(null));
        assertThat(result.ok()).isFalse();
        assertThat(result.detail()).isEqualTo("boom");
    }

    @Test
    void flagReadsOnlyTrue() {
        assertThat(TaskFileParser.frontmatterFlag("---\nvscode: true\n---\n", "vscode")).isTrue();
        assertThat(TaskFileParser.frontmatterFlag("---\nvscode: false\n---\n", "vscode")).isFalse();
        assertThat(TaskFileParser.frontmatterFlag("---\ntitle: x\n---\n", "vscode")).isFalse();
        assertThat(new VsCodeAction(t -> false, argv -> null).isConfigured(task(null))).isFalse();
        assertThat(new VsCodeAction(t -> true, argv -> null).run(task(null)).detail())
                .isEqualTo(String.join(" ", List.of("code", "-n", "/repo/wt")));
    }
}
