package com.contextswitcher.analysis;

import com.contextswitcher.ssh.SshCommandRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.tinylog.Logger;

/// Resolves one task's [RefactoringSummary] over ssh: a cheap HEAD/merge-base
/// probe first, then — only when HEAD moved past the cached summary — the
/// expensive RefactoringMiner `-bc` run whose JSON is parsed for the count.
/// Blocking; runs on the poller thread. The runner needs a generous timeout —
/// analyzing a large commit range takes RM minutes, not the ssh default 10 s.
// [impl->dsn~refactoring-analysis-poller~1]
public class RefactoringLookup {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SshCommandRunner ssh;

    public RefactoringLookup(SshCommandRunner ssh) {
        this.ssh = ssh;
    }

    /// The current summary for `worktree` on `remote`, or null when it cannot
    /// be determined (worktree gone, unreachable remote, unparseable output) —
    /// the caller then keeps whatever it had. Returns `cached` unchanged (no
    /// RM run) while HEAD still matches it.
    public @Nullable RefactoringSummary summary(String remote, String rmHome, String worktree,
            String baseBranch, @Nullable RefactoringSummary cached) {
        SshCommandRunner.SshResult probe =
                ssh.run(remote, RefactoringMinerCommands.countProbeCommand(worktree, baseBranch));
        if (!probe.ok()) {
            return null;
        }
        String[] lines = probe.stdout().strip().split("\n");
        if (lines.length < 2) {
            return null;
        }
        String head = lines[0].strip();
        String mergeBase = lines[1].strip();
        if (cached != null && cached.head().equals(head)) {
            return cached;
        }
        if (head.equals(mergeBase)) {
            // Nothing committed beyond the base — count 0 without a JVM run.
            return new RefactoringSummary(head, 0);
        }
        SshCommandRunner.SshResult counted =
                ssh.run(remote, RefactoringMinerCommands.countCommand(rmHome, worktree, baseBranch));
        if (!counted.ok()) {
            Logger.warn("RefactoringMiner count failed for {} on {}: {}",
                    worktree, remote, counted.stderr().strip());
            return null;
        }
        Integer count = countRefactorings(counted.stdout());
        return count == null ? null : new RefactoringSummary(head, count);
    }

    /// The total refactoring count in RM's `-json` output — the sum over
    /// `commits[].refactorings[]` — or null when the text is not that shape.
    static @Nullable Integer countRefactorings(String json) {
        try {
            JsonNode commits = MAPPER.readTree(json).path("commits");
            if (!commits.isArray()) {
                return null;
            }
            int count = 0;
            for (JsonNode commit : commits) {
                count += commit.path("refactorings").size();
            }
            return count;
        } catch (Exception e) {
            Logger.warn("Cannot parse RefactoringMiner JSON: {}", e.getMessage());
            return null;
        }
    }
}
