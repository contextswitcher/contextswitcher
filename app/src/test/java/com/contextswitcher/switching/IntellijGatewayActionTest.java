package com.contextswitcher.switching;

import com.contextswitcher.tasks.Task;
import com.contextswitcher.tasks.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// [utest->dsn~gateway-url-action~11]
class IntellijGatewayActionTest {

    @Test
    void plainHostAliasPassedThrough() {
        assertThat(IntellijGatewayAction.gatewayUrl("devbox", "/home/oliver/jabref", null))
                .isEqualTo("jetbrains-gateway://connect#host=devbox&type=ssh&port=22"
                        + "&projectPath=%2Fhome%2Foliver%2Fjabref");
    }

    @Test
    void userAtHostSplitsIntoUserParameter() {
        assertThat(IntellijGatewayAction.gatewayUrl("koppor@devbox", "/home/oliver/jabref", null))
                .isEqualTo("jetbrains-gateway://connect#host=devbox&user=koppor&type=ssh&port=22"
                        + "&projectPath=%2Fhome%2Foliver%2Fjabref");
    }

    @Test
    void ideAddsIdePathAndDisablesDeploy() {
        assertThat(IntellijGatewayAction.gatewayUrl("devbox", "/home/oliver/jabref",
                "/home/oliver/.cache/JetBrains/RemoteDev/dist/ideaIU-2026.1"))
                .endsWith("&idePath=%2Fhome%2Foliver%2F.cache%2FJetBrains%2FRemoteDev%2Fdist%2FideaIU-2026.1"
                        + "&deploy=false");
    }

    @Test
    void spacesEncodeAsPercent20NotPlus() {
        assertThat(IntellijGatewayAction.gatewayUrl("devbox", "/home/oliver/my project", null))
                .contains("projectPath=%2Fhome%2Foliver%2Fmy%20project")
                .doesNotContain("+");
    }

    @Test
    void sectionRemoteOverridesTaskRemote() {
        Task task = new Task("t", "T", TaskStatus.ACTIVE, "taskhost",
                null, null, new Task.IntellijConfig("/p", "otherhost", null), null, null, null, "");
        assertThat(task.intellijRemote()).isEqualTo("otherhost");
    }

    @Test
    void projectPathFallsBackToClaudeWorkspaceThenCwd() {
        Task.IntellijConfig bare = new Task.IntellijConfig(null, null, null);
        Task withWorkspace = new Task("t", "T", TaskStatus.ACTIVE, "h", null, null, bare, null,
                new Task.ClaudeConfig("/repo", "id", "/repo/worktree"), null, "");
        Task cwdOnly = new Task("t", "T", TaskStatus.ACTIVE, "h", null, null, bare, null,
                new Task.ClaudeConfig("/repo", "id", null), null, "");
        Task explicit = new Task("t", "T", TaskStatus.ACTIVE, "h", null, null,
                new Task.IntellijConfig("/elsewhere", null, null), null,
                new Task.ClaudeConfig("/repo", "id", "/repo/worktree"), null, "");
        Task noClaude = new Task("t", "T", TaskStatus.ACTIVE, "h", null, null, bare, null, null, null, "");

        assertThat(withWorkspace.intellijProjectPath()).isEqualTo("/repo/worktree");
        assertThat(cwdOnly.intellijProjectPath()).isEqualTo("/repo");
        assertThat(explicit.intellijProjectPath()).isEqualTo("/elsewhere");
        assertThat(noClaude.intellijProjectPath()).isNull();
    }

    @Test
    void bareIntellijWithClaudeSectionIsConfigured() {
        Task task = new Task("t", "T", TaskStatus.ACTIVE, "h", null, null,
                new Task.IntellijConfig(null, null, null), null,
                new Task.ClaudeConfig("/repo", null, null), null, "");
        assertThat(new IntellijGatewayAction(url -> { }, host -> null, path -> false).isConfigured(task)).isTrue();
    }

    @Test
    void bareIntellijWithoutClaudeSectionIsNotConfigured() {
        Task task = new Task("t", "T", TaskStatus.ACTIVE, "h", null, null,
                new Task.IntellijConfig(null, null, null), null, null, null, "");
        assertThat(new IntellijGatewayAction(url -> { }, host -> null, path -> false).isConfigured(task)).isFalse();
    }

    @Test
    void runUsesTheResolvedProjectPath() {
        StringBuilder launched = new StringBuilder();
        IntellijGatewayAction action = new IntellijGatewayAction(launched::append, host -> "/dist/idea", path -> false);
        Task task = new Task("t", "T", TaskStatus.ACTIVE, "h", null, null,
                new Task.IntellijConfig(null, null, null), null,
                new Task.ClaudeConfig("/repo", null, "/repo/worktree"), null, "");
        ActionResult result = action.run(task);
        assertThat(result.ok()).isTrue();
        assertThat(launched.toString()).contains("projectPath=%2Frepo%2Fworktree");
    }

    // Gateway 2026.1 refuses a link without idePath; a task without a pinned
    // intellij.ide falls back to the newest backend installed on the remote.
    @Test
    void unpinnedIdeFallsBackToRemoteLookup() {
        StringBuilder launched = new StringBuilder();
        IntellijGatewayAction action = new IntellijGatewayAction(launched::append,
                host -> "/home/koppor/.cache/JetBrains/RemoteDev/dist/idea-262.8665.176",
                path -> false);
        Task task = new Task("t", "T", TaskStatus.ACTIVE, "koppor@devbox", null, null,
                new Task.IntellijConfig("/repo", null, null), null, null, null, "");
        ActionResult result = action.run(task);
        assertThat(result.ok()).isTrue();
        assertThat(launched.toString())
                .contains("idePath=%2Fhome%2Fkoppor%2F.cache%2FJetBrains%2FRemoteDev%2Fdist%2Fidea-262.8665.176")
                .contains("&deploy=false");
    }

    // An already-open client window is focused; no URL, no remote lookup —
    // relaunching would trigger Gateway's "already running" version prompt.
    @Test
    void openClientWindowIsFocusedInsteadOfLaunchingTheUrl() {
        StringBuilder launched = new StringBuilder();
        IntellijGatewayAction action = new IntellijGatewayAction(launched::append,
                host -> "/dist/idea", path -> true);
        Task task = new Task("t", "T", TaskStatus.ACTIVE, "h", null, null,
                new Task.IntellijConfig("/repo/hayagriva", null, null), null, null, null, "");
        ActionResult result = action.run(task);
        assertThat(result.ok()).isTrue();
        assertThat(result.detail()).contains("focused");
        assertThat(launched.toString()).isEmpty();
    }

    // The chip stays RUNNING until the project's client window appears: after
    // launching, the action polls clientFocus and only reports up once it hits.
    @Test
    void waitsForTheClientWindowBeforeReportingUp() {
        java.util.concurrent.atomic.AtomicInteger polls = new java.util.concurrent.atomic.AtomicInteger();
        // The pre-launch check is the 1st call (false → launches); the window
        // then appears on the 3rd call, so the post-launch poll must run twice.
        java.util.function.Predicate<String> appearsOnThirdCheck = path -> polls.incrementAndGet() >= 3;
        IntellijGatewayAction action = new IntellijGatewayAction(url -> { }, host -> "/dist/idea",
                appearsOnThirdCheck, 10, 1);
        Task task = new Task("t", "T", TaskStatus.ACTIVE, "h", null, null,
                new Task.IntellijConfig("/repo", null, null), null, null, null, "");

        ActionResult result = action.run(task);

        assertThat(result.ok()).isTrue();
        assertThat(result.detail()).contains("opened");
        assertThat(polls.get()).isEqualTo(3);
    }

    // On timeout the action still reports success — the launch worked and the
    // IDE may yet come up; a red chip would misrepresent that.
    @Test
    void reportsSuccessWhenTheClientWindowNeverAppears() {
        java.util.concurrent.atomic.AtomicInteger polls = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Predicate<String> never = path -> {
            polls.incrementAndGet();
            return false;
        };
        IntellijGatewayAction action = new IntellijGatewayAction(url -> { }, host -> "/dist/idea",
                never, 4, 1);
        Task task = new Task("t", "T", TaskStatus.ACTIVE, "h", null, null,
                new Task.IntellijConfig("/repo", null, null), null, null, null, "");

        ActionResult result = action.run(task);

        assertThat(result.ok()).isTrue();
        assertThat(result.detail()).contains("still starting");
        // one pre-launch check plus the four post-launch polls
        assertThat(polls.get()).isEqualTo(5);
    }

    @Test
    void unpinnedIdeWithoutRemoteBackendFails() {
        IntellijGatewayAction action = new IntellijGatewayAction(url -> { }, host -> null, path -> false);
        Task task = new Task("t", "T", TaskStatus.ACTIVE, "h", null, null,
                new Task.IntellijConfig("/repo", null, null), null, null, null, "");
        ActionResult result = action.run(task);
        assertThat(result.ok()).isFalse();
        assertThat(result.detail()).contains("intellij.ide");
    }

    @Test
    void launcherFailureReportsFailure() {
        IntellijGatewayAction action = new IntellijGatewayAction(url -> {
            throw new IllegalStateException("no browser");
        }, host -> null, path -> false);
        Task task = new Task("t", "T", TaskStatus.ACTIVE, "h",
                null, null, new Task.IntellijConfig("/p", null, "/dist/idea"), null, null, null, "");
        ActionResult result = action.run(task);
        assertThat(result.ok()).isFalse();
        assertThat(result.detail()).contains("no browser");
    }
}
